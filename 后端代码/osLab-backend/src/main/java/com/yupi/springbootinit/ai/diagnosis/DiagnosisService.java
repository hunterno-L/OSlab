package com.yupi.springbootinit.ai.diagnosis;

import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从"解释错误"走向"区分错误原因"（计划 §5.4 / P0-4）。
 *
 * <p>候选原因只来自三处可核查的来源：版本化误区映射、题目自带的误区标注、学生所选选项；
 * 模型可以补充提议，但只有落在目录内的代码才会被采纳，
 * 因此不会出现无法复核的"AI 编造原因"。
 *
 * <p>状态判定是确定性规则，不由模型决定：
 * <ul>
 *   <li>{@code INSUFFICIENT}——没有候选原因，或没有"独立观测到的失败"
 *       （带提示作答、看答案后作答、系统/编译错误都不能作为薄弱证据）；</li>
 *   <li>{@code TENTATIVE}——有独立失败观测，但还缺少区分任务的结论；</li>
 *   <li>{@code SUPPORTED}——区分任务已完成，且结果把候选原因收敛到唯一一个。</li>
 * </ul>
 * 一次错误不能直接断言唯一原因；证据不足时明确保留不确定性。
 */
@Slf4j
@Service
public class DiagnosisService {

    private static final int MAX_HYPOTHESES = 3;
    /** 计划 §5.4：必要时用最多两道短题或一次预测来区分。 */
    private static final int MAX_VERIFICATIONS = 2;

    @Resource
    private MisconceptionCatalog catalog;

    @Resource
    private PredictCaseCatalog predictCases;

    @Data
    public static class VerificationOutcome implements Serializable {
        private String predictCase;
        private boolean correct;
        private String eventId;
        /** 该案例区分的误区代码。 */
        private List<String> discriminates = new ArrayList<>();
    }

    @Data
    public static class Input implements Serializable {
        private Long userId;
        private String courseCode;
        /** 知识点编码 / 历史节点名 / 技能编码。 */
        private List<String> skillKeys = new ArrayList<>();
        /** 题目自带的误区标注（人工撰写，可核查）。 */
        private String questionMisconception;
        /** 学生所选选项的文本，不含正确答案。 */
        private String chosenOptionLabel;
        /** 本次作答是否正确（服务端判分结果，不接受前端结论）。 */
        private Boolean correct;
        private EvidenceReadService.EvidenceSummary evidence;
        /** 模型提议的原因代码；只有落在目录内的才被采纳。 */
        private List<String> proposedCodes = new ArrayList<>();
        /** 已完成的区分任务结论。 */
        private List<VerificationOutcome> completedVerifications = new ArrayList<>();
    }

    /**
     * 一个待安排的区分任务。两种形态互斥：
     * <ul>
     *   <li>预测型（{@code predictCase}）——操作系统页面置换案例，答案由 {@code PageReplacer} 现场计算；</li>
     *   <li>题库型（{@code questionCode}）——数据库等课程包里的已发布题目，
     *       由 {@link com.yupi.springbootinit.ai.plan.AiContentScopeService} 解析成学生可见的题目 id，
     *       与 {@code select_verified_activity} 使用同一套已发布/同课程谓词；解析不到就不会生成动作。</li>
     * </ul>
     * 诊断只决定"要区分哪些原因"，题目是否可作答由编排阶段核对，因此目录里写错 code 不会伪造出题目。
     */
    @Data
    public static class VerificationTask implements Serializable {
        private String predictCase;
        private String questionCode;
        /** 该任务能区分的误区代码。 */
        private List<String> discriminates = new ArrayList<>();
        private String reason;
        private int estimatedMinutes = 3;

        public boolean isQuestion() {
            return StringUtils.isNotBlank(questionCode);
        }
    }

    @Data
    public static class Result implements Serializable {
        private Diagnosis diagnosis;
        /** 需要安排的区分任务（由编排器转成 NextAction 并回填 verificationActionIds）。 */
        private List<VerificationTask> verifications = new ArrayList<>();
        /** 参与判定的技能条目，供轨迹说明依据。 */
        private List<String> matchedSkills = new ArrayList<>();
    }

    public Result diagnose(Input input) {
        Result result = new Result();
        Diagnosis diagnosis = new Diagnosis();
        result.setDiagnosis(diagnosis);
        if (input == null || StringUtils.isBlank(input.getCourseCode())) {
            diagnosis.setStatus(Diagnosis.STATUS_INSUFFICIENT);
            diagnosis.setExplanation("缺少课程范围，无法定位误区映射，不作原因判断。");
            return result;
        }
        List<MisconceptionCatalog.Skill> skills = catalog.match(input.getCourseCode(), input.getSkillKeys());
        for (MisconceptionCatalog.Skill skill : skills) {
            result.getMatchedSkills().add(skill.getSkillCode());
        }

        // 1. 候选原因：目录 ∩（选项/题目误区标注命中 或 模型提议）
        Map<String, MisconceptionCatalog.Misconception> candidates = new LinkedHashMap<>();
        Set<String> nodeKeys = new LinkedHashSet<>(input.getSkillKeys());
        for (MisconceptionCatalog.Skill skill : skills) {
            nodeKeys.addAll(skill.getNodeCodes());
            for (MisconceptionCatalog.Misconception misconception : skill.getMisconceptions()) {
                if (matchesHint(misconception, input.getChosenOptionLabel())
                        || matchesHint(misconception, input.getQuestionMisconception())
                        || containsCode(input.getProposedCodes(), misconception.getCode())) {
                    candidates.put(misconception.getCode(), misconception);
                }
            }
        }
        // 没有直接命中时，退回该知识点的全部已登记误区作为待区分候选
        if (candidates.isEmpty()) {
            for (MisconceptionCatalog.Skill skill : skills) {
                for (MisconceptionCatalog.Misconception misconception : skill.getMisconceptions()) {
                    candidates.put(misconception.getCode(), misconception);
                }
            }
        }
        List<String> hypothesisCodes = new ArrayList<>(candidates.keySet());
        if (hypothesisCodes.size() > MAX_HYPOTHESES) {
            hypothesisCodes = new ArrayList<>(hypothesisCodes.subList(0, MAX_HYPOTHESES));
        }
        for (String code : hypothesisCodes) {
            diagnosis.getHypotheses().add(new Diagnosis.Hypothesis(code, candidates.get(code).getLabel()));
        }

        // 2. 证据：独立观测到的失败 / 通过
        List<String> supporting = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();
        int independentFailures = 0;
        int helpedOrFailuresWithoutObservation = 0;
        int diagnostics = 0;
        if (input.getEvidence() != null) {
            for (EvidenceReadService.EvidenceRecord record : input.getEvidence().getRecords()) {
                if (record.isDiagnostic()) {
                    diagnostics++;
                    continue;
                }
                if (!inScope(record, nodeKeys)) {
                    continue;
                }
                boolean independent = record.isFirstObservation()
                        && EvidenceQualification.HELP_NONE.equals(record.getHelpLevel());
                if ("FAIL".equals(record.getResult())) {
                    if (independent) {
                        independentFailures++;
                        supporting.add(record.getEventId());
                    } else {
                        helpedOrFailuresWithoutObservation++;
                    }
                } else if ("PASS".equals(record.getResult()) && independent) {
                    conflicting.add(record.getEventId());
                }
            }
        }
        diagnosis.setSupportingEventIds(trim(supporting));
        diagnosis.setConflictingEventIds(trim(conflicting));

        // 3. 已完成的区分任务：答对说明该原因已被排除
        Set<String> ruledOut = new LinkedHashSet<>();
        boolean verificationDone = false;
        if (input.getCompletedVerifications() != null) {
            for (VerificationOutcome outcome : input.getCompletedVerifications()) {
                if (outcome == null) {
                    continue;
                }
                verificationDone = true;
                if (outcome.isCorrect()) {
                    ruledOut.addAll(outcome.getDiscriminates());
                    if (StringUtils.isNotBlank(outcome.getEventId())) {
                        conflicting.add(outcome.getEventId());
                    }
                } else if (StringUtils.isNotBlank(outcome.getEventId())) {
                    supporting.add(outcome.getEventId());
                }
            }
        }
        List<String> remaining = new ArrayList<>();
        for (String code : hypothesisCodes) {
            if (!ruledOut.contains(code)) {
                remaining.add(code);
            }
        }
        diagnosis.setSupportingEventIds(trim(supporting));
        diagnosis.setConflictingEventIds(trim(conflicting));

        // 4. 状态判定（确定性规则）
        if (hypothesisCodes.isEmpty()) {
            diagnosis.setStatus(Diagnosis.STATUS_INSUFFICIENT);
            diagnosis.setExplanation(catalog.isEmpty()
                    ? "该课程还没有版本化误区映射，题目也未标注常见误区；证据不足，不作原因判断。"
                    : "当前知识点没有匹配的已登记误区，证据不足，不作原因判断。");
            return result;
        }
        if (independentFailures == 0) {
            diagnosis.setStatus(Diagnosis.STATUS_INSUFFICIENT);
            diagnosis.setExplanation(insufficientReason(helpedOrFailuresWithoutObservation, diagnostics,
                    Boolean.TRUE.equals(input.getCorrect())));
            // 仍然给出可用于收集证据的区分任务，但不强判原因
            result.setVerifications(verificationTasks(skills, hypothesisCodes));
            return result;
        }
        if (verificationDone && remaining.size() == 1) {
            diagnosis.setStatus(Diagnosis.STATUS_SUPPORTED);
            diagnosis.setExplanation("区分任务已完成且结果只与「"
                    + candidates.get(remaining.get(0)).getLabel() + "」一致，本次原因判断有证据支撑。"
                    + "是否达到掌握标准仍由证据规则决定，不由本结论替代。");
            diagnosis.setHypotheses(single(diagnosis.getHypotheses(), remaining.get(0)));
            return result;
        }
        diagnosis.setStatus(Diagnosis.STATUS_TENTATIVE);
        diagnosis.setExplanation("已观测到 " + independentFailures
                + " 次无帮助条件下的失败，候选原因 " + hypothesisCodes.size()
                + " 个尚不能区分；先完成下面的区分任务，再更新结论。");
        result.setVerifications(verificationTasks(skills,
                remaining.isEmpty() ? hypothesisCodes : remaining));
        return result;
    }

    /**
     * 组装区分任务：预测型（OS 既有行为）在前，题库型（课程包）在后，总数不超过上限。
     *
     * <p>操作系统目录只登记 {@code predictCase}，候选原因与顺序与升级前完全一致；
     * 数据库课程目录只登记 {@code questionCode}，因此不会被塞进页面置换案例。
     */
    List<VerificationTask> verificationTasks(List<MisconceptionCatalog.Skill> skills,
                                             List<String> hypothesisCodes) {
        List<VerificationTask> tasks = new ArrayList<>();
        List<PredictCaseCatalog.PredictCase> predictCasesForCodes =
                predictCases.discriminating(hypothesisCodes);
        for (PredictCaseCatalog.PredictCase predictCase : limit(predictCasesForCodes, MAX_VERIFICATIONS)) {
            VerificationTask task = new VerificationTask();
            task.setPredictCase(predictCase.getCode());
            task.setDiscriminates(new ArrayList<>(predictCase.getDiscriminates()));
            task.setReason(StringUtils.defaultIfBlank(predictCase.getTitle(), predictCase.getCode()));
            task.setEstimatedMinutes(predictCase.getEstimatedMinutes() > 0
                    ? predictCase.getEstimatedMinutes() : 3);
            tasks.add(task);
        }
        if (tasks.size() >= MAX_VERIFICATIONS) {
            return tasks;
        }
        for (VerificationTask task : questionVerificationTasks(skills, hypothesisCodes)) {
            if (tasks.size() >= MAX_VERIFICATIONS) {
                break;
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 题库型验证条目：只把落在本次候选原因内的条目转成任务。
     *
     * <p>条目本身不带 {@code discriminates} 时，按"该技能下进入候选的误区"处理；
     * 带了就取交集，避免把无关原因算进区分范围。
     */
    private List<VerificationTask> questionVerificationTasks(List<MisconceptionCatalog.Skill> skills,
                                                             List<String> hypothesisCodes) {
        List<VerificationTask> tasks = new ArrayList<>();
        if (skills == null || hypothesisCodes == null || hypothesisCodes.isEmpty()) {
            return tasks;
        }
        for (MisconceptionCatalog.Skill skill : skills) {
            List<String> owned = new ArrayList<>();
            for (MisconceptionCatalog.Misconception misconception : skill.getMisconceptions()) {
                if (hypothesisCodes.contains(misconception.getCode())) {
                    owned.add(misconception.getCode());
                }
            }
            if (owned.isEmpty()) {
                continue;
            }
            for (MisconceptionCatalog.Verification verification : skill.getVerification()) {
                if (StringUtils.isBlank(verification.getQuestionCode())) {
                    continue;
                }
                VerificationTask task = new VerificationTask();
                task.setQuestionCode(verification.getQuestionCode().trim());
                List<String> narrow = new ArrayList<>();
                if (verification.getDiscriminates() != null) {
                    for (String code : verification.getDiscriminates()) {
                        if (owned.contains(code)) {
                            narrow.add(code);
                        }
                    }
                }
                task.setDiscriminates(narrow.isEmpty() ? new ArrayList<>(owned) : narrow);
                task.setReason(StringUtils.defaultIfBlank(verification.getReason(),
                        "用一道已发布题目区分「" + StringUtils.defaultIfBlank(skill.getLabel(),
                                skill.getSkillCode()) + "」的候选原因。"));
                task.setEstimatedMinutes(verification.getEstimatedMinutes() > 0
                        ? verification.getEstimatedMinutes() : 3);
                tasks.add(task);
            }
        }
        return tasks;
    }

    /**
     * 模型提议的原因代码必须落在目录内，且必须属于本次匹配到的技能；
     * 否则一律丢弃，避免把课程外的原因写进诊断。
     */
    public List<String> filterProposed(String courseCode, List<String> skillKeys, List<String> proposed) {
        List<String> allowed = new ArrayList<>();
        if (proposed == null || proposed.isEmpty()) {
            return allowed;
        }
        for (MisconceptionCatalog.Skill skill : catalog.match(courseCode, skillKeys)) {
            for (MisconceptionCatalog.Misconception misconception : skill.getMisconceptions()) {
                allowed.add(misconception.getCode());
            }
        }
        List<String> out = new ArrayList<>();
        for (String code : proposed) {
            if (StringUtils.isNotBlank(code) && allowed.contains(code.trim()) && !out.contains(code.trim())) {
                out.add(code.trim());
            }
        }
        return out;
    }

    private static String insufficientReason(int helpedFailures, int diagnostics, boolean currentCorrect) {
        StringBuilder sb = new StringBuilder("证据不足，不作原因判断：");
        if (diagnostics > 0 && helpedFailures == 0) {
            sb.append("近期只有系统或编译类诊断事件，这类问题不计为学生知识失误。");
        } else if (helpedFailures > 0) {
            sb.append(helpedFailures).append(" 次失败发生在获得提示或查看答案之后，不能作为独立能力证据。");
        } else if (currentCorrect) {
            sb.append("本次作答正确且没有历史独立失败观测，暂无需要解释的薄弱点。");
        } else {
            sb.append("该范围内没有无帮助条件下的失败观测，建议先完成 1-3 个短任务了解起点。");
        }
        return sb.toString();
    }

    private static boolean inScope(EvidenceReadService.EvidenceRecord record, Set<String> nodeKeys) {
        if (nodeKeys.isEmpty()) {
            return true;
        }
        return (record.getNodeCode() != null && nodeKeys.contains(record.getNodeCode()))
                || (record.getNodeName() != null && nodeKeys.contains(record.getNodeName()));
    }

    private static boolean matchesHint(MisconceptionCatalog.Misconception misconception, String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (String hint : misconception.getOptionHints()) {
            if (StringUtils.isNotBlank(hint) && text.contains(hint)) {
                return true;
            }
        }
        for (String hint : misconception.getTextHints()) {
            if (StringUtils.isNotBlank(hint) && text.contains(hint)) {
                return true;
            }
        }
        return text.contains(StringUtils.defaultString(misconception.getLabel()));
    }

    private static boolean containsCode(List<String> codes, String code) {
        if (codes == null) {
            return false;
        }
        for (String value : codes) {
            if (code.equals(StringUtils.trimToEmpty(value))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> trim(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.isNotBlank(id)) {
                unique.add(id);
            }
        }
        List<String> out = new ArrayList<>(unique);
        return out.size() > 8 ? new ArrayList<>(out.subList(0, 8)) : out;
    }

    private static List<Diagnosis.Hypothesis> single(List<Diagnosis.Hypothesis> all, String code) {
        List<Diagnosis.Hypothesis> out = new ArrayList<>();
        for (Diagnosis.Hypothesis hypothesis : all) {
            if (code.equals(hypothesis.getCode())) {
                out.add(hypothesis);
            }
        }
        return out;
    }

    private static List<PredictCaseCatalog.PredictCase> limit(List<PredictCaseCatalog.PredictCase> cases, int max) {
        return cases.size() > max ? new ArrayList<>(cases.subList(0, max)) : cases;
    }
}
