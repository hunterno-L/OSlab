package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningQuestionAssessmentTest {
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningAttemptMapper attemptMapper = mock(LearningAttemptMapper.class);
    private final LearningAssessmentRunMapper runMapper = mock(LearningAssessmentRunMapper.class);
    private final LearningEventService eventService = mock(LearningEventService.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final UserService userService = mock(UserService.class);
    private final com.yupi.springbootinit.ai.evidence.LearningEvidenceContextService evidenceContexts =
            mock(com.yupi.springbootinit.ai.evidence.LearningEvidenceContextService.class);
    private final LearningQuestionServiceImpl service = new LearningQuestionServiceImpl();
    private final User user = new User();
    private LearningAssessmentRun run;
    private LearningQuestion question;
    private LearningAnswerRequest request;

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "assessment-answer-test");
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        user.setId(7L);
        question = new LearningQuestion();
        question.setId(101L);
        question.setUserId(9L);
        question.setIsDelete(0);
        question.setReviewStatus("PUBLISHED");
        question.setType("JUDGE");
        question.setAnswer("\"true\"");
        run = new LearningAssessmentRun();
        run.setId(41L);
        run.setUserId(7L);
        run.setStatus("ACTIVE");
        run.setQuestionIdsJson("[101]");
        request = new LearningAnswerRequest();
        request.setQuestionId(101L);
        request.setAssessmentRunId(41L);
        request.setAnswer("true");
        when(questionMapper.selectById(101L)).thenReturn(question);
        when(runMapper.selectById(41L)).thenReturn(run);
        when(attemptMapper.selectCount(any())).thenReturn(1L);
        when(attemptMapper.insert(any())).thenAnswer(invocation -> {
            LearningAttempt attempt = invocation.getArgument(0);
            attempt.setId(88L);
            return 1;
        });
        when(eventService.recordAttemptEvents(anyLong(), any(), anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of());
        when(eventService.recordAttemptEvents(anyLong(), any(), anyLong(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);
        ReflectionTestUtils.setField(service, "learningAttemptMapper", attemptMapper);
        ReflectionTestUtils.setField(service, "learningAssessmentRunMapper", runMapper);
        ReflectionTestUtils.setField(service, "learningEventService", eventService);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "evidenceContexts", evidenceContexts);
    }

    @Test
    void correctFilterRetainsBatchFirstAndLatestResultsWithoutPerQuestionCountQueries() {
        LearningQuestionQueryRequest query = new LearningQuestionQueryRequest();
        query.setStudyState("CORRECT");
        query.setCurrent(1);
        query.setPageSize(10);
        LearningAttempt first = existingAttempt(101L, 40L);
        first.setId(1L);
        first.setIsCorrect(0);
        LearningAttempt latest = existingAttempt(101L, 41L);
        latest.setId(2L);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of(first, latest));
        when(nodeMapper.selectList(any())).thenReturn(List.of());

        var result = service.pageVO(query, user);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        var record = result.getRecords().get(0);
        assertTrue(record.getHasAttempted());
        assertEquals(2, record.getAttemptCount());
        assertEquals("WRONG", record.getFirstResult());
        assertEquals("CORRECT", record.getLatestResult());
        verify(attemptMapper, times(1)).selectList(any());
        verify(attemptMapper, never()).selectCount(any());
    }

    @Test
    void validRunStoresSessionButKeepsLifetimeFirstRuleUnchanged() {
        LearningAnswerResultVO result = service.answerQuestion(request, user);
        assertTrue(result.getCorrect());
        assertFalse(result.getIsFirst());
        ArgumentCaptor<LearningAttempt> attempt = ArgumentCaptor.forClass(LearningAttempt.class);
        verify(attemptMapper).insert(attempt.capture());
        assertEquals(41L, attempt.getValue().getAssessmentRunId());
        assertEquals(0, attempt.getValue().getIsFirst());
        verify(eventService).recordAttemptEvents(eq(7L), eq(question), eq(88L), eq(true), eq(false),
                argThat(qualifiesAsUnknownHelp()));
        // 首次判定按"评估身份 + 历史遗留（无评估身份）"两次计数，二者都是学生终身口径：
        // 断言的是"任何一次计数都不得按测评会话过滤"，而不是调用次数。
        ArgumentCaptor<LambdaQueryWrapper<LearningAttempt>> lifetime = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attemptMapper, atLeastOnce()).selectCount(lifetime.capture());
        assertEquals(2, lifetime.getAllValues().size(), "评估身份计数 + 遗留计数");
        boolean sawIdentityCount = false;
        boolean sawLegacyCount = false;
        for (LambdaQueryWrapper<LearningAttempt> wrapper : lifetime.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            assertFalse(sql.contains("assessmentRunId") || sql.contains("assessment_run_id"),
                    "首次判定必须按学生终身口径，不得按测评会话过滤");
            if (sql.contains("evaluationIdentity")) {
                sawIdentityCount = true;
            }
            if (sql.contains("questionId")) {
                sawLegacyCount = true;
            }
        }
        assertTrue(sawIdentityCount, "应按跨副本评估身份计数");
        assertTrue(sawLegacyCount, "应兼容无评估身份的历史作答");
    }

    @Test
    void standalonePracticeStillWorksWithoutSession() {
        request.setAssessmentRunId(null);
        assertTrue(service.answerQuestion(request, user).getCorrect());
        verifyNoInteractions(runMapper);
        ArgumentCaptor<LearningAttempt> attempt = ArgumentCaptor.forClass(LearningAttempt.class);
        verify(attemptMapper).insert(attempt.capture());
        assertNull(attempt.getValue().getAssessmentRunId());
    }

    @Test
    void comprehensivePracticeRevealsAnalysisWithoutGradingOrRecordingEvidence() {
        question.setType("COMPREHENSIVE");
        question.setAnalysis("参考答案与解析");
        request.setAssessmentRunId(null);
        request.setAnswer("我的推导");
        LearningAnswerResultVO result = service.answerQuestion(request, user);
        assertNull(result.getCorrect());
        assertNull(result.getAttemptId());
        assertEquals("参考答案与解析", result.getAnalysis());
        verify(attemptMapper, never()).insert(any());
        verifyNoInteractions(eventService, runMapper);
    }

    @Test
    void comprehensivePracticeRequiresAnAnswerAndIsExcludedFromScoredSessions() {
        question.setType("COMPREHENSIVE");
        request.setAssessmentRunId(null);
        request.setAnswer(" ");
        assertThrows(BusinessException.class, () -> service.answerQuestion(request, user));
        request.setAnswer("我的推导");
        request.setAssessmentRunId(41L);
        assertThrows(BusinessException.class, () -> service.answerQuestion(request, user));
        verify(attemptMapper, never()).insert(any());
        verifyNoInteractions(eventService, runMapper);
    }

    @Test
    void rejectsMissingSessionBeforeRecordingAnything() {
        when(runMapper.selectById(41L)).thenReturn(null);
        assertRejected(ErrorCode.NOT_FOUND_ERROR);
    }

    @Test
    void rejectsOtherUsersSessionBeforeRecordingAnything() {
        run.setUserId(8L);
        assertRejected(ErrorCode.NO_AUTH_ERROR);
    }

    @Test
    void rejectsFinishedSessionBeforeRecordingAnything() {
        run.setStatus("FINISHED");
        assertRejected(ErrorCode.OPERATION_ERROR);
    }

    @Test
    void rejectsQuestionOutsideFixedQueueBeforeRecordingAnything() {
        run.setQuestionIdsJson("[102]");
        assertRejected(ErrorCode.PARAMS_ERROR);
    }

    @Test
    void rejectsOfflineQuestionEvenWhenSessionContainsIt() {
        question.setReviewStatus("OFFLINE");
        assertRejected(ErrorCode.OPERATION_ERROR);
        verifyNoInteractions(runMapper);
    }

    @Test
    void sameSubmissionKeyInSameSessionReusesAttemptWithoutNewEvents() {
        request.setSubmissionKey("retry-key");
        when(attemptMapper.selectOne(any())).thenReturn(existingAttempt(101L, 41L));
        LearningAnswerResultVO result = service.answerQuestion(request, user);
        assertEquals(55L, result.getAttemptId());
        assertTrue(result.getCorrect());
        verify(attemptMapper, never()).insert(any());
        verify(attemptMapper, never()).selectCount(any());
        verifyNoInteractions(eventService);
    }

    @Test
    void retryAfterQuestionRevisionUsesImmutableOriginalFeedback() {
        request.setSubmissionKey("retry-key");
        LearningAttempt prior=existingAttempt(101L,41L);
        question.setAnalysis("original explanation");
        question.setRevision(2);
        prior.setQuestionSnapshot(cn.hutool.json.JSONUtil.toJsonStr(question));
        prior.setQuestionRevision(2);
        question.setAnalysis("new explanation");
        question.setAnswer("\"false\"");
        question.setRevision(3);
        when(attemptMapper.selectOne(any())).thenReturn(prior);
        LearningAnswerResultVO result=service.answerQuestion(request,user);
        assertTrue(result.getCorrect());
        assertEquals("original explanation",result.getAnalysis());
        verify(attemptMapper,never()).insert(any());
        verifyNoInteractions(eventService);
    }

    @Test
    void sameKeyCannotAttachIndependentAttemptToCourseActivity() {
        request.setSubmissionKey("retry-key");
        request.setSourceUnitCode("unit.test");
        request.setSourceActivityCode("act.test");
        when(attemptMapper.selectOne(any())).thenReturn(existingAttempt(101L,41L));
        assertThrows(BusinessException.class,()->service.answerQuestion(request,user));
        verify(attemptMapper,never()).insert(any());
    }

    @Test
    void submissionKeyCannotReuseAnotherSessionsAttempt() {
        request.setSubmissionKey("retry-key");
        when(attemptMapper.selectOne(any())).thenReturn(existingAttempt(101L, 40L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.answerQuestion(request, user)).getCode());
        verify(attemptMapper, never()).insert(any());
        verify(attemptMapper, never()).selectCount(any());
        verifyNoInteractions(eventService);
    }

    @Test
    void submissionKeyCannotReuseAnotherQuestionsAttempt() {
        request.setSubmissionKey("retry-key");
        when(attemptMapper.selectOne(any())).thenReturn(existingAttempt(102L, 41L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.answerQuestion(request, user)).getCode());
        verify(attemptMapper, never()).insert(any());
        verifyNoInteractions(eventService);
    }

    // region AI 升级 §5.5：帮助等级在答题事务前生效并参与证据资格

    @Test
    void legacyEntryWithoutEvidenceContextKeepsUnknownHelpAndExistingObservation() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        LearningAnswerResultVO result = service.answerQuestion(request, user);
        assertTrue(result.getIsFirst(), "终身首次判定不变");
        verify(evidenceContexts, never()).lockAndQualify(anyLong(), any());
        verify(eventService).recordAttemptEvents(eq(7L), eq(question), eq(88L), eq(true), eq(true),
                argThat(qualifiesAsUnknownHelp()));
    }

    @Test
    void hintedFirstAttemptIsNotCountedAsIndependentObservation() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(evidenceContexts.lockAndQualify(7L, 555L)).thenReturn(
                EvidenceQualification.of(555L, EvidenceQualification.HELP_HINT, null));
        LearningAnswerResultVO result = service.answerQuestion(request, user, 555L);
        assertTrue(result.getIsFirst(), "响应仍如实报告这是终身首次作答");
        verify(eventService).recordAttemptEvents(eq(7L), eq(question), eq(88L), eq(true), eq(false),
                argThat(q -> q != null && EvidenceQualification.HELP_HINT.equals(q.getHelpLevel())
                        && !q.isEligibleForObservation()));
    }

    @Test
    void revealedAnswerFirstAttemptIsNotCountedAsIndependentObservation() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(evidenceContexts.lockAndQualify(7L, 555L)).thenReturn(
                EvidenceQualification.of(555L, EvidenceQualification.HELP_ANSWER_REVEALED, "EXTERNAL_AI"));
        service.answerQuestion(request, user, 555L);
        verify(eventService).recordAttemptEvents(eq(7L), eq(question), eq(88L), eq(true), eq(false),
                argThat(q -> q != null
                        && EvidenceQualification.HELP_ANSWER_REVEALED.equals(q.getHelpLevel())
                        && "EXTERNAL_AI".equals(q.getSelfReportedHelp())
                        && !q.isEligibleForObservation()));
    }

    @Test
    void unhelpedContextStillCountsFirstObservation() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(evidenceContexts.lockAndQualify(7L, 555L)).thenReturn(
                EvidenceQualification.of(555L, EvidenceQualification.HELP_NONE, null));
        service.answerQuestion(request, user, 555L);
        verify(eventService).recordAttemptEvents(eq(7L), eq(question), eq(88L), eq(true), eq(true),
                argThat(q -> q != null && q.isEligibleForObservation()
                        && EvidenceQualification.HELP_NONE.equals(q.getHelpLevel())));
    }

    @Test
    void evidenceContextIsLockedBeforeTheAttemptIsWritten() {
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(evidenceContexts.lockAndQualify(7L, 555L)).thenReturn(
                EvidenceQualification.of(555L, EvidenceQualification.HELP_NONE, null));
        service.answerQuestion(request, user, 555L);
        // 帮助等级必须在写 attempt / 更新掌握度之前读取并生效
        InOrder inOrder = inOrder(evidenceContexts, attemptMapper, eventService);
        inOrder.verify(evidenceContexts).lockAndQualify(7L, 555L);
        inOrder.verify(attemptMapper).insert(any(LearningAttempt.class));
        inOrder.verify(eventService).recordAttemptEvents(anyLong(), any(), anyLong(), anyBoolean(),
                anyBoolean(), any());
    }

    @Test
    void foreignEvidenceContextIsRejectedBeforeAnythingIsWritten() {
        when(evidenceContexts.lockAndQualify(7L, 555L)).thenThrow(
                new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该证据上下文"));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.answerQuestion(request, user, 555L)).getCode());
        verify(attemptMapper, never()).insert(any());
        verifyNoInteractions(eventService);
    }

    @Test
    void replayedSubmissionKeyDoesNotCreateNewEvidenceContextOrObservation() {
        request.setSubmissionKey("retry-key");
        when(attemptMapper.selectOne(any())).thenReturn(existingAttempt(101L, 41L));
        LearningAnswerResultVO result = service.answerQuestion(request, user, 555L);
        assertEquals(55L, result.getAttemptId());
        // 重复提交既不新建证据上下文，也不产生新的首次观测
        verifyNoInteractions(evidenceContexts);
        verify(attemptMapper, never()).insert(any());
        verifyNoInteractions(eventService);
    }

    private static org.mockito.ArgumentMatcher<EvidenceQualification> qualifiesAsUnknownHelp() {
        return qualification -> qualification != null
                && EvidenceQualification.HELP_UNKNOWN.equals(qualification.getHelpLevel())
                && qualification.isEligibleForObservation()
                && qualification.getContextId() == null;
    }

    // endregion

    private void assertRejected(ErrorCode error) {
        assertEquals(error.getCode(),
                assertThrows(BusinessException.class, () -> service.answerQuestion(request, user)).getCode());
        verifyNoInteractions(attemptMapper, eventService);
    }

    private LearningAttempt existingAttempt(Long questionId, Long runId) {
        LearningAttempt attempt = new LearningAttempt();
        attempt.setId(55L);
        attempt.setUserId(7L);
        attempt.setQuestionId(questionId);
        attempt.setAssessmentRunId(runId);
        attempt.setIsCorrect(1);
        attempt.setIsFirst(0);
        return attempt;
    }
}
