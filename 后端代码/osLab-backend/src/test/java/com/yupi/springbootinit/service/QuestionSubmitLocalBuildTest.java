package com.yupi.springbootinit.service;

import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.judge.JudgeService;
import com.yupi.springbootinit.learning.ExperimentJudgeModeService;
import com.yupi.springbootinit.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.impl.QuestionSubmitServiceImpl;
import com.yupi.springbootinit.task.TaskExecutionContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuestionSubmitLocalBuildTest {
    @Test void automaticTaskSourceContinuesToSaveAndJudge() {
        QuestionSubmitServiceImpl service = spy(new QuestionSubmitServiceImpl());
        QuestionService questions = mock(QuestionService.class);
        ExperimentJudgeModeService modes = mock(ExperimentJudgeModeService.class);
        TaskExecutionContextService tasks = mock(TaskExecutionContextService.class);
        JudgeService judge = mock(JudgeService.class);
        ReflectionTestUtils.setField(service, "questionService", questions);
        ReflectionTestUtils.setField(service, "experimentJudgeModes", modes);
        ReflectionTestUtils.setField(service, "taskContext", tasks);
        ReflectionTestUtils.setField(service, "judgeService", judge);
        Question question = new Question(); question.setId(42L); question.setResourceScope("TASK");
        when(questions.getById(42L)).thenReturn(question);
        TaskExecutionContextService.Context context = new TaskExecutionContextService.Context();
        context.snapshot = new ObjectMapper().createObjectNode().put("manualReview", false);
        context.sourceQuestionId = 1930155905954140162L;
        when(tasks.validate(any(), eq("task"), eq("item"), eq(42L), eq(1), eq(true))).thenReturn(context);
        doAnswer(invocation -> { QuestionSubmit saved = invocation.getArgument(0); saved.setId(123L); return true; })
                .when(service).save(any());
        when(questions.update(any())).thenReturn(true);
        QuestionSubmitAddRequest request = new QuestionSubmitAddRequest();
        request.setLanguage("c"); request.setQuestionId(42L); request.setCode("int main(void) { return 0; }");
        request.setSourceTaskCode("task"); request.setSourceTaskItemCode("item"); request.setSourceTaskRound(1);
        User user = new User(); user.setId(1L);
        org.junit.jupiter.api.Assertions.assertEquals(123L, service.doQuestionSubmit(request, user));
        verify(modes).requireAutomaticJudge(context.sourceQuestionId);
        verify(judge, timeout(2000)).doJudge(123L);
    }

    @Test void legacyTaskCopyWithLocalSourceCannotPersistOrReachJudge() {
        QuestionSubmitServiceImpl service = spy(new QuestionSubmitServiceImpl());
        QuestionService questions = mock(QuestionService.class);
        ExperimentJudgeModeService modes = mock(ExperimentJudgeModeService.class);
        TaskExecutionContextService tasks = mock(TaskExecutionContextService.class);
        JudgeService judge = mock(JudgeService.class);
        ReflectionTestUtils.setField(service, "questionService", questions);
        ReflectionTestUtils.setField(service, "experimentJudgeModes", modes);
        ReflectionTestUtils.setField(service, "taskContext", tasks);
        ReflectionTestUtils.setField(service, "judgeService", judge);
        Question question = new Question(); question.setId(42L); question.setResourceScope("TASK");
        when(questions.getById(42L)).thenReturn(question);
        TaskExecutionContextService.Context context = new TaskExecutionContextService.Context();
        context.snapshot = new ObjectMapper().createObjectNode().put("manualReview", false);
        context.sourceQuestionId = 1930155905954140162L;
        when(tasks.validate(any(), eq("task"), eq("item"), eq(42L), eq(1), eq(true))).thenReturn(context);
        doThrow(new BusinessException(com.yupi.springbootinit.common.ErrorCode.PARAMS_ERROR))
                .when(modes).requireAutomaticJudge(context.sourceQuestionId);
        QuestionSubmitAddRequest request = new QuestionSubmitAddRequest();
        request.setLanguage("c"); request.setQuestionId(42L); request.setCode("int main(void) { return 0; }");
        request.setSourceTaskCode("task"); request.setSourceTaskItemCode("item"); request.setSourceTaskRound(1);
        User user = new User(); user.setId(1L);
        assertThrows(BusinessException.class, () -> service.doQuestionSubmit(request, user));
        verify(service, never()).save(any());
        verifyNoInteractions(judge);
        verify(modes).requireAutomaticJudge(context.sourceQuestionId);
    }

    @Test void manualBuildCannotCreateASubmissionOrReachTheJudge() {
        QuestionSubmitServiceImpl service = new QuestionSubmitServiceImpl();
        QuestionService questions = mock(QuestionService.class);
        ExperimentJudgeModeService modes = mock(ExperimentJudgeModeService.class);
        JudgeService judge = mock(JudgeService.class);
        ReflectionTestUtils.setField(service, "questionService", questions);
        ReflectionTestUtils.setField(service, "experimentJudgeModes", modes);
        ReflectionTestUtils.setField(service, "judgeService", judge);
        Question question = new Question();
        question.setId(42L);
        when(questions.getById(42L)).thenReturn(question);
        doThrow(new BusinessException(com.yupi.springbootinit.common.ErrorCode.PARAMS_ERROR))
                .when(modes).requireAutomaticJudge(42L);
        QuestionSubmitAddRequest request = new QuestionSubmitAddRequest();
        request.setLanguage("c");
        request.setQuestionId(42L);
        request.setCode("int main(void) { return 0; }");
        User user = new User();
        user.setId(1L);
        assertThrows(BusinessException.class, () -> service.doQuestionSubmit(request, user));
        verifyNoInteractions(judge);
        verify(questions, only()).getById(42L);
    }
}
