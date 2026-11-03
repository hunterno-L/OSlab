package com.yupi.springbootinit.ai.plan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.ai.diagnosis.DiagnosisService;
import com.yupi.springbootinit.ai.session.AiSessionService;
import com.yupi.springbootinit.model.entity.LearningReviewItem;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.TargetRef;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 区分任务落成动作的回归（题库型 / 预测型两种形态）。
 *
 * <p>关键约束：题库型任务必须先解析成"已发布、同课程、学生可见"的题目才能生成动作；
 * 解析不到（草稿、下线、别的课程、写错 code）时宁可不生成动作，也不引用不可作答的题目。
 * 预测型任务保持原有页面置换案例行为。
 */
class VerificationActionTest {

    private final AiContentScopeService contentScope = mock(AiContentScopeService.class);
    private final PredictCaseCatalog predictCases = new PredictCaseCatalog();
    private final NextActionPlanner planner = new NextActionPlanner();

    private final User user = new User();

    @BeforeEach
    void setUp() {
        predictCases.init();
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "verification-action-test");
        TableInfoHelper.initTableInfo(assistant, LearningReviewItem.class);

        user.setId(7L);
        ReflectionTestUtils.setField(planner, "contentScope", contentScope);
        ReflectionTestUtils.setField(planner, "predictCases", predictCases);
        when(contentScope.publishedUnits(anyString())).thenReturn(Collections.emptyList());
        when(contentScope.publishedQuestionIds(any(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void publishedQuestionCodeBecomesAQuestionActionWithTheDatabaseId() {
        when(contentScope.eligibleQuestionIdByCode(eq(user), eq("course.database-systems"),
                eq("q.db.fd.closure-composite"))).thenReturn(2117582691588841476L);

        List<NextAction> actions = planner.plan(input(questionTask("q.db.fd.closure-composite")));

        NextAction action = actions.stream()
                .filter(item -> NextAction.TYPE_QUESTION.equals(item.getType()))
                .findFirst().orElse(null);
        assertNotNull(action, "可解析的题库型验证必须生成题目动作");
        assertEquals(TargetRef.KIND_QUESTION, action.getTargetRef().getKind());
        assertEquals("2117582691588841476", action.getTargetRef().getCode());
        assertTrue(action.getReason().contains("已发布、同课程"));
        assertTrue(action.getEstimatedMinutes() > 0);
    }

    @Test
    void unresolvableQuestionCodeProducesNoActionAtAll() {
        when(contentScope.eligibleQuestionIdByCode(any(), anyString(), anyString())).thenReturn(null);

        List<NextAction> actions = planner.plan(input(questionTask("q.db.fd.not-published")));

        assertTrue(actions.stream().noneMatch(item -> NextAction.TYPE_QUESTION.equals(item.getType())),
                "草稿/下线/越权/写错的 code 一律不得生成题目动作");
        verify(contentScope).eligibleQuestionIdByCode(user, "course.database-systems",
                "q.db.fd.not-published");
    }

    @Test
    void predictVerificationStillUsesTheCalculatorCase() {
        List<NextAction> actions = planner.plan(input(predictTask("page.lru-vs-fifo.victim")));

        NextAction action = actions.stream()
                .filter(item -> NextAction.TYPE_PREDICT.equals(item.getType()))
                .findFirst().orElse(null);
        assertNotNull(action);
        assertEquals(TargetRef.KIND_SIMULATION, action.getTargetRef().getKind());
        assertEquals("page.lru-vs-fifo.victim", action.getTargetRef().getCode());
        assertTrue(action.getReason().contains("页面置换计算器"));
        verify(contentScope, never()).eligibleQuestionIdByCode(any(), anyString(), anyString());
    }

    @Test
    void unknownPredictCaseIsSkippedInsteadOfInventingAnAction() {
        List<NextAction> actions = planner.plan(input(predictTask("page.no.such.case")));

        assertTrue(actions.stream().noneMatch(item -> NextAction.TYPE_PREDICT.equals(item.getType())));
    }

    @Test
    void questionAndPredictTasksCanCoexistInOnePlan() {
        when(contentScope.eligibleQuestionIdByCode(any(), anyString(), anyString())).thenReturn(42L);
        List<DiagnosisService.VerificationTask> tasks = new ArrayList<>();
        tasks.add(predictTask("page.lru-vs-fifo.victim"));
        tasks.add(questionTask("q.db.fd.closure-basic"));

        List<NextAction> actions = planner.plan(input(tasks.toArray(new DiagnosisService.VerificationTask[0])));

        assertTrue(actions.stream().anyMatch(item -> NextAction.TYPE_PREDICT.equals(item.getType())));
        assertTrue(actions.stream().anyMatch(item -> NextAction.TYPE_QUESTION.equals(item.getType())));
    }

    // region fixtures

    private NextActionPlanner.PlanInput input(DiagnosisService.VerificationTask... tasks) {
        NextActionPlanner.PlanInput input = new NextActionPlanner.PlanInput();
        input.setUser(user);
        input.setCourseCode("course.database-systems");
        input.setFocus(AiSessionService.FOCUS_LEARN);
        input.setTimeBudgetMinutes(30);
        input.setLimit(5);
        input.setSessionId(31L);
        input.setVerifications(new ArrayList<>(List.of(tasks)));
        Diagnosis diagnosis = new Diagnosis();
        diagnosis.setStatus(Diagnosis.STATUS_TENTATIVE);
        input.setDiagnosis(diagnosis);
        return input;
    }

    private DiagnosisService.VerificationTask questionTask(String code) {
        DiagnosisService.VerificationTask task = new DiagnosisService.VerificationTask();
        task.setQuestionCode(code);
        task.setDiscriminates(new ArrayList<>(List.of("closure.one-step")));
        task.setReason("复合决定因素要连续扩展两轮才能得到完整闭包");
        task.setEstimatedMinutes(3);
        return task;
    }

    private DiagnosisService.VerificationTask predictTask(String caseCode) {
        DiagnosisService.VerificationTask task = new DiagnosisService.VerificationTask();
        task.setPredictCase(caseCode);
        task.setDiscriminates(new ArrayList<>(List.of("lru.hit-no-update")));
        task.setReason("换出页能区分是否更新了最近使用顺序");
        task.setEstimatedMinutes(3);
        return task;
    }

    // endregion
}
