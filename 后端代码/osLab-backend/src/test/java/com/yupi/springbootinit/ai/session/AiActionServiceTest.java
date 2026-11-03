package com.yupi.springbootinit.ai.session;

import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.LearningEvidenceContextService;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.ai.plan.ReviewPlanner;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.ActionSubmitResultVO;
import com.yupi.springbootinit.model.vo.ai.ActivityRefVO;
import com.yupi.springbootinit.model.vo.ai.EvidenceSnapshot;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.TargetRef;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.LearningQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 动作执行的回归（任务书 §3/§4）：
 * 归属校验、submissionKey 幂等、判分一律委托原有服务、
 * 帮助等级在判分事务之前落库、作答前不泄露答案。
 */
class AiActionServiceTest {

    private final AiSessionService sessionService = mock(AiSessionService.class);
    private final LearningEvidenceContextService evidenceContexts = mock(LearningEvidenceContextService.class);
    private final LearningQuestionService questionService = mock(LearningQuestionService.class);
    private final LearningEventService eventService = mock(LearningEventService.class);
    private final ReviewPlanner reviewPlanner = mock(ReviewPlanner.class);
    private final AiMaterialChunkMapper chunkMapper = mock(AiMaterialChunkMapper.class);
    /** 预测目录用真实实例：答案由 PageReplacer 现算，测试不能替它编答案。 */
    private final PredictCaseCatalog predictCases = new PredictCaseCatalog();
    private final AiActionService service = new AiActionService();

    private final User owner = new User();
    private final User intruder = new User();
    private AiLearningSession session;
    private LearningQuestion question;
    private LearningEvidenceContext context;

    @BeforeEach
    void setUp() {
        predictCases.init();
        owner.setId(7L);
        intruder.setId(8L);

        session = new AiLearningSession();
        session.setId(31L);
        session.setUserId(7L);
        session.setCourseCode("course.operating-systems");
        session.setUnitCode("unit.memory");
        session.setFocus(AiSessionService.FOCUS_LEARN);
        session.setTimeBudgetMinutes(20);
        session.setStatus(AiSessionService.STATUS_ACTIVE);
        session.setRowVersion(0);

        question = new LearningQuestion();
        question.setId(101L);
        question.setUserId(9L);
        question.setType("SINGLE");
        question.setTitle("LRU 换出哪一页");
        question.setContent("三个物理块，访问 1,2,3,1,4，LRU 换出？");
        question.setOptions("{\"A\":\"页面 1\",\"B\":\"页面 2\",\"C\":\"页面 3\"}");
        question.setAnswer("\"B\"");
        question.setAnalysis("LRU 命中 1 后更新顺序，最久未用的是 2。");
        question.setRevision(2);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);

        context = new LearningEvidenceContext();
        context.setId(61L);
        context.setUserId(7L);
        context.setHelpLevel(EvidenceQualification.HELP_NONE);

        ReflectionTestUtils.setField(service, "sessionService", sessionService);
        ReflectionTestUtils.setField(service, "evidenceContexts", evidenceContexts);
        ReflectionTestUtils.setField(service, "learningQuestionService", questionService);
        ReflectionTestUtils.setField(service, "learningEventService", eventService);
        ReflectionTestUtils.setField(service, "predictCases", predictCases);
        ReflectionTestUtils.setField(service, "reviewPlanner", reviewPlanner);
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);

        when(sessionService.requireOwned(eq(owner), anyLong())).thenReturn(session);
        when(sessionService.requireOwned(eq(intruder), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该学习会话"));
        when(sessionService.plan(session)).thenAnswer(invocation -> plan(questionAction(), predictAction()));
        when(evidenceContexts.begin(eq(7L), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyLong(), any())).thenReturn(context);
        when(evidenceContexts.lockAndQualify(7L, 61L))
                .thenReturn(EvidenceQualification.of(61L, EvidenceQualification.HELP_NONE, null));
        when(evidenceContexts.snapshot(any(), any(), anyString(), anyList())).thenReturn(new EvidenceSnapshot());
        when(questionService.getById(101L)).thenReturn(question);
        when(eventService.eventIdsOfAttempt(eq(7L), any())).thenReturn(List.of(1001L));
    }

    // region 权限

    @Test
    void startIsRejectedForAnotherUsersAction() {
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.start(intruder, "na-31-0")).getCode());
        verifyNoInteractions(evidenceContexts, questionService);
    }

    @Test
    void submitIsRejectedForAnotherUsersAction() {
        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-1");
        request.setChoiceKey("B");
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(intruder, "na-31-0", request)).getCode());
        verifyNoInteractions(evidenceContexts, questionService);
    }

    @Test
    void malformedActionIdIsRejectedBeforeAnyLookup() {
        assertThrows(BusinessException.class, () -> service.start(owner, "not-an-action"));
        assertThrows(BusinessException.class, () -> service.start(owner, null));
        verifyNoInteractions(sessionService);
    }

    @Test
    void actionOutsideTheCurrentPlanIsNotFound() {
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.start(owner, "na-31-9")).getCode());
    }

    @Test
    void helpOnUnassessedActionIsRejected() {
        when(sessionService.plan(session)).thenAnswer(invocation -> plan(readAction()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.recordHelp(owner, "na-31-0", "HINT", null, null)).getCode());
        verifyNoInteractions(evidenceContexts);
    }

    // endregion

    // region 作答前不泄露答案

    @Test
    void questionStartNeverSendsAnalysisOrCorrectAnswer() {
        LearningQuestionVO vo = new LearningQuestionVO();
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setType("SINGLE");
        vo.setOptions(question.getOptions());
        // 故意把解析放进 VO：即使上游给了，activityRef 也不得下发（LearningQuestionVO 契约本身永不含 answer）
        vo.setAnalysis(question.getAnalysis());
        when(questionService.getQuestionVO(101L, owner)).thenReturn(vo);

        ActivityRefVO ref = service.start(owner, "na-31-0");

        assertEquals(question.getContent(), ref.getPrompt());
        assertEquals(3, ref.getOptions().size());
        assertTrue(ref.getOptions().stream().noneMatch(option -> option.getLabel().contains("最久未用")),
                "选项本身不含解析");
        assertEquals(EvidenceQualification.HELP_NONE, ref.getHelpLevel(), "帮助等级从 NONE 起");
        assertNotNull(ref.getSubmissionKey());
        // activityRef 是唯一下发点：解析与标准答案不得出现在其中
        String serialized = com.yupi.springbootinit.ai.AiJson.write(ref);
        assertFalse(serialized.contains("最久未用"), "start 响应不得包含解析内容");
        assertFalse(serialized.contains("answer"), "start 响应不得包含标准答案字段");
    }

    @Test
    void predictStartOnlySendsTheVisiblePrefix() {
        ActivityRefVO ref = service.start(owner, "na-31-1");

        assertEquals("PREDICT", ref.getType());
        assertEquals(3, ref.getFrames());
        // 计划 §5.4 的例子：待预测步为第 5 次访问（下标 4）
        assertEquals(4, ref.getPredictStepIndex());
        assertEquals(List.of(1, 2, 3, 1, 4), ref.getVisibleSequence());
        assertEquals(4, ref.getVisibleSteps().size(), "只下发待预测步之前的步骤");
        for (ActivityRefVO.PredictStep step : ref.getVisibleSteps()) {
            assertNull(step.getEvicted(), "可见步骤不含换出页，答案不会提前暴露");
        }
        String serialized = com.yupi.springbootinit.ai.AiJson.write(ref);
        assertFalse(serialized.contains("explanation"), "解释只在提交后下发");
    }

    // endregion

    // region 判分委托与幂等

    @Test
    void gradingIsDelegatedToTheOriginalServiceWithTheEvidenceContext() {
        LearningAnswerResultVO graded = new LearningAnswerResultVO();
        graded.setCorrect(true);
        graded.setAttemptId(88L);
        graded.setIsFirst(true);
        graded.setAnalysis(question.getAnalysis());
        when(questionService.answerQuestion(any(), eq(owner), eq(61L))).thenReturn(graded);

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-1");
        request.setChoiceKey("B");
        ActionSubmitResultVO result = service.submit(owner, "na-31-0", request);

        assertEquals("PASS", result.getResult());
        assertTrue(result.getCorrect());
        assertEquals(question.getAnalysis(), result.getAnalysis(), "解析只在提交后下发");
        ArgumentCaptor<LearningAnswerRequest> captor = ArgumentCaptor.forClass(LearningAnswerRequest.class);
        verify(questionService).answerQuestion(captor.capture(), eq(owner), eq(61L));
        assertEquals(101L, captor.getValue().getQuestionId());
        assertEquals("B", captor.getValue().getAnswer());
        assertEquals("sub-1", captor.getValue().getSubmissionKey(),
                "submissionKey 必须原样透传，幂等由原有作答服务保证");
        verify(evidenceContexts).consume(7L, 61L, "PASS", 88L, List.of(1001L));
    }

    @Test
    void selfReportedHelpIsRecordedBeforeTheGradingTransaction() {
        LearningAnswerResultVO graded = new LearningAnswerResultVO();
        graded.setCorrect(false);
        graded.setAttemptId(88L);
        when(questionService.answerQuestion(any(), eq(owner), eq(61L))).thenReturn(graded);
        when(evidenceContexts.recordHelp(eq(7L), eq(61L), anyString(), eq("EXTERNAL_AI"), anyString()))
                .thenReturn(EvidenceQualification.of(61L, EvidenceQualification.HELP_NONE, "EXTERNAL_AI"));

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-1");
        request.setChoiceKey("A");
        request.setSelfReportedHelp("EXTERNAL_AI");
        service.submit(owner, "na-31-0", request);

        InOrder inOrder = inOrder(evidenceContexts, questionService);
        inOrder.verify(evidenceContexts).begin(eq(7L), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyLong(), any());
        inOrder.verify(evidenceContexts).recordHelp(eq(7L), eq(61L), anyString(), eq("EXTERNAL_AI"), anyString());
        inOrder.verify(questionService).answerQuestion(any(), eq(owner), eq(61L));
        inOrder.verify(evidenceContexts).lockAndQualify(7L, 61L);
    }

    @Test
    void hintedAttemptIsNotCountedAsAnIndependentObservation() {
        when(evidenceContexts.lockAndQualify(7L, 61L)).thenReturn(
                EvidenceQualification.of(61L, EvidenceQualification.HELP_HINT, null));
        LearningAnswerResultVO graded = new LearningAnswerResultVO();
        graded.setCorrect(true);
        graded.setAttemptId(88L);
        when(questionService.answerQuestion(any(), eq(owner), eq(61L))).thenReturn(graded);

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-1");
        request.setChoiceKey("B");
        ActionSubmitResultVO result = service.submit(owner, "na-31-0", request);

        assertEquals("PASS", result.getResult(), "成绩事实源不变：仍然判对");
        assertNotNull(result.getEvidence(), "证据快照必须随提交结果一起返回");
        verify(evidenceContexts).snapshot(argThat(qualification ->
                        qualification != null && !qualification.isEligibleForObservation()),
                any(), eq("PASS"), anyList());
    }

    @Test
    void submitRequiresSubmissionKeyForIdempotency() {
        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setChoiceKey("B");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(owner, "na-31-0", request)).getCode());
        verifyNoInteractions(questionService, evidenceContexts);
    }

    @Test
    void repeatedPredictSubmissionUsesAStableIdempotencyKey() {
        when(eventService.recordSimulationEvent(eq(7L), anyString(), anyString(), anyBoolean(), anyString()))
                .thenReturn(2001L);

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-predict");
        request.setPredictedProcess("2");
        ActionSubmitResultVO first = service.submit(owner, "na-31-1", request);
        ActionSubmitResultVO replay = service.submit(owner, "na-31-1", request);

        assertEquals("PASS", first.getResult());
        assertEquals(first.getResult(), replay.getResult());
        // 幂等键固定为案例编码：刷新、换会话、换模型都不会产生第二次首次观测
        verify(eventService, times(2)).recordSimulationEvent(eq(7L), eq("os.memory.lru"),
                eq("ai-predict:page.lru-vs-fifo.victim"), eq(true), anyString());
        assertTrue(first.getAnalysis().contains("LRU"), "提交后才下发完整解释");
        assertFalse(first.getDiffSteps().isEmpty());
    }

    @Test
    void hintedPredictDoesNotWriteANewSimulationEvent() {
        when(evidenceContexts.lockAndQualify(7L, 61L)).thenReturn(
                EvidenceQualification.of(61L, EvidenceQualification.HELP_ANSWER_REVEALED, null));

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-predict");
        request.setPredictedProcess("2");
        ActionSubmitResultVO result = service.submit(owner, "na-31-1", request);

        assertEquals("PASS", result.getResult(), "判分照实，但不再计入独立观测");
        verify(eventService, never()).recordSimulationEvent(anyLong(), anyString(), anyString(),
                anyBoolean(), anyString());
        verify(evidenceContexts).consume(7L, 61L, "PASS", null, new ArrayList<>());
    }

    @Test
    void wrongPredictIsReportedWithTheCalculatorResult() {
        when(eventService.recordSimulationEvent(eq(7L), anyString(), anyString(), anyBoolean(), anyString()))
                .thenReturn(2002L);

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-predict");
        request.setPredictedProcess("1");
        ActionSubmitResultVO result = service.submit(owner, "na-31-1", request);

        assertEquals("FAIL", result.getResult());
        assertFalse(result.getCorrect());
        assertTrue(result.getJudgeMessage().contains("实际 2"), "要告诉学生计算器给出的真值");
    }

    @Test
    void predictWithoutAnswerIsRejected() {
        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-predict");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(owner, "na-31-1", request)).getCode());
        request.setPredictedProcess("abc");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(owner, "na-31-1", request)).getCode());
        verifyNoInteractions(eventService);
    }

    @Test
    void unknownPredictCaseIsNotFoundRatherThanInvented() {
        when(sessionService.plan(session)).thenAnswer(invocation -> plan(predictAction("page.no.such.case")));
        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-predict");
        request.setPredictedProcess("2");
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(owner, "na-31-0", request)).getCode());
    }

    // endregion

    // region 非评估任务

    @Test
    void readingTaskIsRecordedWithoutPretendingToBeAnAssessment() {
        when(sessionService.plan(session)).thenAnswer(invocation -> plan(readAction()));

        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-read");
        ActionSubmitResultVO result = service.submit(owner, "na-31-0", request);

        assertEquals("RECORDED", result.getResult());
        assertNull(result.getCorrect(), "阅读任务不是判分，correct 必须为 null");
        assertTrue(result.getJudgeMessage().contains("不是一次判分"));
        verifyNoInteractions(questionService, eventService);
        verify(evidenceContexts, never()).consume(anyLong(), anyLong(), anyString(), any(), anyList());
    }

    @Test
    void explainTaskRequiresTheStudentsOwnWords() {
        when(sessionService.plan(session)).thenAnswer(invocation -> plan(explainAction()));
        AiRequests.SubmitAction request = new AiRequests.SubmitAction();
        request.setSubmissionKey("sub-explain");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.submit(owner, "na-31-0", request)).getCode());

        request.setText("因为命中后要把该页移到最近使用端。");
        assertEquals("RECORDED", service.submit(owner, "na-31-0", request).getResult());
    }

    // endregion

    // region 答案泄露兜底

    @Test
    void leakedCorrectOptionEscalatesHelpBeforeSubmission() {
        session.setCurrentActionJson(com.yupi.springbootinit.ai.AiJson.write(questionAction()));
        when(evidenceContexts.findByKey(7L, LearningEvidenceContextService.actionContextKey("na-31-0")))
                .thenReturn(context);

        service.revealIfAnswerLeaked(owner, session,
                "这道题的正确选项是「页面 2」，因为命中后 LRU 会更新顺序。");

        verify(evidenceContexts).recordHelp(eq(7L), eq(61L),
                eq(EvidenceQualification.HELP_ANSWER_REVEALED), any(), anyString());
    }

    @Test
    void ordinaryExplanationDoesNotEscalateHelp() {
        session.setCurrentActionJson(com.yupi.springbootinit.ai.AiJson.write(questionAction()));

        service.revealIfAnswerLeaked(owner, session,
                "先回忆 LRU 的定义：命中后要把该页移到最近使用端，再自己算一遍。");

        verify(evidenceContexts, never()).recordHelp(anyLong(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void leakGuardIgnoresSessionsWithoutAnOpenAssessedTask() {
        session.setCurrentActionJson(null);
        service.revealIfAnswerLeaked(owner, session, "正确选项是「页面 2」");
        service.revealIfAnswerLeaked(owner, null, "正确选项是「页面 2」");
        service.revealIfAnswerLeaked(owner, session, "   ");
        verifyNoInteractions(evidenceContexts);
    }

    // endregion

    // region fixtures

    private List<NextAction> plan(NextAction... actions) {
        List<NextAction> out = new ArrayList<>();
        for (int i = 0; i < actions.length; i++) {
            NextAction action = actions[i];
            action.setActionId(AiSessionService.actionId(31L, i));
            out.add(action);
        }
        return out;
    }

    private NextAction questionAction() {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_QUESTION);
        action.setReason("你在该知识点上有一次独立失败观测");
        action.setEstimatedMinutes(3);
        action.setStatus(NextAction.STATUS_PENDING);
        TargetRef target = new TargetRef();
        target.setKind(TargetRef.KIND_QUESTION);
        target.setCode("101");
        action.setTargetRef(target);
        return action;
    }

    private NextAction predictAction() {
        return predictAction("page.lru-vs-fifo.victim");
    }

    private NextAction predictAction(String caseCode) {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_PREDICT);
        action.setReason("用于区分「混淆 FIFO 与 LRU」和「命中后不更新顺序」");
        action.setEstimatedMinutes(3);
        action.setStatus(NextAction.STATUS_PENDING);
        TargetRef target = new TargetRef();
        target.setKind(TargetRef.KIND_SIMULATION);
        target.setCode(caseCode);
        action.setTargetRef(target);
        return action;
    }

    private NextAction readAction() {
        NextAction action = new NextAction();
        action.setType(NextAction.TYPE_READ);
        action.setReason("先补齐页面置换的基本定义");
        action.setEstimatedMinutes(4);
        action.setStatus(NextAction.STATUS_PENDING);
        TargetRef target = new TargetRef();
        target.setKind(TargetRef.KIND_UNIT);
        target.setCode("unit.memory");
        action.setTargetRef(target);
        return action;
    }

    private NextAction explainAction() {
        NextAction action = readAction();
        action.setType(NextAction.TYPE_EXPLAIN);
        action.setReason("用自己的话解释命中后为什么要更新顺序");
        return action;
    }

    // endregion
}
