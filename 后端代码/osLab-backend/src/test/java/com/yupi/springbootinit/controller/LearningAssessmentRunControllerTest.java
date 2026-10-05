package com.yupi.springbootinit.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearningAssessmentRunControllerTest {
    private final LearningAssessmentRunMapper runMapper = mock(LearningAssessmentRunMapper.class);
    private final LearningAttemptMapper attemptMapper = mock(LearningAttemptMapper.class);
    private final LearningQuestionService questionService = mock(LearningQuestionService.class);
    private final UserService userService = mock(UserService.class);
    private final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    private final LearningAssessmentRunController controller = new LearningAssessmentRunController();
    private final User user = new User();
    private LearningAssessmentRun run;

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "assessment-run-test");
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);
        user.setId(7L);
        run = new LearningAssessmentRun();
        run.setId(41L);
        run.setUserId(7L);
        run.setStatus("ACTIVE");
        run.setQuestionIdsJson("[101]");
        when(userService.getLoginUser(servletRequest)).thenReturn(user);
        when(runMapper.selectById(41L)).thenReturn(run);
        when(runMapper.updateById(any())).thenReturn(1);
        ReflectionTestUtils.setField(controller, "assessmentRunMapper", runMapper);
        ReflectionTestUtils.setField(controller, "learningAttemptMapper", attemptMapper);
        ReflectionTestUtils.setField(controller, "learningQuestionService", questionService);
        ReflectionTestUtils.setField(controller, "userService", userService);
    }

    @Test
    void newRunDoesNotReuseLifetimeAnswerState() {
        LearningQuestionVO lifetime = questionVO(101L);
        lifetime.setHasAttempted(true);
        lifetime.setAttemptCount(9);
        lifetime.setFirstResult("CORRECT");
        lifetime.setLatestResult("CORRECT");
        when(questionService.getQuestionVOs(List.of(101L), user)).thenReturn(List.of(lifetime));
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        LearningAssessmentRunController.RunQuestionVO question = (LearningAssessmentRunController.RunQuestionVO)
                controller.run(41L, servletRequest).getData().getQuestions().get(0);

        assertFalse(question.getHasAttempted());
        assertEquals(0, question.getAttemptCount());
        assertNull(question.getFirstResult());
        assertNull(question.getLatestResult());
        assertNull(question.getLatestCorrect());
        assertNull(question.getLatestAttemptTime());
        assertNull(question.getAnalysis());
        assertNull(question.getMisconception());
        verifyScopedAttemptQuery();
        verify(questionService).getQuestionVOs(List.of(101L), user);
        verify(questionService, never()).getQuestionVO(anyLong(), any());
        verify(attemptMapper, never()).selectCount(any());
    }

    @Test
    void runSeparatesFirstResultFromLatestRetryInThisRun() {
        when(questionService.getQuestionVOs(List.of(101L), user)).thenReturn(List.of(questionVO(101L)));
        LearningAttempt first = attempt(1L, 101L, 0);
        LearningAttempt retry = attempt(2L, 101L, 1);
        when(attemptMapper.selectList(any())).thenReturn(List.of(first, retry));

        LearningAssessmentRunController.RunQuestionVO question = (LearningAssessmentRunController.RunQuestionVO)
                controller.run(41L, servletRequest).getData().getQuestions().get(0);

        assertTrue(question.getHasAttempted());
        assertEquals(2, question.getAttemptCount());
        assertEquals("WRONG", question.getFirstResult());
        assertEquals("CORRECT", question.getLatestResult());
        assertTrue(question.getLatestCorrect());
        assertEquals(retry.getCreateTime(), question.getLatestAttemptTime());
        assertEquals("analysis", question.getAnalysis());
        verifyScopedAttemptQuery();
    }

    @Test
    void unavailableQuestionRetainsDisabledPlaceholderWithoutHistory() {
        when(questionService.getQuestionVOs(List.of(101L), user)).thenReturn(List.of());

        LearningAssessmentRunController.RunQuestionVO question = (LearningAssessmentRunController.RunQuestionVO)
                controller.run(41L, servletRequest).getData().getQuestions().get(0);

        assertEquals(101L, question.getId());
        assertTrue(question.getOffline());
        assertEquals("OFFLINE", question.getReviewStatus());
        assertFalse(question.getHasAttempted());
        assertEquals(0, question.getAttemptCount());
        verifyNoInteractions(attemptMapper);
    }

    @Test
    void finishIgnoresClientCountsAndCountsUniqueFirstResultsOnly() {
        run.setQuestionIdsJson("[101,101,102,103]");
        when(questionService.list(any())).thenReturn(List.of(publishedQuestion(101L), publishedQuestion(102L)));
        when(attemptMapper.selectList(any())).thenReturn(List.of(
                attempt(1L, 101L, 0), attempt(2L, 101L, 1),
                attempt(3L, 102L, 1), attempt(4L, 102L, 0)));
        LearningAssessmentRunController.FinishRequest request = finishRequest();
        request.setAnsweredCount(999);
        request.setFirstCorrect(999);

        LearningAssessmentRunController.FinishResult result = controller.finish(request, servletRequest).getData();

        assertEquals(2, result.getTotalQuestions());
        assertEquals(2, result.getAnsweredQuestions());
        assertEquals(1, result.getFirstCorrect());
        assertEquals("FINISHED", run.getStatus());
        assertNotNull(run.getFinishTime());
        verify(runMapper).updateById(run);
        verifyScopedAttemptQuery();
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestion>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionService).list(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("PUBLISHED"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void finishWithNoRunAttemptsCannotClaimLifetimeOrClientResults() {
        when(questionService.list(any())).thenReturn(List.of(publishedQuestion(101L)));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        LearningAssessmentRunController.FinishRequest request = finishRequest();
        request.setAnsweredCount(1);
        request.setFirstCorrect(1);

        LearningAssessmentRunController.FinishResult result = controller.finish(request, servletRequest).getData();

        assertEquals(1, result.getTotalQuestions());
        assertEquals(0, result.getAnsweredQuestions());
        assertEquals(0, result.getFirstCorrect());
        verifyScopedAttemptQuery();
    }

    @Test
    void runAndFinishRejectAnotherUsersSessionBeforeReadingAttempts() {
        run.setUserId(8L);
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.run(41L, servletRequest)).getCode());
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.finish(finishRequest(), servletRequest)).getCode());
        verifyNoInteractions(attemptMapper, questionService);
        verify(runMapper, never()).updateById(any());
    }

    @Test
    void finishedRunCannotBeFinishedAgain() {
        run.setStatus("FINISHED");
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> controller.finish(finishRequest(), servletRequest)).getCode());
        verifyNoInteractions(attemptMapper, questionService);
        verify(runMapper, never()).updateById(any());
    }

    private void verifyScopedAttemptQuery() {
        ArgumentCaptor<LambdaQueryWrapper<LearningAttempt>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attemptMapper, times(1)).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("assessmentRunId") || sql.contains("assessment_run_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(41L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(101L));
        assertTrue(sql.contains("ORDER BY"));
        verify(attemptMapper, never()).selectCount(any());
    }

    private LearningQuestionVO questionVO(Long id) {
        LearningQuestionVO question = new LearningQuestionVO();
        question.setId(id);
        question.setType("JUDGE");
        question.setReviewStatus("PUBLISHED");
        question.setAnalysis("analysis");
        question.setMisconception("misconception");
        return question;
    }

    private LearningQuestion publishedQuestion(Long id) {
        LearningQuestion question = new LearningQuestion();
        question.setId(id);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);
        return question;
    }

    private LearningAttempt attempt(Long id, Long questionId, int correct) {
        LearningAttempt attempt = new LearningAttempt();
        attempt.setId(id);
        attempt.setUserId(7L);
        attempt.setAssessmentRunId(41L);
        attempt.setQuestionId(questionId);
        attempt.setIsCorrect(correct);
        attempt.setIsFirst(0);
        attempt.setCreateTime(new Date(id * 1000));
        return attempt;
    }

    private LearningAssessmentRunController.FinishRequest finishRequest() {
        LearningAssessmentRunController.FinishRequest request = new LearningAssessmentRunController.FinishRequest();
        request.setRunId(41L);
        return request;
    }
}
