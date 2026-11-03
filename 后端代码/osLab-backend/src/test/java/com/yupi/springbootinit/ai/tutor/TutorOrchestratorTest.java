package com.yupi.springbootinit.ai.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.ModelGateway;
import com.yupi.springbootinit.ai.diagnosis.DiagnosisService;
import com.yupi.springbootinit.ai.diagnosis.MisconceptionCatalog;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.plan.AiContentScopeService;
import com.yupi.springbootinit.ai.session.AiSessionService;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import com.yupi.springbootinit.model.vo.ai.Diagnosis;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.ai.ToolTraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 编排器的降级与预算回归（任务书 §4）：
 * 模型不可用 / 调用失败 / 语义检索不可用时 mode 必须是 FALLBACK，
 * 每轮工具执行不超过 3 次、模型调用不超过 2 次，
 * 引用编号只能指向本轮真实检索到的来源。
 */
class TutorOrchestratorTest {

    private final ModelGateway gateway = mock(ModelGateway.class);
    private final TutorToolkit toolkit = mock(TutorToolkit.class);
    private final DiagnosisService diagnosisService = mock(DiagnosisService.class);
    private final MisconceptionCatalog catalog = mock(MisconceptionCatalog.class);
    private final AiContentScopeService contentScope = mock(AiContentScopeService.class);
    private final EvidenceReadService evidenceReader = mock(EvidenceReadService.class);
    private final TutorOrchestrator orchestrator = new TutorOrchestrator();

    private final AtomicInteger executedTools = new AtomicInteger();
    private final User user = new User();
    private AiLearningSession session;
    private boolean semanticUsed = true;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        ReflectionTestUtils.setField(orchestrator, "properties", properties);
        ReflectionTestUtils.setField(orchestrator, "modelGateway", gateway);
        ReflectionTestUtils.setField(orchestrator, "toolkit", toolkit);
        ReflectionTestUtils.setField(orchestrator, "diagnosisService", diagnosisService);
        ReflectionTestUtils.setField(orchestrator, "misconceptionCatalog", catalog);
        ReflectionTestUtils.setField(orchestrator, "contentScope", contentScope);
        ReflectionTestUtils.setField(orchestrator, "evidenceReader", evidenceReader);

        user.setId(7L);
        session = new AiLearningSession();
        session.setId(31L);
        session.setUserId(7L);
        session.setCourseCode("course.operating-systems");
        session.setGoal("搞懂页面置换");
        session.setFocus(AiSessionService.FOCUS_LEARN);
        session.setTimeBudgetMinutes(20);
        session.setStatus(AiSessionService.STATUS_ACTIVE);

        executedTools.set(0);
        semanticUsed = true;
        when(catalog.version()).thenReturn("os-page-replacement/1");
        when(contentScope.courseTitle("course.operating-systems")).thenReturn("操作系统");
        when(diagnosisService.diagnose(any())).thenReturn(insufficientDiagnosis());
        // 五个已注册工具都返回一条来源，并记录执行次数
        for (String name : new String[]{TutorToolkit.TOOL_RETRIEVE, TutorToolkit.TOOL_EVIDENCE,
                TutorToolkit.TOOL_SELECT_ACTIVITY, TutorToolkit.TOOL_PAGE_TRACE,
                TutorToolkit.TOOL_NEXT_ACTIONS}) {
            when(toolkit.find(name)).thenReturn(fakeTool(name));
        }
        when(toolkit.find("write_mastery")).thenReturn(null);
    }

    // region 降级标注

    @Test
    void unconfiguredModelProducesRuleAnswerMarkedFallback() {
        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), null);

        assertEquals(AiTutorTurn.MODE_FALLBACK, result.getMode());
        assertNotNull(result.getAnswer());
        assertFalse(result.getAnswer().isBlank(), "降级也必须给出真实可用的规则回答");
        assertEquals("unconfigured", result.getModelLabel());
        verifyNoInteractions(gateway);
        assertTrue(traceText(result).contains("未配置可用模型"), "轨迹必须写明为什么降级");
    }

    @Test
    void modelCallFailureDegradesToRuleAnswerAndKeepsRetryableFlag() {
        ModelEndpoint endpoint = endpoint();
        when(gateway.chat(any(), any())).thenThrow(
                new ModelGateway.ModelCallException("模型调用失败：鉴权失败（HTTP 401）", false));

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint);

        assertEquals(AiTutorTurn.MODE_FALLBACK, result.getMode());
        assertFalse(result.getAnswer().isBlank());
        assertTrue(result.getErrorMessage().contains("鉴权"));
        assertFalse(result.isRetryable(), "401 不是瞬时故障，不该标可重试");
        assertFalse(result.getErrorMessage().contains("sk-"), "错误信息不得包含密钥片段");
    }

    @Test
    void transientModelFailureIsMarkedRetryableSoTheTurnCanRerun() {
        when(gateway.chat(any(), any())).thenThrow(
                new ModelGateway.ModelCallException("模型调用失败：网关超时（HTTP 503）", true));

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertEquals(AiTutorTurn.MODE_FALLBACK, result.getMode());
        assertTrue(result.isRetryable());
    }

    @Test
    void semanticRetrievalUnavailableMarksFallbackEvenWhenTheModelAnswered() {
        semanticUsed = false;
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":[{\"name\":\"retrieve_sources\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("因为命中后 LRU 会更新顺序 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertEquals(AiTutorTurn.MODE_FALLBACK, result.getMode(),
                "语义不可用时即使模型作答也必须标 FALLBACK");
        assertTrue(traceText(result).contains("语义检索不可用"));
    }

    @Test
    void modelAnswerWithSemanticRetrievalIsTheOnlyAiMode() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":[{\"name\":\"retrieve_sources\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("因为命中后 LRU 会更新顺序 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertEquals(AiTutorTurn.MODE_AI, result.getMode());
        assertEquals("因为命中后 LRU 会更新顺序 [1]", result.getAnswer());
    }

    @Test
    void blankModelAnswerIsNeverPassedOffAsAnAnswer() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":[{\"name\":\"retrieve_sources\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("   ");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertEquals(AiTutorTurn.MODE_FALLBACK, result.getMode());
        assertFalse(result.getAnswer().isBlank());
        assertTrue(traceText(result).contains("模型返回空内容"));
    }

    // endregion

    // region 预算

    @Test
    void toolBudgetIsThreeExecutionsPerTurn() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":["
                + "{\"name\":\"retrieve_sources\",\"arguments\":{}},"
                + "{\"name\":\"read_learning_evidence\",\"arguments\":{}},"
                + "{\"name\":\"select_verified_activity\",\"arguments\":{}},"
                + "{\"name\":\"check_page_trace\",\"arguments\":{}},"
                + "{\"name\":\"propose_next_actions\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("结论 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertEquals(3, executedTools.get(), "每轮最多执行 3 次工具");
        assertEquals(3, result.getToolCalls());
        // 计划解析阶段就按预算截断，超出的工具根本不会进入执行队列
        assertFalse(traceText(result).contains(TutorToolkit.TOOL_PAGE_TRACE),
                "超出预算的工具不得被执行");
    }

    @Test
    void planParsingCapsToolCallsAtTheConfiguredBudget() {
        List<TutorOrchestrator.ToolCallPlan> plan = orchestrator.parsePlan(
                "{\"tools\":["
                        + "{\"name\":\"retrieve_sources\"},"
                        + "{\"name\":\"read_learning_evidence\"},"
                        + "{\"name\":\"select_verified_activity\"},"
                        + "{\"name\":\"check_page_trace\"},"
                        + "{\"name\":\"propose_next_actions\"}]}",
                new TutorTool.Context());

        assertEquals(3, plan.size(), "工具计划必须在解析阶段就被预算截断");
    }

    @Test
    void modelCallBudgetIsTwoPerTurn() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":[{\"name\":\"retrieve_sources\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("结论 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        verify(gateway, times(2)).chat(any(), any());
        assertEquals(2, result.getModelCalls());
    }

    @Test
    void malformedPlanIsRepairedLocallyWithoutAnExtraModelCall() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        // 推理型模型常见的"JSON 前后带解释文字"，只允许本地修复，不能再花一次模型调用
        plan.setContent("好的，我选择这些工具：\n{\"tools\":[{\"name\":\"retrieve_sources\"}]}\n以上。");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("结论 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        orchestrator.run(user, session, "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        verify(gateway, times(2)).chat(any(), any());
        assertTrue(executedTools.get() >= 1);
    }

    @Test
    void unregisteredToolIsDroppedAndRetrievalIsAlwaysForced() {
        List<TutorOrchestrator.ToolCallPlan> plan = orchestrator.parsePlan(
                "{\"tools\":[{\"name\":\"write_mastery\"},{\"name\":\"read_learning_evidence\"}]}",
                new TutorTool.Context());

        assertEquals(2, plan.size());
        assertEquals(TutorToolkit.TOOL_RETRIEVE, plan.get(0).getName(),
                "检索必须被强制加入，否则回答没有可引用来源");
        assertEquals(TutorToolkit.TOOL_EVIDENCE, plan.get(1).getName());
        assertTrue(plan.stream().noneMatch(item -> "write_mastery".equals(item.getName())),
                "模型不得调用未注册工具（尤其不允许写掌握度）");
    }

    @Test
    void garbagePlanFallsBackToTheDeterministicPlan() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("我不知道该调用什么工具");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        answer.setContent("结论 [1]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertTrue(traceText(result).contains("模型未给出合法工具计划"));
        assertTrue(executedTools.get() >= 1, "计划失败也必须执行确定性工具计划");
    }

    // endregion

    // region 引用与来源

    @Test
    void citationsOutsideRetrievedSourcesAreStripped() {
        ModelGateway.ChatResult plan = new ModelGateway.ChatResult();
        plan.setContent("{\"tools\":[{\"name\":\"retrieve_sources\",\"arguments\":{}}]}");
        ModelGateway.ChatResult answer = new ModelGateway.ChatResult();
        // 只检索到 1 条来源，[2]/[9] 都是编造的引用
        answer.setContent("结论 [1]，另见 [2] 与 [9]");
        when(gateway.chat(any(), any())).thenReturn(plan, answer);

        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), endpoint());

        assertTrue(result.getAnswer().contains("[1]"));
        assertFalse(result.getAnswer().contains("[2]"), "越界引用必须被移除");
        assertFalse(result.getAnswer().contains("[9]"));
    }

    @Test
    void diagnosisIsComputedDeterministicallyAndReportedWithCatalogVersion() {
        TutorOrchestrator.TurnResult result = orchestrator.run(user, session,
                "为什么 LRU 换出 2？", new AiCourseContext(), null);

        assertNotNull(result.getDiagnosis());
        assertEquals(Diagnosis.STATUS_INSUFFICIENT, result.getDiagnosis().getStatus());
        assertTrue(traceText(result).contains("os-page-replacement/1"),
                "轨迹必须写明用的是哪一版误区目录");
        verify(diagnosisService, atLeastOnce()).diagnose(any());
    }

    // endregion

    // region fixtures

    private ModelEndpoint endpoint() {
        ModelEndpoint endpoint = ModelEndpoint.platform("https://ark.cn-beijing.volces.com/api/v3",
                "deepseek-v3-250324", "sk-test");
        return endpoint;
    }

    private TutorTool fakeTool(String name) {
        return new TutorTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name;
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return new HashMap<>();
            }

            @Override
            public Outcome execute(Context context, JsonNode arguments) {
                executedTools.incrementAndGet();
                if (TutorToolkit.TOOL_RETRIEVE.equals(name)) {
                    context.getSources().add(SourceRef.of("chunk.1", "COURSE",
                            "course.operating-systems", 2, "block.1", "2.2.5", "LRU 命中后更新顺序"));
                    context.setSemanticUsed(semanticUsed);
                    if (!semanticUsed) {
                        context.setSemanticSkipReason("未配置 embedding 模型");
                    }
                }
                return Outcome.ok(name + " 完成", name);
            }
        };
    }

    private DiagnosisService.Result insufficientDiagnosis() {
        DiagnosisService.Result result = new DiagnosisService.Result();
        Diagnosis diagnosis = new Diagnosis();
        diagnosis.setStatus(Diagnosis.STATUS_INSUFFICIENT);
        diagnosis.setExplanation("独立失败观测不足，暂不下结论");
        result.setDiagnosis(diagnosis);
        result.setVerifications(new ArrayList<>());
        result.setMatchedSkills(new ArrayList<>());
        return result;
    }

    private String traceText(TutorOrchestrator.TurnResult result) {
        StringBuilder text = new StringBuilder();
        for (ToolTraceEntry entry : result.getToolTrace()) {
            text.append(entry.getTool()).append(':').append(entry.getSummary()).append('\n');
        }
        return text.toString();
    }

    // endregion
}
