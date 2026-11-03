package com.yupi.springbootinit.ai.plan;

import com.yupi.springbootinit.ai.diagnosis.DiagnosisService;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.session.AiSessionService;
import com.yupi.springbootinit.model.entity.LearningReviewItem;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.ai.TargetRef;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习与复习共享的短期规划（计划 §5.6 / P0-5）。
 *
 * <p>每次安排 3–5 个任务，按学生可用时间截断；每个任务都带
 * "为什么现在做"（reason）、"预计多久"（estimatedMinutes）与真实证据引用（evidenceIds）。
 *
 * <p>选择依据优先级：到期复习 → 已验证的近期错误（区分任务）→ 目标覆盖（读具体段落）
 * → 前置不足（补学后返回原任务）→ 证据缺失（先做 1–3 个短任务了解起点，标"待确认"）。
 *
 * <p>只引用实际存在且可访问的已发布单元、题目与人工核验的预测案例：
 * 单元与题目一律经 {@link AiContentScopeService} 用既有发布谓词取出，
 * 规划器不会引用草稿、下线或越权内容。
 *
 * <p>时间估算与排序权重是可配置启发规则，不包装成训练过的最优策略。
 */
@Slf4j
@Service
public class NextActionPlanner {

    /** 启发式时间估算（分钟），可由内容包的 estimatedMinutes 覆盖。 */
    private static final int MINUTES_READ = 4;
    private static final int MINUTES_QUESTION = 3;
    private static final int MINUTES_PREDICT = 3;
    private static final int MINUTES_EXPLAIN = 3;
    private static final int MINUTES_RETEST = 3;

    private static final int MIN_ACTIONS = 3;
    private static final int MAX_ACTIONS = 5;

    @Resource
    private AiContentScopeService contentScope;

    @Resource
    private PredictCaseCatalog predictCases;

    @Data
    public static class PlanInput implements Serializable {
        private User user;
        private String courseCode;
        /** LEARN / REVIEW */
        private String focus = AiSessionService.FOCUS_LEARN;
        private int timeBudgetMinutes;
        private int limit = MAX_ACTIONS;
        /** 用于生成可反解归属的 actionId。 */
        private Long sessionId;
        private EvidenceReadService.EvidenceSummary evidence;
        private List<LearningReviewItem> dueReviews = new ArrayList<>();
        /** 区分任务：预测型与题库型统一表示，由诊断服务决定"要区分哪些原因"。 */
        private List<DiagnosisService.VerificationTask> verifications = new ArrayList<>();
        private Diagnosis diagnosis;
        private List<SourceRef> sources = new ArrayList<>();
        private String unitCode;
        private List<String> nodeCodes = new ArrayList<>();
        /** 学生当前卡住的原任务：补学类任务（READ/EXPLAIN）完成后要返回它。 */
        private TargetRef returnRef;
    }

    public List<NextAction> plan(PlanInput input) {
        List<NextAction> actions = new ArrayList<>();
        if (input == null || input.getUser() == null || StringUtils.isBlank(input.getCourseCode())) {
            return actions;
        }
        boolean reviewFocus = AiSessionService.FOCUS_REVIEW.equals(input.getFocus());
        String weakNode = weakestNode(input);
        List<AiContentScopeService.UnitRef> units = contentScope.publishedUnits(input.getCourseCode());

        // 1. 到期复习：先回忆再解释，因此排在最前
        for (LearningReviewItem item : safe(input.getDueReviews())) {
            if (actions.size() >= input.getLimit()) {
                break;
            }
            NextAction action = reviewAction(input, item);
            if (action != null) {
                actions.add(action);
            }
        }

        // 2. 区分任务：只在有候选原因时安排，最多两道（由 DiagnosisService 限制）。
        //    题库型任务必须解析成"已发布且同课程、学生可见"的题目才生成动作，解析不到就跳过。
        for (DiagnosisService.VerificationTask task : safe(input.getVerifications())) {
            if (actions.size() >= input.getLimit()) {
                break;
            }
            NextAction action = verificationAction(input, task);
            if (action != null) {
                actions.add(action);
            }
        }

        // 3. 读具体段落：优先用本次检索到的真实来源块，保证"可以打开到具体段落"
        SourceRef readSource = firstCourseSource(input.getSources());
        if (actions.size() < input.getLimit() && (readSource != null || !units.isEmpty())) {
            actions.add(readAction(input, readSource, units, weakNode));
        }

        // 4. 回答一题：按薄弱知识点取学生口径的已发布题目
        if (actions.size() < input.getLimit()) {
            NextAction question = questionAction(input, weakNode);
            if (question != null) {
                actions.add(question);
            }
        }

        // 5. 解释区别：把候选原因变成一次可完成的说明任务
        if (actions.size() < input.getLimit() && hasHypotheses(input.getDiagnosis())) {
            actions.add(explainAction(input, units, weakNode));
        }

        // 6. 证据缺失：给可完成的小目标，标"待确认"，不自动判为薄弱。
        //    复习侧重下不新造起点任务，只安排到期复习与补学。
        if (!reviewFocus && actions.size() < MIN_ACTIONS && !units.isEmpty()) {
            for (AiContentScopeService.UnitRef unit : units) {
                if (actions.size() >= Math.max(MIN_ACTIONS, Math.min(input.getLimit(), MAX_ACTIONS))) {
                    break;
                }
                if (containsTarget(actions, TargetRef.KIND_UNIT, unit.getUnitCode())) {
                    continue;
                }
                NextAction starter = new NextAction();
                starter.setType(NextAction.TYPE_READ);
                starter.setTargetRef(new TargetRef(TargetRef.KIND_UNIT, unit.getUnitCode(), null));
                starter.setEstimatedMinutes(unit.getEstimatedMinutes() > 0
                        ? unit.getEstimatedMinutes() : MINUTES_READ);
                starter.setReason("尚无该课程的独立观测证据，起点待确认：先读「"
                        + StringUtils.defaultIfBlank(unit.getTitle(), unit.getUnitCode())
                        + "」建立可完成的小目标，再决定是否安排测评。");
                starter.setStatus(NextAction.STATUS_PENDING);
                actions.add(starter);
            }
        }

        // 补学类任务携带"返回原任务"的引用：卡住 → 补学 → 回到未完成队列
        if (input.getReturnRef() != null) {
            for (NextAction action : actions) {
                if ((NextAction.TYPE_READ.equals(action.getType())
                        || NextAction.TYPE_EXPLAIN.equals(action.getType()))
                        && action.getReturnRef() == null) {
                    action.setReturnRef(input.getReturnRef());
                }
            }
        }
        return truncateByBudget(actions, input.getTimeBudgetMinutes(), input.getLimit());
    }

    /**
     * 按可用时间截断：累计预计分钟数超过预算就停；
     * 预算未设定（0）时按数量上限返回。至少保留一个任务，避免"给了预算却没有可做的事"。
     */
    static List<NextAction> truncateByBudget(List<NextAction> actions, int timeBudgetMinutes, int limit) {
        int cap = limit <= 0 ? MAX_ACTIONS : Math.min(limit, MAX_ACTIONS);
        List<NextAction> out = new ArrayList<>();
        if (timeBudgetMinutes <= 0) {
            for (NextAction action : actions) {
                if (out.size() >= cap) {
                    break;
                }
                out.add(action);
            }
            return out;
        }
        int used = 0;
        for (NextAction action : actions) {
            int cost = action.getEstimatedMinutes() == null ? MINUTES_QUESTION : action.getEstimatedMinutes();
            if (used + cost > timeBudgetMinutes && !out.isEmpty()) {
                break;
            }
            if (out.size() >= cap) {
                break;
            }
            out.add(action);
            used += cost;
        }
        return out;
    }

    /** 分配可反解归属的动作 id：{@code na-<sessionId>-<序号>}。 */
    public static List<NextAction> assignIds(List<NextAction> actions, Long sessionId) {
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setActionId(sessionId == null
                    ? "na-0-" + i : AiSessionService.actionId(sessionId, i));
        }
        return actions;
    }

    // region 各类任务

    private NextAction reviewAction(PlanInput input, LearningReviewItem item) {
        if (item == null) {
            return null;
        }
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_RETEST);
        String code = StringUtils.defaultIfBlank(item.getActivityCode(), item.getNodeCode());
        action.setTargetRef(new TargetRef(TargetRef.KIND_QUESTION, code, null));
        action.setEstimatedMinutes(MINUTES_RETEST);
        action.setReason("到期复习（" + ReviewPlanner.ladderLabel(
                item.getStage() == null ? 0 : item.getStage())
                + "）：先尝试回忆作答，再看解释；复习状态与长期掌握度相互独立。");
        action.setStatus(NextAction.STATUS_PENDING);
        if (item.getSourceEventId() != null) {
            action.getEvidenceIds().add(String.valueOf(item.getSourceEventId()));
        }
        // 只引用确实已发布的题目，否则退化为"回到对应单元"
        if (!isPublishedQuestion(input, code)) {
            if (StringUtils.isBlank(item.getNodeCode())) {
                return null;
            }
            action.setType(NextAction.TYPE_READ);
            action.setTargetRef(unitTargetFor(input, item.getNodeCode()));
            action.setReason("到期复习（" + ReviewPlanner.ladderLabel(
                    item.getStage() == null ? 0 : item.getStage())
                    + "）：原题目已不可用，改为回到对应单元先回忆要点。");
            if (action.getTargetRef() == null) {
                return null;
            }
        }
        return action;
    }

    /**
     * 区分任务分派：预测型沿用既有页面置换案例，题库型解析成学生可见题目。
     *
     * @return 可执行动作；任务无效或题目不可作答时返回 null（不生成占位动作）
     */
    private NextAction verificationAction(PlanInput input, DiagnosisService.VerificationTask task) {
        if (task == null) {
            return null;
        }
        if (task.isQuestion()) {
            return verificationQuestionAction(input, task);
        }
        PredictCaseCatalog.PredictCase predictCase = predictCases.find(task.getPredictCase());
        return predictCase == null ? null : predictAction(input, predictCase);
    }

    /**
     * 题库型区分任务：把课程包里的 {@code questionCode} 解析成学生可作答的题目 id。
     *
     * <p>只接受已发布、未删除、同课程且落在既有学生口径谓词内的题目，
     * 与 {@code select_verified_activity} 走同一条解析路径；解析不到就不生成动作，
     * 宁可少一个任务，也不引用不可作答或越权的题目。
     */
    private NextAction verificationQuestionAction(PlanInput input, DiagnosisService.VerificationTask task) {
        Long questionId = contentScope.eligibleQuestionIdByCode(input.getUser(), input.getCourseCode(),
                task.getQuestionCode());
        if (questionId == null) {
            log.info("[ai-plan] verification question unavailable code={} course={}",
                    task.getQuestionCode(), input.getCourseCode());
            return null;
        }
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_QUESTION);
        action.setTargetRef(new TargetRef(TargetRef.KIND_QUESTION, String.valueOf(questionId), null));
        action.setEstimatedMinutes(task.getEstimatedMinutes() > 0
                ? task.getEstimatedMinutes() : MINUTES_QUESTION);
        action.setStatus(NextAction.STATUS_PENDING);
        action.setEvidenceIds(supportingIds(input.getDiagnosis()));
        action.setReason("区分候选原因：" + StringUtils.defaultIfBlank(task.getReason(),
                "完成一道已发布题目以区分候选原因")
                + "（题目已发布、同课程，判分由服务端完成）");
        return action;
    }

    private NextAction predictAction(PlanInput input, PredictCaseCatalog.PredictCase predictCase) {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_PREDICT);
        action.setTargetRef(new TargetRef(TargetRef.KIND_SIMULATION, predictCase.getCode(), null));
        action.setEstimatedMinutes(predictCase.getEstimatedMinutes() > 0
                ? predictCase.getEstimatedMinutes() : MINUTES_PREDICT);
        action.setReason("区分候选原因：" + StringUtils.defaultIfBlank(predictCase.getTitle(), predictCase.getCode())
                + "。答案由服务端页面置换计算器核对，提交前只显示允许看到的前缀。");
        action.setStatus(NextAction.STATUS_PENDING);
        action.setEvidenceIds(supportingIds(input.getDiagnosis()));
        // 预测卡住时先补学再回来：returnRef 指向本预测任务，由补学任务携带
        return action;
    }

    private NextAction readAction(PlanInput input, SourceRef source,
                                  List<AiContentScopeService.UnitRef> units, String weakNode) {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_READ);
        action.setEstimatedMinutes(MINUTES_READ);
        action.setStatus(NextAction.STATUS_PENDING);
        if (source != null) {
            action.setTargetRef(new TargetRef(TargetRef.KIND_MATERIAL, source.getBlockId(), source.getLocator()));
            action.setReason("读具体段落：" + abbreviate(source.getExcerpt(), 40)
                    + "（来源 " + source.getSourceId() + " 修订 " + source.getRevision()
                    + "）——本次检索命中且与当前问题直接相关。");
            action.setEvidenceIds(supportingIds(input.getDiagnosis()));
            return action;
        }
        AiContentScopeService.UnitRef unit = pickUnit(units, weakNode, input.getUnitCode());
        if (unit == null) {
            return null;
        }
        action.setTargetRef(new TargetRef(TargetRef.KIND_UNIT, unit.getUnitCode(), null));
        action.setEstimatedMinutes(unit.getEstimatedMinutes() > 0 ? unit.getEstimatedMinutes() : MINUTES_READ);
        action.setReason("目标覆盖：读已发布单元「" + StringUtils.defaultIfBlank(unit.getTitle(), unit.getUnitCode())
                + "」" + (StringUtils.isBlank(weakNode) ? "" : "，覆盖知识点 " + weakNode) + "。");
        return action;
    }

    private NextAction questionAction(PlanInput input, String weakNode) {
        List<Long> ids = contentScope.publishedQuestionIds(input.getUser(), input.getCourseCode(), weakNode, 3);
        if (ids.isEmpty()) {
            ids = contentScope.publishedQuestionIds(input.getUser(), input.getCourseCode(), null, 3);
        }
        if (ids.isEmpty()) {
            return null;
        }
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_QUESTION);
        action.setTargetRef(new TargetRef(TargetRef.KIND_QUESTION, String.valueOf(ids.get(0)), null));
        action.setEstimatedMinutes(MINUTES_QUESTION);
        action.setStatus(NextAction.STATUS_PENDING);
        action.setEvidenceIds(supportingIds(input.getDiagnosis()));
        action.setReason(StringUtils.isBlank(weakNode)
                ? "证据缺失：完成一道已发布客观题，作为了解起点的短任务（起点待确认，不判为薄弱）。"
                : "已验证的近期错误：在知识点 " + weakNode + " 上做一道已发布客观题，判分仍由服务端完成。");
        return action;
    }

    private NextAction explainAction(PlanInput input, List<AiContentScopeService.UnitRef> units, String weakNode) {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_EXPLAIN);
        action.setEstimatedMinutes(MINUTES_EXPLAIN);
        action.setStatus(NextAction.STATUS_PENDING);
        TargetRef target = unitTargetFor(input, weakNode);
        if (target == null) {
            AiContentScopeService.UnitRef unit = pickUnit(units, weakNode, input.getUnitCode());
            if (unit == null) {
                return null;
            }
            target = new TargetRef(TargetRef.KIND_UNIT, unit.getUnitCode(), null);
        }
        action.setTargetRef(target);
        List<String> labels = new ArrayList<>();
        if (input.getDiagnosis() != null) {
            for (Diagnosis.Hypothesis hypothesis : input.getDiagnosis().getHypotheses()) {
                labels.add(hypothesis.getLabel());
            }
        }
        action.setReason("解释区别：用自己的话说明「" + String.join("」与「", labels)
                + "」的不同，说明后再做未揭示答案的新任务复测。");
        action.setEvidenceIds(supportingIds(input.getDiagnosis()));
        return action;
    }

    // endregion

    // region 辅助

    private TargetRef unitTargetFor(PlanInput input, String nodeCode) {
        if (StringUtils.isBlank(nodeCode)) {
            return null;
        }
        for (AiContentScopeService.ActivityRef activity : contentScope.publishedActivities(input.getCourseCode())) {
            if (nodeCode.equals(activity.getReferenceCode())) {
                return new TargetRef(TargetRef.KIND_UNIT, activity.getUnitCode(), activity.getActivityCode());
            }
        }
        return null;
    }

    private boolean isPublishedQuestion(PlanInput input, String code) {
        if (StringUtils.isBlank(code) || !StringUtils.isNumeric(code)) {
            return false;
        }
        Long id = Long.valueOf(code);
        for (Long candidate : contentScope.publishedQuestionIds(input.getUser(), input.getCourseCode(), null, 200)) {
            if (id.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static AiContentScopeService.UnitRef pickUnit(List<AiContentScopeService.UnitRef> units,
                                                          String weakNode, String currentUnit) {
        if (units == null || units.isEmpty()) {
            return null;
        }
        if (StringUtils.isNotBlank(weakNode)) {
            for (AiContentScopeService.UnitRef unit : units) {
                if (weakNode.equals(unit.getUnitCode())
                        || StringUtils.contains(unit.getTitle(), weakNode)) {
                    return unit;
                }
            }
        }
        if (StringUtils.isNotBlank(currentUnit)) {
            for (AiContentScopeService.UnitRef unit : units) {
                if (currentUnit.equals(unit.getUnitCode())) {
                    return unit;
                }
            }
        }
        return units.get(0);
    }

    private static SourceRef firstCourseSource(List<SourceRef> sources) {
        if (sources == null) {
            return null;
        }
        for (SourceRef source : sources) {
            if (source != null && "COURSE".equals(source.getOwnerScope())) {
                return source;
            }
        }
        return sources.isEmpty() ? null : sources.get(0);
    }

    private static String weakestNode(PlanInput input) {
        if (input.getEvidence() != null && !input.getEvidence().getWeakNodeCodes().isEmpty()) {
            return input.getEvidence().getWeakNodeCodes().get(0);
        }
        if (input.getNodeCodes() != null && !input.getNodeCodes().isEmpty()) {
            return input.getNodeCodes().get(0);
        }
        return null;
    }

    private static boolean hasHypotheses(Diagnosis diagnosis) {
        return diagnosis != null && !diagnosis.getHypotheses().isEmpty();
    }

    private static List<String> supportingIds(Diagnosis diagnosis) {
        List<String> ids = new ArrayList<>();
        if (diagnosis != null && diagnosis.getSupportingEventIds() != null) {
            ids.addAll(diagnosis.getSupportingEventIds());
        }
        return ids;
    }

    private static boolean containsTarget(List<NextAction> actions, String kind, String code) {
        for (NextAction action : actions) {
            TargetRef target = action.getTargetRef();
            if (target != null && kind.equals(target.getKind()) && code.equals(target.getCode())) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static String abbreviate(String text, int max) {
        String value = StringUtils.defaultString(text).replaceAll("\\s+", " ").trim();
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    // endregion
}
