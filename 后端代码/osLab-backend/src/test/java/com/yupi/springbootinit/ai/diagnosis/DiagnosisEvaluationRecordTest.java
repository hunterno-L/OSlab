package com.yupi.springbootinit.ai.diagnosis;

import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 诊断验收案例记录（计划 §9）：固定 8 条案例，执行真实 {@link DiagnosisService}，
 * 把 hypotheses / status / supporting / conflicting / verificationActions 写到
 * {@code artifacts/ai-platform-demo/eval-diagnosis-<RunKey>.json}。
 *
 * <p>本类默认跳过，避免普通回归测试写产物；显式执行：
 * <pre>
 * ./mvnw.cmd -o test -Dtest=DiagnosisEvaluationRecordTest \
 *     -Dai.eval.diagnosis=true -Dai.eval.runKey=20261103-...
 * </pre>
 *
 * <p>案例覆盖：知识混淆×2、计算失误、证据不足、已看答案、系统故障、跨课程数据库×2。
 * 断言性质检查：证据不足不强判、已看答案不产生独立观测、系统故障不降掌握。
 */
class DiagnosisEvaluationRecordTest {

    private static final String OS_COURSE = "course.operating-systems";
    private static final String DB_COURSE = "course.database-systems";
    private static final String OS_NODE = "os.memory.lru";

    private final MisconceptionCatalog catalog = new MisconceptionCatalog();
    private final PredictCaseCatalog predictCases = new PredictCaseCatalog();
    private final DiagnosisService service = new DiagnosisService();

    private int failedAssertions;
    private final List<Map<String, Object>> cases = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getProperty("ai.eval.diagnosis", "false")),
                "诊断验收记录默认跳过；使用 -Dai.eval.diagnosis=true 显式执行");
        catalog.load();
        predictCases.init();
        ReflectionTestUtils.setField(service, "catalog", catalog);
        ReflectionTestUtils.setField(service, "predictCases", predictCases);
        failedAssertions = 0;
        cases.clear();
    }

    @Test
    void recordEightDiagnosisCases() throws Exception {
        knowledgeConfusionOne();
        knowledgeConfusionTwoWithVerification();
        calculationError();
        insufficientEvidence();
        answerRevealed();
        systemFailure();
        databaseClosure();
        databaseCandidateKey();

        String runKey = System.getProperty("ai.eval.runKey",
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", cases.size());
        summary.put("insufficient", countStatus(Diagnosis.STATUS_INSUFFICIENT));
        summary.put("tentative", countStatus(Diagnosis.STATUS_TENTATIVE));
        summary.put("supported", countStatus(Diagnosis.STATUS_SUPPORTED));
        summary.put("failedAssertions", failedAssertions);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runKey", runKey);
        report.put("generatedAt", AiJson.nowIso());
        report.put("catalogVersion", catalog.version());
        report.put("caseCount", cases.size());
        report.put("summary", summary);
        report.put("cases", cases);

        Path reportDir = repoRoot().resolve("artifacts").resolve("ai-platform-demo");
        Files.createDirectories(reportDir);
        Path reportPath = reportDir.resolve("eval-diagnosis-" + runKey + ".json");
        String json = AiJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.write(reportPath, json.getBytes(StandardCharsets.UTF_8));
        System.out.println("[eval-diagnosis] report=" + reportPath.toAbsolutePath());

        assertEquals(0, failedAssertions, "诊断验收断言失败：" + failedAssertions);
    }

    // region 8 条固定案例

    private void knowledgeConfusionOne() {
        DiagnosisService.Input input = osInput();
        input.setEvidence(summary(osRecord("k1-e1", "FAIL", true,
                EvidenceQualification.HELP_NONE, false)));
        DiagnosisService.Result result = service.diagnose(input);
        Diagnosis diagnosis = result.getDiagnosis();

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "独立失败后为 TENTATIVE，不直接下唯一结论",
                Diagnosis.STATUS_TENTATIVE.equals(diagnosis.getStatus()));
        check(checks, "候选原因来自版本化目录", !diagnosis.getHypotheses().isEmpty());
        check(checks, "支持证据包含独立失败事件",
                diagnosis.getSupportingEventIds().contains("k1-e1"));
        check(checks, "给出区分任务", !result.getVerifications().isEmpty());
        record("knowledge-confusion-1", "知识混淆", input, result, checks);
    }

    private void knowledgeConfusionTwoWithVerification() {
        DiagnosisService.Input base = osInput();
        base.setEvidence(summary(osRecord("k2-e1", "FAIL", true,
                EvidenceQualification.HELP_NONE, false)));
        DiagnosisService.Result first = service.diagnose(base);
        List<Diagnosis.Hypothesis> hypotheses = first.getDiagnosis().getHypotheses();
        assertTrue(hypotheses.size() >= 2, "该案例需要至少两个候选原因才能验证收敛");

        List<String> ruledOut = new ArrayList<>();
        for (int i = 1; i < hypotheses.size(); i++) {
            ruledOut.add(hypotheses.get(i).getCode());
        }
        DiagnosisService.VerificationOutcome outcome = new DiagnosisService.VerificationOutcome();
        outcome.setCorrect(true);
        outcome.setEventId("k2-verify");
        outcome.setDiscriminates(ruledOut);
        base.setCompletedVerifications(new ArrayList<>(List.of(outcome)));

        DiagnosisService.Result result = service.diagnose(base);
        Diagnosis diagnosis = result.getDiagnosis();

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "区分任务正确后可收敛为 SUPPORTED",
                Diagnosis.STATUS_SUPPORTED.equals(diagnosis.getStatus()));
        check(checks, "收敛后只保留一个候选原因", diagnosis.getHypotheses().size() == 1);
        check(checks, "区分任务结果进入 conflicting 证据",
                diagnosis.getConflictingEventIds().contains("k2-verify"));
        record("knowledge-confusion-2", "知识混淆", base, result, checks);
    }

    private void calculationError() {
        DiagnosisService.Input input = osInput();
        input.setEvidence(summary(osRecord("calc-e1", "FAIL", true,
                EvidenceQualification.HELP_NONE, false)));
        input.setChosenOptionLabel("计算时把访问序列记错了");
        DiagnosisService.Result result = service.diagnose(input);

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "计算失误只列为待区分，不直接断言知识原因",
                Diagnosis.STATUS_TENTATIVE.equals(result.getDiagnosis().getStatus()));
        check(checks, "仍给出候选原因与区分任务",
                !result.getDiagnosis().getHypotheses().isEmpty() && !result.getVerifications().isEmpty());
        record("calculation-error", "计算失误", input, result, checks);
    }

    private void insufficientEvidence() {
        DiagnosisService.Result result = service.diagnose(osInput());

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "无证据时状态为 INSUFFICIENT",
                Diagnosis.STATUS_INSUFFICIENT.equals(result.getDiagnosis().getStatus()));
        check(checks, "证据不足不强判：supporting 为空",
                result.getDiagnosis().getSupportingEventIds().isEmpty());
        check(checks, "仍列出候选原因供后续区分",
                !result.getDiagnosis().getHypotheses().isEmpty());
        check(checks, "给出收集证据的区分任务", !result.getVerifications().isEmpty());
        record("insufficient-evidence", "证据不足", osInput(), result, checks);
    }

    private void answerRevealed() {
        DiagnosisService.Input input = osInput();
        input.setEvidence(summary(osRecord("ans-e1", "FAIL", false,
                EvidenceQualification.HELP_ANSWER_REVEALED, false)));
        DiagnosisService.Result result = service.diagnose(input);

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "已看答案后的失败不产生独立观测",
                result.getDiagnosis().getSupportingEventIds().isEmpty());
        check(checks, "已看答案时仍保持 INSUFFICIENT",
                Diagnosis.STATUS_INSUFFICIENT.equals(result.getDiagnosis().getStatus()));
        record("answer-revealed", "已看答案", input, result, checks);
    }

    private void systemFailure() {
        DiagnosisService.Input input = osInput();
        input.setEvidence(summary(osRecord("sys-e1", "SYSTEM_ERROR", true,
                EvidenceQualification.HELP_NONE, true)));
        DiagnosisService.Result result = service.diagnose(input);

        boolean hasMasteryDependency = Arrays.stream(DiagnosisService.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().toLowerCase().contains("mastery"));

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "系统故障不能作为能力证据",
                result.getDiagnosis().getSupportingEventIds().isEmpty());
        check(checks, "系统故障时保持 INSUFFICIENT",
                Diagnosis.STATUS_INSUFFICIENT.equals(result.getDiagnosis().getStatus()));
        check(checks, "诊断服务不持有掌握度写入依赖（系统故障不降掌握）", !hasMasteryDependency);
        record("system-failure", "系统故障", input, result, checks);
    }

    private void databaseClosure() {
        DiagnosisService.Input input = dbInput("db.fd.attribute-closure");
        DiagnosisService.Result result = service.diagnose(input);

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "数据库课程无证据时为 INSUFFICIENT",
                Diagnosis.STATUS_INSUFFICIENT.equals(result.getDiagnosis().getStatus()));
        check(checks, "属性闭包候选原因来自目录",
                !result.getDiagnosis().getHypotheses().isEmpty());
        check(checks, "数据库区分任务均为题库型", allQuestionVerifications(result));
        check(checks, "题库型任务不得引用页面置换预测案例", noPredictVerifications(result));
        record("db-closure", "跨课程数据库", input, result, checks);
    }

    private void databaseCandidateKey() {
        DiagnosisService.Input input = dbInput("db.fd.candidate-key");
        input.setEvidence(summary(dbRecord("db-e1", "FAIL", true,
                EvidenceQualification.HELP_NONE, false, "db.fd.candidate-key")));
        DiagnosisService.Result result = service.diagnose(input);

        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "数据库独立失败后为 TENTATIVE",
                Diagnosis.STATUS_TENTATIVE.equals(result.getDiagnosis().getStatus()));
        check(checks, "候选键候选原因来自目录",
                !result.getDiagnosis().getHypotheses().isEmpty());
        check(checks, "数据库区分任务均为题库型（不混入 OS 预测）",
                allQuestionVerifications(result) && noPredictVerifications(result));
        check(checks, "支持证据包含数据库独立失败",
                result.getDiagnosis().getSupportingEventIds().contains("db-e1"));
        record("db-candidate-key", "跨课程数据库", input, result, checks);
    }

    // endregion

    // region 记录与断言辅助

    private void record(String id, String kind, DiagnosisService.Input input,
                        DiagnosisService.Result result, List<Map<String, Object>> checks) {
        Diagnosis diagnosis = result.getDiagnosis();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("kind", kind);
        row.put("input", inputSummary(input));
        row.put("status", diagnosis.getStatus());
        row.put("hypotheses", hypothesisMaps(diagnosis.getHypotheses()));
        row.put("supportingEventIds", new ArrayList<>(diagnosis.getSupportingEventIds()));
        row.put("conflictingEventIds", new ArrayList<>(diagnosis.getConflictingEventIds()));
        row.put("verificationActions", verificationMaps(result.getVerifications()));
        row.put("explanation", diagnosis.getExplanation());
        row.put("matchedSkills", new ArrayList<>(result.getMatchedSkills()));
        row.put("assertions", checks);
        boolean passed = true;
        for (Map<String, Object> check : checks) {
            if (!Boolean.TRUE.equals(check.get("passed"))) {
                passed = false;
            }
        }
        row.put("assertionsPassed", passed);
        cases.add(row);
    }

    private void check(List<Map<String, Object>> checks, String note, boolean condition) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("note", note);
        check.put("passed", condition);
        checks.add(check);
        if (!condition) {
            failedAssertions++;
        }
    }

    private Map<String, Object> inputSummary(DiagnosisService.Input input) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("courseCode", input.getCourseCode());
        map.put("skillKeys", new ArrayList<>(input.getSkillKeys()));
        map.put("chosenOptionLabel", input.getChosenOptionLabel());
        map.put("correct", input.getCorrect());
        map.put("proposedCodes", new ArrayList<>(input.getProposedCodes()));
        List<Map<String, Object>> evidence = new ArrayList<>();
        if (input.getEvidence() != null) {
            for (EvidenceReadService.EvidenceRecord record : input.getEvidence().getRecords()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("eventId", record.getEventId());
                item.put("nodeCode", record.getNodeCode());
                item.put("result", record.getResult());
                item.put("firstObservation", record.isFirstObservation());
                item.put("helpLevel", record.getHelpLevel());
                item.put("diagnostic", record.isDiagnostic());
                evidence.add(item);
            }
        }
        map.put("evidence", evidence);
        List<Map<String, Object>> verifications = new ArrayList<>();
        for (DiagnosisService.VerificationOutcome outcome : input.getCompletedVerifications()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventId", outcome.getEventId());
            item.put("correct", outcome.isCorrect());
            item.put("discriminates", new ArrayList<>(outcome.getDiscriminates()));
            verifications.add(item);
        }
        map.put("completedVerifications", verifications);
        return map;
    }

    private List<Map<String, Object>> hypothesisMaps(List<Diagnosis.Hypothesis> hypotheses) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Diagnosis.Hypothesis hypothesis : hypotheses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", hypothesis.getCode());
            item.put("label", hypothesis.getLabel());
            out.add(item);
        }
        return out;
    }

    private List<Map<String, Object>> verificationMaps(List<DiagnosisService.VerificationTask> tasks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DiagnosisService.VerificationTask task : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", task.isQuestion() ? "QUESTION" : "PREDICT");
            item.put("predictCase", task.getPredictCase());
            item.put("questionCode", task.getQuestionCode());
            item.put("discriminates", new ArrayList<>(task.getDiscriminates()));
            item.put("reason", task.getReason());
            item.put("estimatedMinutes", task.getEstimatedMinutes());
            out.add(item);
        }
        return out;
    }

    private boolean allQuestionVerifications(DiagnosisService.Result result) {
        if (result.getVerifications().isEmpty()) {
            return false;
        }
        for (DiagnosisService.VerificationTask task : result.getVerifications()) {
            if (!task.isQuestion() || task.getQuestionCode() == null
                    || !task.getQuestionCode().startsWith("q.db.fd.")) {
                return false;
            }
        }
        return true;
    }

    private boolean noPredictVerifications(DiagnosisService.Result result) {
        for (DiagnosisService.VerificationTask task : result.getVerifications()) {
            if (task.getPredictCase() != null) {
                return false;
            }
        }
        return true;
    }

    private long countStatus(String status) {
        return cases.stream()
                .filter(item -> status.equals(item.get("status")))
                .count();
    }

    // endregion

    // region fixtures

    private DiagnosisService.Input osInput() {
        DiagnosisService.Input input = new DiagnosisService.Input();
        input.setUserId(7L);
        input.setCourseCode(OS_COURSE);
        input.setSkillKeys(new ArrayList<>(List.of(OS_NODE)));
        return input;
    }

    private DiagnosisService.Input dbInput(String skillKey) {
        DiagnosisService.Input input = new DiagnosisService.Input();
        input.setUserId(7L);
        input.setCourseCode(DB_COURSE);
        input.setSkillKeys(new ArrayList<>(List.of(skillKey)));
        return input;
    }

    private EvidenceReadService.EvidenceSummary summary(EvidenceReadService.EvidenceRecord... records) {
        EvidenceReadService.EvidenceSummary summary = new EvidenceReadService.EvidenceSummary();
        summary.setRecords(new ArrayList<>(Arrays.asList(records)));
        return summary;
    }

    private EvidenceReadService.EvidenceRecord osRecord(String eventId, String result,
                                                        boolean firstObservation,
                                                        String helpLevel, boolean diagnostic) {
        return record(eventId, result, firstObservation, helpLevel, diagnostic, OS_NODE, "LRU 页面置换算法");
    }

    private EvidenceReadService.EvidenceRecord dbRecord(String eventId, String result,
                                                        boolean firstObservation,
                                                        String helpLevel, boolean diagnostic,
                                                        String nodeCode) {
        return record(eventId, result, firstObservation, helpLevel, diagnostic, nodeCode, "数据库函数依赖");
    }

    private EvidenceReadService.EvidenceRecord record(String eventId, String result,
                                                      boolean firstObservation, String helpLevel,
                                                      boolean diagnostic, String nodeCode, String nodeName) {
        EvidenceReadService.EvidenceRecord record = new EvidenceReadService.EvidenceRecord();
        record.setEventId(eventId);
        record.setNodeCode(nodeCode);
        record.setNodeName(nodeName);
        record.setEventType("JUDGE");
        record.setResult(result);
        record.setFirstObservation(firstObservation);
        record.setHelpLevel(helpLevel);
        record.setDiagnostic(diagnostic);
        return record;
    }

    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("后端代码"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    // endregion
}
