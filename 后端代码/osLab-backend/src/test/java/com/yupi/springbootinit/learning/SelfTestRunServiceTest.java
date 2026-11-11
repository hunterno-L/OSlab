package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningAssessmentDraftMapper;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.learning.SelfTestDraftSaveRequest;
import com.yupi.springbootinit.model.entity.LearningAssessmentDraft;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.MasteryChangeVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestDraftSaveVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestResultVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestRunVO;
import com.yupi.springbootinit.service.LearningEventService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 整组自测核心不变量：
 * 草稿不判分/不发事件；版本冲突不覆盖；交卷幂等；未答/综合/失效题处理准确。
 */
class SelfTestRunServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LearningAssessmentRunMapper runMapper = mock(LearningAssessmentRunMapper.class);
    private final LearningAssessmentDraftMapper draftMapper = mock(LearningAssessmentDraftMapper.class);
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final LearningAttemptMapper attemptMapper = mock(LearningAttemptMapper.class);
    private final LearningEventService eventService = mock(LearningEventService.class);
    private final SelfTestPolicy selfTestPolicy = mock(SelfTestPolicy.class);
    private final SelfTestRunService service = new SelfTestRunService();

    private final User user = new User();
    private LearningAssessmentRun run;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "selftest-run-test");
        TableInfoHelper.initTableInfo(assistant, LearningAssessmentDraft.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);

        user.setId(7L);
        run = new LearningAssessmentRun();
        run.setId(41L);
        run.setUserId(7L);
        run.setStatus("ACTIVE");
        run.setMode("SELF_TEST");
        run.setRowVersion(3);
        run.setQuestionIdsJson("[101,102]");
        run.setQuestionRevisionsJson("{\"101\":1,\"102\":1}");
        when(runMapper.selectByIdForUpdate(41L)).thenReturn(run);
        when(runMapper.selectById(41L)).thenReturn(run);
        when(runMapper.updateById(any())).thenReturn(1);
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question(101L, "SINGLE"), question(102L, "SINGLE")));
        when(nodeMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(attemptMapper.insert(any(LearningAttempt.class))).thenAnswer(invocation -> {
            LearningAttempt attempt = invocation.getArgument(0);
            attempt.setId(88L);
            return 1;
        });
        when(eventService.recordAttemptEvents(anyLong(), any(), anyLong(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new ArrayList<MasteryChangeVO>());
        when(eventService.eventIdsOfAttempt(anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(selfTestPolicy.queueIds(any())).thenAnswer(invocation -> {
            LearningAssessmentRun value = invocation.getArgument(0);
            return JSONUtil.toList(value.getQuestionIdsJson(), Long.class);
        });

        ReflectionTestUtils.setField(service, "runMapper", runMapper);
        ReflectionTestUtils.setField(service, "draftMapper", draftMapper);
        ReflectionTestUtils.setField(service, "learningQuestionMapper", questionMapper);
        ReflectionTestUtils.setField(service, "questionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "attemptMapper", attemptMapper);
        ReflectionTestUtils.setField(service, "learningEventService", eventService);
        ReflectionTestUtils.setField(service, "selfTestPolicy", selfTestPolicy);
    }

    @Test
    void saveDraftsOnlyWritesDraftsAndNeverGrades() {
        when(draftMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(draftMapper.insert(any())).thenReturn(1);
        when(runMapper.bumpDraftVersion(41L, "save-1", 3)).thenReturn(1);
        SelfTestDraftSaveRequest request = saveRequest(3, "save-1", 101L, "A", true);

        SelfTestDraftSaveVO result = service.saveDrafts(user, 41L, request);

        assertFalse(result.getConflict());
        assertEquals(4, result.getRowVersion());
        verify(runMapper).bumpDraftVersion(41L, "save-1", 3);
        verifyNoInteractions(attemptMapper, eventService);
    }

    @Test
    void savedDraftAnswerIsJsonAndReadableFromView() {
        when(draftMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(draftMapper.insert(any(LearningAssessmentDraft.class))).thenReturn(1);
        when(runMapper.bumpDraftVersion(41L, "save-roundtrip", 3)).thenReturn(1);

        service.saveDrafts(user, 41L, saveRequest(3, "save-roundtrip", 101L, "A", true));

        ArgumentCaptor<LearningAssessmentDraft> captor = ArgumentCaptor.forClass(LearningAssessmentDraft.class);
        verify(draftMapper).insert(captor.capture());
        LearningAssessmentDraft savedDraft = captor.getValue();
        assertEquals("\"A\"", savedDraft.getAnswerJson());
        when(draftMapper.selectList(any())).thenReturn(List.of(savedDraft));

        SelfTestRunVO view = service.view(user, 41L);

        assertEquals("A", view.getQuestions().get(0).getDraftAnswer());
        assertEquals(Boolean.TRUE, view.getQuestions().get(0).getMarkedUnsure());
    }

    @Test
    void staleDraftVersionReturnsServerDraftsWithoutOverwrite() {
        LearningAssessmentDraft serverDraft = draft(101L, "\"B\"", 0);
        when(draftMapper.selectList(any())).thenReturn(List.of(serverDraft));

        SelfTestDraftSaveVO result = service.saveDrafts(user, 41L,
                saveRequest(2, "save-stale", 101L, "A", false));

        assertTrue(result.getConflict());
        assertEquals(3, result.getRowVersion());
        assertEquals(1, result.getServerDrafts().size());
        assertEquals("B", result.getServerDrafts().get(0).getAnswer());
        verify(draftMapper, never()).insert(any());
        verify(draftMapper, never()).updateById(any());
        verify(runMapper, never()).bumpDraftVersion(anyLong(), any(), any());
    }

    @Test
    void repeatedClientSaveIdIsIdempotent() {
        run.setLastSaveId("save-1");
        run.setLastSaveAt(new Date(1000L));

        SelfTestDraftSaveVO result = service.saveDrafts(user, 41L,
                saveRequest(3, "save-1", 101L, "A", false));

        assertFalse(result.getConflict());
        assertEquals(3, result.getRowVersion());
        assertEquals(1000L, result.getSavedAt());
        verifyNoInteractions(draftMapper);
        verify(runMapper, never()).bumpDraftVersion(anyLong(), any(), any());
    }

    @Test
    void finishRequiresLatestDraftVersion() {
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.finish(user, 41L, "submit-1", 2)).getCode());
        verifyNoInteractions(attemptMapper, eventService);
        verify(runMapper, never()).updateById(any());
    }

    @Test
    void sameSubmissionKeyReturnsStoredResultWithoutNewEvents() throws Exception {
        run.setStatus("FINISHED");
        run.setSubmissionKey("submit-1");
        SelfTestResultVO stored = new SelfTestResultVO();
        stored.setRunId("41");
        stored.setStatus("FINISHED");
        stored.setCorrectCount(1);
        run.setFinishResultJson(JSON.writeValueAsString(stored));

        SelfTestResultVO result = service.finish(user, 41L, "submit-1", 3);

        assertEquals(1, result.getCorrectCount());
        verifyNoInteractions(attemptMapper, eventService, questionMapper);
        verify(runMapper, never()).updateById(any());
    }

    @Test
    void sameRunDifferentSubmissionKeyIsRejected() {
        run.setStatus("FINISHED");
        run.setSubmissionKey("submit-1");
        run.setFinishResultJson("{}");

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.finish(user, 41L, "submit-2", 3)).getCode());
        verifyNoInteractions(attemptMapper, eventService);
    }

    @Test
    void finishGradesAnsweredObjectiveQuestionsAndSkipsUnanswered() {
        when(draftMapper.selectList(any())).thenReturn(List.of(draft(101L, "\"A\"", 0)));
        // 101 标准答案 A → 正确；102 无草稿 → 未作答
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(
                question(101L, "SINGLE"), question(102L, "SINGLE")));
        ArgumentCaptor<LearningAttempt> attempt = ArgumentCaptor.forClass(LearningAttempt.class);

        SelfTestResultVO result = service.finish(user, 41L, "submit-1", 3);

        verify(attemptMapper, times(1)).insert(attempt.capture());
        assertEquals("selftest:41:101", attempt.getValue().getSubmissionKey());
        verify(eventService, times(1)).recordAttemptEvents(eq(7L), any(), anyLong(), eq(true), eq(true),
                any(EvidenceQualification.class));
        assertEquals(2, result.getTotalQuestions());
        assertEquals(1, result.getAnsweredQuestions());
        assertEquals(1, result.getObjectiveQuestions());
        assertEquals(1, result.getCorrectCount());
        assertEquals(1, result.getUnansweredCount());
        assertEquals("FINISHED", run.getStatus());
        assertNotNull(run.getFinishResultJson());
    }

    @Test
    void comprehensiveDraftIsPendingSelfAssessmentWithoutAttemptOrEvent() {
        LearningQuestion comprehensive = question(101L, "COMPREHENSIVE");
        comprehensive.setAnswer(null);
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(comprehensive));
        run.setQuestionIdsJson("[101]");
        when(draftMapper.selectList(any())).thenReturn(List.of(draft(101L, "\"我的思路\"", 0)));

        SelfTestResultVO result = service.finish(user, 41L, "submit-1", 3);

        assertEquals(SelfTestResultVO.STATUS_PENDING_SELF_ASSESSMENT,
                result.getQuestions().get(0).getStatus());
        assertEquals(0, result.getObjectiveQuestions());
        assertEquals(1, result.getPendingSelfAssessmentCount());
        assertNull(result.getQuestions().get(0).getCorrect());
        verifyNoInteractions(attemptMapper, eventService);
    }

    @Test
    void offlineAndVersionChangedQuestionsAreInvalidAndExcludedFromDenominator() {
        LearningQuestion offline = question(101L, "SINGLE");
        offline.setReviewStatus("OFFLINE");
        LearningQuestion changed = question(102L, "SINGLE");
        changed.setRevision(9);
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(offline, changed));

        SelfTestResultVO result = service.finish(user, 41L, "submit-1", 3);

        assertEquals(2, result.getInvalidCount());
        assertEquals(0, result.getValidQuestions());
        assertEquals(SelfTestResultVO.STATUS_INVALID_OFFLINE,
                result.getQuestions().get(0).getStatus());
        assertEquals(SelfTestResultVO.STATUS_INVALID_VERSION,
                result.getQuestions().get(1).getStatus());
        verifyNoInteractions(attemptMapper, eventService);
    }

    @Test
    void resultRequiresFinishedRun() {
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class,
                        () -> service.result(user, 41L)).getCode());
    }

    private SelfTestDraftSaveRequest saveRequest(int version, String saveId, Long questionId,
                                                 String answer, boolean marked) {
        SelfTestDraftSaveRequest request = new SelfTestDraftSaveRequest();
        request.setRowVersion(version);
        request.setClientSaveId(saveId);
        SelfTestDraftSaveRequest.Draft draft = new SelfTestDraftSaveRequest.Draft();
        draft.setQuestionId(questionId);
        draft.setAnswer(answer);
        draft.setMarkedUnsure(marked);
        request.setDrafts(List.of(draft));
        return request;
    }

    private LearningAssessmentDraft draft(Long questionId, String answerJson, int marked) {
        LearningAssessmentDraft draft = new LearningAssessmentDraft();
        draft.setId(questionId + 1000);
        draft.setRunId(41L);
        draft.setUserId(7L);
        draft.setQuestionId(questionId);
        draft.setAnswerJson(answerJson);
        draft.setMarkedUnsure(marked);
        draft.setQuestionRevision(1);
        draft.setIsDelete(0);
        return draft;
    }

    private LearningQuestion question(Long id, String type) {
        LearningQuestion question = new LearningQuestion();
        question.setId(id);
        question.setType(type);
        question.setTitle("题目" + id);
        question.setContent("题干");
        question.setAnswer("\"A\"");
        question.setReviewStatus("PUBLISHED");
        question.setResourceScope("SHARED");
        question.setRevision(1);
        question.setIsDelete(0);
        question.setQuestionCode("q." + id);
        return question;
    }
}
