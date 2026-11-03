package com.yupi.springbootinit.ai.diagnosis;

import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 诊断服务的回归（计划 §5.4 / 任务书 §4）。
 *
 * <p>核心要求：证据不足时绝不强行下结论；带提示或看答案的失败、
 * 编译错误/系统错误这类诊断性事件都不能当作能力证据；
 * 模型只能在版本化目录内提议原因代码，越界提议一律丢弃。
 *
 * <p>使用真实的误区目录与预测案例目录（不 mock），
 * 因此"目录与判定规则不一致"会直接暴露。
 */
class DiagnosisServiceTest {

    private static final String COURSE = "course.operating-systems";
    private static final String NODE = "os.memory.lru";

    private final MisconceptionCatalog catalog = new MisconceptionCatalog();
    private final PredictCaseCatalog predictCases = new PredictCaseCatalog();
    private final DiagnosisService service = new DiagnosisService();

    @BeforeEach
    void setUp() {
        catalog.load();
        predictCases.init();
        ReflectionTestUtils.setField(service, "catalog", catalog);
        ReflectionTestUtils.setField(service, "predictCases", predictCases);
    }

    // region 证据不足

    @Test
    void noEvidenceAtAllIsInsufficientAndNeverForcesACause() {
        DiagnosisService.Result result = service.diagnose(input(NODE));

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, result.getDiagnosis().getStatus());
        assertTrue(result.getDiagnosis().getSupportingEventIds().isEmpty());
        assertFalse(result.getDiagnosis().getHypotheses().isEmpty(),
                "可以列出待区分的候选原因，但不能给出结论");
        assertTrue(result.getDiagnosis().getExplanation().contains("证据不足"));
    }

    @Test
    void missingCourseScopeIsInsufficient() {
        DiagnosisService.Input input = new DiagnosisService.Input();
        input.setUserId(7L);
        input.setSkillKeys(new ArrayList<>(List.of(NODE)));

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, service.diagnose(input).getDiagnosis().getStatus());
    }

    @Test
    void anotherCoursesCatalogIsNotUsed() {
        DiagnosisService.Input input = input(NODE);
        input.setCourseCode("course.data-structures");

        DiagnosisService.Result result = service.diagnose(input);
        assertEquals(Diagnosis.STATUS_INSUFFICIENT, result.getDiagnosis().getStatus());
        assertTrue(result.getMatchedSkills().isEmpty(), "不得借用别的课程的误区映射");
    }

    @Test
    void hintedFailuresAreNotSupportingEvidence() {
        EvidenceReadService.EvidenceSummary evidence = summary(
                record("e1", "FAIL", false, EvidenceQualification.HELP_HINT, false),
                record("e2", "FAIL", true, EvidenceQualification.HELP_ANSWER_REVEALED, false));
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(evidence);

        DiagnosisService.Result result = service.diagnose(input);

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, result.getDiagnosis().getStatus(),
                "带提示/看答案的失败不是独立观测，不能支撑原因判断");
        assertTrue(result.getDiagnosis().getSupportingEventIds().isEmpty());
    }

    @Test
    void repeatedAttemptsAreNotSupportingEvidence() {
        EvidenceReadService.EvidenceSummary evidence = summary(
                record("e1", "FAIL", false, EvidenceQualification.HELP_NONE, false));
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(evidence);

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, service.diagnose(input).getDiagnosis().getStatus());
    }

    @Test
    void compileAndSystemErrorsAreDiagnosticNotCapabilityEvidence() {
        EvidenceReadService.EvidenceSummary evidence = summary(
                record("e1", "COMPILE_ERROR", true, EvidenceQualification.HELP_NONE, true),
                record("e2", "SYSTEM_ERROR", true, EvidenceQualification.HELP_NONE, true));
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(evidence);

        DiagnosisService.Result result = service.diagnose(input);

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, result.getDiagnosis().getStatus(),
                "编译错误/系统错误不得被当成能力不足的证据");
        assertTrue(result.getDiagnosis().getSupportingEventIds().isEmpty());
    }

    @Test
    void outOfScopeNodeEvidenceIsIgnored() {
        EvidenceReadService.EvidenceRecord other = record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false);
        other.setNodeCode("os.cpu.scheduling");
        other.setNodeName("处理机调度");
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(other));

        assertEquals(Diagnosis.STATUS_INSUFFICIENT, service.diagnose(input).getDiagnosis().getStatus());
    }

    // endregion

    // region 有证据时的判定

    @Test
    void oneIndependentFailureYieldsTentativeWithSupportingEvent() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertEquals(Diagnosis.STATUS_TENTATIVE, diagnosis.getStatus(),
                "单次独立失败只能给待验证结论，不能直接 SUPPORTED");
        assertEquals(List.of("e1"), diagnosis.getSupportingEventIds());
        assertTrue(diagnosis.getConflictingEventIds().isEmpty());
        assertFalse(diagnosis.getHypotheses().isEmpty());
        assertTrue(diagnosis.getHypotheses().size() <= 3, "候选原因最多 3 个");
    }

    @Test
    void independentPassesAreListedAsConflicting() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(
                record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false),
                record("e2", "PASS", true, EvidenceQualification.HELP_NONE, false)));

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertEquals(List.of("e1"), diagnosis.getSupportingEventIds());
        assertEquals(List.of("e2"), diagnosis.getConflictingEventIds());
    }

    @Test
    void helpedPassesAreNotConflictingEvidence() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(
                record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false),
                record("e2", "PASS", false, EvidenceQualification.HELP_HINT, false)));

        assertTrue(service.diagnose(input).getDiagnosis().getConflictingEventIds().isEmpty());
    }

    @Test
    void completedVerificationNarrowsToOneSupportedCause() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));
        // 候选被限制到两个，答对其中一个案例即可排除对应误区
        input.setProposedCodes(new ArrayList<>(Arrays.asList("lru.hit-no-update", "fifo.lru-confusion")));
        DiagnosisService.VerificationOutcome outcome = new DiagnosisService.VerificationOutcome();
        outcome.setPredictCase("page.lru-vs-fifo.victim");
        outcome.setCorrect(true);
        outcome.setEventId("e9");
        outcome.setDiscriminates(new ArrayList<>(Arrays.asList("lru.hit-no-update", "fifo.lru-confusion")));
        input.setCompletedVerifications(new ArrayList<>(List.of(outcome)));

        DiagnosisService.Result result = service.diagnose(input);
        Diagnosis diagnosis = result.getDiagnosis();

        // 该案例同时排除两条候选：没有剩余原因时不得强行给出 SUPPORTED 结论
        assertNotEquals(Diagnosis.STATUS_SUPPORTED, diagnosis.getStatus(),
                "全部候选都被排除后不能伪造一个被支撑的原因");
        assertTrue(diagnosis.getHypotheses().stream()
                        .noneMatch(hypothesis -> "lru.hit-no-update".equals(hypothesis.getCode())
                                && Diagnosis.STATUS_SUPPORTED.equals(diagnosis.getStatus())),
                "已被区分任务排除的原因不得作为结论保留");
        assertTrue(diagnosis.getConflictingEventIds().contains("e9"),
                "答对的区分任务要作为反证记录");
        assertNotNull(diagnosis.getExplanation());
        assertFalse(result.getMatchedSkills().isEmpty());
    }

    @Test
    void supportedRequiresExactlyOneRemainingCause() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));
        input.setProposedCodes(new ArrayList<>(Arrays.asList("lru.hit-no-update", "fifo.lru-confusion")));
        DiagnosisService.VerificationOutcome outcome = new DiagnosisService.VerificationOutcome();
        outcome.setPredictCase("page.lru.hit-update-order");
        outcome.setCorrect(true);
        outcome.setEventId("e9");
        // 只有"命中后不更新顺序"被排除，剩下唯一候选
        outcome.setDiscriminates(new ArrayList<>(List.of("lru.hit-no-update")));
        input.setCompletedVerifications(new ArrayList<>(List.of(outcome)));

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertEquals(Diagnosis.STATUS_SUPPORTED, diagnosis.getStatus());
        assertEquals(1, diagnosis.getHypotheses().size());
        assertEquals("fifo.lru-confusion", diagnosis.getHypotheses().get(0).getCode());
        assertTrue(diagnosis.getExplanation().contains("掌握标准"),
                "结论必须说明它不替代掌握标准判定");
    }

    @Test
    void failedVerificationDoesNotRuleAnythingOut() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));
        input.setProposedCodes(new ArrayList<>(Arrays.asList("lru.hit-no-update", "fifo.lru-confusion")));
        DiagnosisService.VerificationOutcome outcome = new DiagnosisService.VerificationOutcome();
        outcome.setPredictCase("page.lru-vs-fifo.victim");
        outcome.setCorrect(false);
        outcome.setEventId("e9");
        outcome.setDiscriminates(new ArrayList<>(List.of("lru.hit-no-update")));
        input.setCompletedVerifications(new ArrayList<>(List.of(outcome)));

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertNotEquals(Diagnosis.STATUS_SUPPORTED, diagnosis.getStatus(), "答错不能排除任何原因");
        assertTrue(diagnosis.getSupportingEventIds().contains("e9"));
    }

    // endregion

    // region 目录边界

    @Test
    void modelProposedCodesOutsideTheCatalogAreRejected() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));
        input.setProposedCodes(new ArrayList<>(List.of("made.up.misconception", "student.is.lazy")));

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertTrue(diagnosis.getHypotheses().stream()
                        .noneMatch(hypothesis -> hypothesis.getCode().startsWith("made.up")),
                "模型不得凭空生成原因代码");
        assertTrue(diagnosis.getHypotheses().stream()
                .allMatch(hypothesis -> catalog.find(COURSE, hypothesis.getCode()) != null));
    }

    @Test
    void hypothesesAreCappedAtThreeAndVerificationsAtTwo() {
        DiagnosisService.Input input = input(NODE);
        DiagnosisService.Result result = service.diagnose(input);

        assertTrue(result.getDiagnosis().getHypotheses().size() <= 3);
        assertTrue(result.getVerifications().size() <= 2);
        for (PredictCaseCatalog.PredictCase predictCase : result.getVerifications()) {
            assertNotNull(catalog.find(COURSE, predictCase.getDiscriminates().get(0)),
                    "区分任务必须能对应目录里的误区");
        }
    }

    @Test
    void catalogIsVersionedAndScopedToTheCourse() {
        assertEquals("os-page-replacement/1", catalog.version());
        assertFalse(catalog.isEmpty());
        assertFalse(catalog.match(COURSE, List.of(NODE)).isEmpty());
        assertTrue(catalog.match("course.unknown", List.of(NODE)).isEmpty());
        assertNotNull(catalog.find(COURSE, "lru.hit-no-update"));
        assertNull(catalog.find("course.unknown", "lru.hit-no-update"));
    }

    @Test
    void chosenOptionHintNarrowsTheCandidates() {
        DiagnosisService.Input input = input(NODE);
        input.setEvidence(summary(record("e1", "FAIL", true, EvidenceQualification.HELP_NONE, false)));
        input.setChosenOptionLabel("访问序列顺序不变，命中不影响淘汰");

        Diagnosis diagnosis = service.diagnose(input).getDiagnosis();

        assertTrue(diagnosis.getHypotheses().stream()
                        .anyMatch(hypothesis -> "lru.hit-no-update".equals(hypothesis.getCode())),
                "选项文本命中误区标注时，该原因应进入候选");
    }

    // endregion

    // region fixtures

    private DiagnosisService.Input input(String nodeCode) {
        DiagnosisService.Input input = new DiagnosisService.Input();
        input.setUserId(7L);
        input.setCourseCode(COURSE);
        input.setSkillKeys(new ArrayList<>(List.of(nodeCode)));
        return input;
    }

    private EvidenceReadService.EvidenceSummary summary(EvidenceReadService.EvidenceRecord... records) {
        EvidenceReadService.EvidenceSummary summary = new EvidenceReadService.EvidenceSummary();
        summary.setRecords(new ArrayList<>(Arrays.asList(records)));
        return summary;
    }

    private EvidenceReadService.EvidenceRecord record(String eventId, String result, boolean firstObservation,
                                                      String helpLevel, boolean diagnostic) {
        EvidenceReadService.EvidenceRecord record = new EvidenceReadService.EvidenceRecord();
        record.setEventId(eventId);
        record.setNodeCode(NODE);
        record.setNodeName("LRU 页面置换算法");
        record.setEventType("JUDGE");
        record.setResult(result);
        record.setFirstObservation(firstObservation);
        record.setHelpLevel(helpLevel);
        record.setDiagnostic(diagnostic);
        return record;
    }

    // endregion
}
