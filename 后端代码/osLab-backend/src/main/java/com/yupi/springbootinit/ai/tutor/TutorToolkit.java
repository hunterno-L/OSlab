package com.yupi.springbootinit.ai.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.ai.plan.AiContentScopeService;
import com.yupi.springbootinit.ai.plan.NextActionPlanner;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.ai.retrieval.HybridRetrievalService;
import com.yupi.springbootinit.learning.PageReplacer;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.learning.PublishedCourseScopeService;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * 导师工具集（计划 §5.3）：检索、读取本人证据、选择已核验活动、页面置换核验、下一步建议。
 *
 * <p>确定性约束：
 * <ul>
 *   <li>{@code select_verified_activity} 只从已发布题目与人工核验的预测案例中选，
 *       模型生成的题目不会被当作正式评估证据；</li>
 *   <li>{@code check_page_trace} 调用既有 {@link PageReplacer}，
 *       且在会话存在未提交的预测任务时只返回允许看到的前缀，绝不提前暴露答案；</li>
 *   <li>{@code read_learning_evidence} 只读，且恒定限定当前用户。</li>
 * </ul>
 */
@Slf4j
@Component
public class TutorToolkit {

    public static final String TOOL_RETRIEVE = "retrieve_sources";
    public static final String TOOL_EVIDENCE = "read_learning_evidence";
    public static final String TOOL_SELECT_ACTIVITY = "select_verified_activity";
    public static final String TOOL_PAGE_TRACE = "check_page_trace";
    public static final String TOOL_NEXT_ACTIONS = "propose_next_actions";

    @Resource
    private HybridRetrievalService retrieval;

    @Resource
    private EvidenceReadService evidenceReader;

    @Resource
    private PublishedCourseScopeService publishedCourseScope;

    @Resource
    private AiContentScopeService contentScope;

    @Resource
    private PredictCaseCatalog predictCases;

    @Resource
    private NextActionPlanner nextActionPlanner;

    private final List<TutorTool> tools = new ArrayList<>();

    @javax.annotation.PostConstruct
    void init() {
        tools.add(new RetrieveSourcesTool());
        tools.add(new ReadLearningEvidenceTool());
        tools.add(new SelectVerifiedActivityTool());
        tools.add(new CheckPageTraceTool());
        tools.add(new ProposeNextActionsTool());
    }

    public List<TutorTool> tools() {
        return tools;
    }

    public TutorTool find(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        for (TutorTool tool : tools) {
            if (tool.name().equals(name.trim())) {
                return tool;
            }
        }
        return null;
    }

    // region retrieve_sources

    class RetrieveSourcesTool implements TutorTool {
        @Override
        public String name() {
            return TOOL_RETRIEVE;
        }

        @Override
        public String description() {
            return "在当前课程与本人授权资料内做语义+关键词+图谱联合检索，返回可引用的段落来源";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return objectSchema(props("query", "string", "检索问句，通常改写自学生问题"));
        }

        @Override
        public Outcome execute(Context context, JsonNode arguments) {
            String query = text(arguments, "query", context.getMessage());
            HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
            scope.setUserId(context.getUser().getId());
            scope.setCourseCode(context.courseCode());
            scope.setNodeCodes(context.getCourseContext() == null
                    ? new ArrayList<>() : context.getCourseContext().getNodeCodes());
            scope.setSelectedSourceIds(context.getCourseContext() == null
                    ? new ArrayList<>() : context.getCourseContext().getSelectedSourceIds());
            HybridRetrievalService.Result result = retrieval.retrieve(scope, query);
            context.setSemanticUsed(result.isSemanticUsed());
            context.setSemanticSkipReason(result.getSemanticSkipReason());
            context.getRetrievalTrace().addAll(result.getTrace());
            if (result.getSources().isEmpty()) {
                return Outcome.empty("课程内没有检索到可引用来源");
            }
            context.setSources(result.getSources());
            StringBuilder payload = new StringBuilder();
            for (int i = 0; i < result.getSources().size(); i++) {
                SourceRef source = result.getSources().get(i);
                payload.append('[').append(i + 1).append("] ")
                        .append(source.getOwnerScope()).append(' ')
                        .append(source.getSourceId()).append(" rev=").append(source.getRevision())
                        .append(' ').append(source.getLocator()).append('\n')
                        .append(StringUtils.abbreviate(source.getExcerpt(), 400)).append("\n\n");
            }
            return Outcome.ok("检索到 " + result.getSources().size() + " 条来源（语义"
                            + (result.isSemanticUsed() ? "可用" : "不可用，已降级") + "）",
                    payload.toString());
        }
    }

    // endregion

    // region read_learning_evidence

    class ReadLearningEvidenceTool implements TutorTool {
        @Override
        public String name() {
            return TOOL_EVIDENCE;
        }

        @Override
        public String description() {
            return "只读本人相关作答、帮助情况、失败类型与复习历史；不包含任何其他学生的数据";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return objectSchema(props("nodeCodes", "array", "限定知识点编码，可空"));
        }

        @Override
        public Outcome execute(Context context, JsonNode arguments) {
            String courseCode = StringUtils.trimToNull(context.courseCode());
            if (courseCode == null || context.getUser() == null || context.getUser().getId() == null) {
                return emptyEvidence(context, "缺少课程或用户上下文，无法读取学习证据", true);
            }
            Map<String, NodeIdentityService.Identity> courseNodes;
            try {
                courseNodes = publishedCourseScope.nodes(courseCode);
            } catch (RuntimeException e) {
                log.warn("[tutor] course evidence scope unavailable: {}", e.getClass().getSimpleName());
                return emptyEvidence(context, "课程证据范围暂不可用", true);
            }
            List<String> requested = stringList(arguments, "nodeCodes");
            if (requested.isEmpty() && context.getCourseContext() != null
                    && context.getCourseContext().getNodeCodes() != null) {
                requested = context.getCourseContext().getNodeCodes();
            }
            Set<String> requestedKeys = new LinkedHashSet<>();
            for (String value : requested) {
                if (StringUtils.isNotBlank(value)) requestedKeys.add(value.trim());
            }
            Set<String> evidenceKeys = new LinkedHashSet<>();
            for (NodeIdentityService.Identity identity : courseNodes.values()) {
                if (requestedKeys.isEmpty() || requestedKeys.contains(identity.nodeCode)
                        || requestedKeys.contains(identity.storageKey)) {
                    if (StringUtils.isNotBlank(identity.nodeCode)) evidenceKeys.add(identity.nodeCode);
                    if (StringUtils.isNotBlank(identity.storageKey)) evidenceKeys.add(identity.storageKey);
                }
            }
            if (evidenceKeys.isEmpty()) {
                return emptyEvidence(context, "当前课程范围内没有可读取的学习证据", false);
            }
            EvidenceReadService.EvidenceSummary summary = evidenceReader.read(
                    context.getUser().getId(), new ArrayList<>(evidenceKeys), 12);
            context.setEvidence(summary);
            if (summary.getRecords().isEmpty()) {
                return Outcome.empty(summary.getNarrative());
            }
            return Outcome.ok("读取本人学习事件 " + summary.getRecords().size() + " 条（失败 "
                            + summary.getFailures() + "、诊断 " + summary.getDiagnostics()
                            + "、获得帮助 " + summary.getHelpedAttempts() + "）",
                    evidenceReader.describe(summary));
        }
    }

    private TutorTool.Outcome emptyEvidence(TutorTool.Context context, String reason, boolean failed) {
        EvidenceReadService.EvidenceSummary summary = new EvidenceReadService.EvidenceSummary();
        summary.setNarrative(reason);
        context.setEvidence(summary);
        return failed ? TutorTool.Outcome.failed(reason) : TutorTool.Outcome.empty(reason);
    }

    // endregion

    // region select_verified_activity

    class SelectVerifiedActivityTool implements TutorTool {
        @Override
        public String name() {
            return TOOL_SELECT_ACTIVITY;
        }

        @Override
        public String description() {
            return "从已发布且人工核验的题目/预测任务中选择，返回活动引用；不返回答案，不生成新题目";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return objectSchema(props("nodeCode", "string", "知识点编码，可空",
                    "kind", "string", "QUESTION 或 PREDICT，可空"));
        }

        @Override
        public Outcome execute(Context context, JsonNode arguments) {
            String nodeCode = text(arguments, "nodeCode", null);
            String kind = StringUtils.upperCase(text(arguments, "kind", ""));
            if (StringUtils.isBlank(nodeCode) && context.getEvidence() != null
                    && !context.getEvidence().getWeakNodeCodes().isEmpty()) {
                nodeCode = context.getEvidence().getWeakNodeCodes().get(0);
            }
            StringBuilder payload = new StringBuilder();
            int count = 0;
            if (!"PREDICT".equals(kind)) {
                List<Long> ids = contentScope.publishedQuestionIds(context.getUser(),
                        context.courseCode(), nodeCode, 3);
                for (Long id : ids) {
                    payload.append("QUESTION ").append(id).append("（已发布，学生口径可作答）\n");
                    context.getSelectedQuestionIds().add(String.valueOf(id));
                    count++;
                }
            }
            if (!"QUESTION".equals(kind)) {
                for (PredictCaseCatalog.PredictCase predictCase : predictCases.all()) {
                    if (count >= 5) {
                        break;
                    }
                    payload.append("PREDICT ").append(predictCase.getCode()).append(' ')
                            .append(predictCase.getTitle())
                            .append("（人工核验，答案由服务端计算器核对）\n");
                    context.getSelectedPredictCases().add(predictCase.getCode());
                    count++;
                }
            }
            if (count == 0) {
                return Outcome.empty("该课程内没有可选的已发布核验活动");
            }
            return Outcome.ok("选出 " + count + " 个已发布/已核验活动", payload.toString());
        }
    }

    // endregion

    // region check_page_trace

    class CheckPageTraceTool implements TutorTool {
        @Override
        public String name() {
            return TOOL_PAGE_TRACE;
        }

        @Override
        public String description() {
            return "调用确定性页面置换计算器生成 FIFO/LRU/OPT 轨迹用于核验；存在未提交预测任务时只返回允许看到的前缀";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return objectSchema(props(
                    "algorithm", "string", "FIFO / LRU / OPT",
                    "frames", "integer", "物理块数",
                    "sequence", "array", "页面访问序列",
                    "uptoStep", "integer", "只计算到第几步（可空）"));
        }

        @Override
        public Outcome execute(Context context, JsonNode arguments) {
            String algorithm = StringUtils.upperCase(text(arguments, "algorithm", "LRU"));
            if (!Arrays.asList("FIFO", "LRU", "OPT").contains(algorithm)) {
                return Outcome.failed("不支持的置换算法：" + algorithm);
            }
            int frames = intArg(arguments, "frames", 3);
            List<Integer> sequence = intList(arguments, "sequence");
            if (frames <= 0 || frames > 16) {
                return Outcome.failed("物理块数必须在 1..16 之间");
            }
            if (sequence.isEmpty() || sequence.size() > 64) {
                return Outcome.failed("访问序列长度必须在 1..64 之间");
            }
            // 答案保护：会话存在未提交的预测任务且序列一致时，只算到待预测步之前
            int guard = pendingPredictGuard(context, algorithm, frames, sequence);
            int upto = intArg(arguments, "uptoStep", sequence.size() - 1);
            upto = Math.max(0, Math.min(upto, sequence.size() - 1));
            if (guard >= 0) {
                upto = Math.min(upto, guard);
            }
            List<Integer> visible = new ArrayList<>(sequence.subList(0, upto + 1));
            SimulationPageVO computed = PageReplacer.replace(algorithm, frames, visible);
            StringBuilder payload = new StringBuilder();
            payload.append("算法=").append(algorithm).append(" 物理块=").append(frames)
                    .append(" 已计算序列=").append(visible)
                    .append(" 缺页=").append(computed.getPageFaults()).append('\n');
            for (SimulationPageVO.PageStep step : computed.getSteps()) {
                payload.append("step").append(step.getStep()).append(" 访问").append(step.getPage())
                        .append(step.getFault() != null && step.getFault() ? " 缺页" : " 命中")
                        .append(step.getEvicted() == null ? "" : " 换出" + step.getEvicted())
                        .append(" 内存=").append(step.getFrames()).append('\n');
            }
            String summary = "核验 " + algorithm + " 前 " + visible.size() + " 步：缺页 "
                    + computed.getPageFaults() + " 次";
            if (guard >= 0 && upto >= guard) {
                summary += "；当前有未提交预测任务，后续轨迹已遮蔽";
                payload.append("（待预测步及其之后的轨迹未计算，避免提前暴露答案）\n");
            }
            context.setPageTraceSummary(summary);
            return Outcome.ok(summary, payload.toString());
        }

        /**
         * @return 允许计算到的最大步序号；-1 表示无需遮蔽
         */
        private int pendingPredictGuard(Context context, String algorithm, int frames, List<Integer> sequence) {
            NextAction current = AiJson.read(context.getSession() == null ? null
                    : context.getSession().getCurrentActionJson(), NextAction.class);
            if (current == null || !NextAction.TYPE_PREDICT.equals(current.getType())
                    || current.getTargetRef() == null) {
                return -1;
            }
            boolean stillOpen = NextAction.STATUS_PENDING.equals(current.getStatus())
                    || NextAction.STATUS_STARTED.equals(current.getStatus());
            if (!stillOpen) {
                return -1;
            }
            PredictCaseCatalog.PredictCase predictCase = predictCases.find(current.getTargetRef().getCode());
            if (predictCase == null || !predictCase.getAlgorithm().equalsIgnoreCase(algorithm)
                    || predictCase.getFrames() != frames
                    || sequence.size() > predictCase.getSequence().size()) {
                return -1;
            }
            for (int i = 0; i < sequence.size(); i++) {
                if (!sequence.get(i).equals(predictCase.getSequence().get(i))) {
                    return -1;
                }
            }
            // 只允许算到待预测步之前（不含该步的换出结果）
            return Math.max(0, predictCase.getPredictStepIndex() - 1);
        }
    }

    // endregion

    // region propose_next_actions

    class ProposeNextActionsTool implements TutorTool {
        @Override
        public String name() {
            return TOOL_NEXT_ACTIONS;
        }

        @Override
        public String description() {
            return "返回符合时间预算、前置条件与内容可用性的下一步建议；用户点击后才开始";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return objectSchema(props("timeBudgetMinutes", "integer", "可用时间，可空"));
        }

        @Override
        public Outcome execute(Context context, JsonNode arguments) {
            int budget = intArg(arguments, "timeBudgetMinutes",
                    context.getSession() == null || context.getSession().getTimeBudgetMinutes() == null
                            ? 0 : context.getSession().getTimeBudgetMinutes());
            NextActionPlanner.PlanInput input = new NextActionPlanner.PlanInput();
            input.setUser(context.getUser());
            input.setCourseCode(context.courseCode());
            input.setFocus(context.getSession() == null ? AiSessionFocus.LEARN : context.getSession().getFocus());
            input.setTimeBudgetMinutes(budget);
            input.setLimit(5);
            input.setSessionId(context.getSession() == null ? null : context.getSession().getId());
            input.setEvidence(context.getEvidence());
            input.setVerifications(context.getDiagnosisResult() == null
                    ? new ArrayList<>() : context.getDiagnosisResult().getVerifications());
            input.setDiagnosis(context.getDiagnosisResult() == null
                    ? null : context.getDiagnosisResult().getDiagnosis());
            input.setSources(context.getSources());
            input.setUnitCode(context.getSession() == null ? null : context.getSession().getUnitCode());
            input.setNodeCodes(context.getCourseContext() == null
                    ? new ArrayList<>() : context.getCourseContext().getNodeCodes());
            NextAction current = AiJson.read(context.getSession() == null ? null
                    : context.getSession().getCurrentActionJson(), NextAction.class);
            if (current != null) {
                input.setReturnRef(current.getTargetRef());
            }
            List<NextAction> actions = nextActionPlanner.plan(input);
            if (actions.isEmpty()) {
                return Outcome.empty("当前课程内没有可安排的已发布活动");
            }
            context.setNextActions(actions);
            StringBuilder payload = new StringBuilder();
            for (NextAction action : actions) {
                payload.append(action.getType()).append(' ')
                        .append(action.getTargetRef() == null ? "" : action.getTargetRef().getKind())
                        .append(' ').append(action.getTargetRef() == null ? "" : action.getTargetRef().getCode())
                        .append(" 约").append(action.getEstimatedMinutes()).append("分钟：")
                        .append(action.getReason()).append('\n');
            }
            return Outcome.ok("生成 " + actions.size() + " 个下一步任务（预算 "
                    + (budget <= 0 ? "未设定" : budget + " 分钟") + "）", payload.toString());
        }
    }

    /** 会话侧重常量（避免直接依赖 AiSessionService 造成循环）。 */
    static final class AiSessionFocus {
        static final String LEARN = "LEARN";
    }

    // endregion

    // region 参数工具

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<String>());
        return schema;
    }

    /** 以 (名称, 类型, 说明) 三元组构造 JSON Schema properties。 */
    private static Map<String, Object> props(String... nameTypeDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i + 2 < nameTypeDescription.length; i += 3) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", nameTypeDescription[i + 1]);
            property.put("description", nameTypeDescription[i + 2]);
            properties.put(nameTypeDescription[i], property);
        }
        return properties;
    }

    private static String text(JsonNode arguments, String field, String fallback) {
        if (arguments == null) {
            return fallback;
        }
        String value = arguments.path(field).asText(null);
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private static int intArg(JsonNode arguments, String field, int fallback) {
        if (arguments == null || !arguments.path(field).isNumber()) {
            return fallback;
        }
        return arguments.path(field).asInt(fallback);
    }

    private static List<String> stringList(JsonNode arguments, String field) {
        List<String> out = new ArrayList<>();
        if (arguments == null || !arguments.path(field).isArray()) {
            return out;
        }
        for (JsonNode item : arguments.path(field)) {
            if (StringUtils.isNotBlank(item.asText())) {
                out.add(item.asText());
            }
        }
        return out;
    }

    private static List<Integer> intList(JsonNode arguments, String field) {
        List<Integer> out = new ArrayList<>();
        if (arguments == null) {
            return out;
        }
        JsonNode node = arguments.path(field);
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isNumber()) {
                    out.add(item.asInt());
                } else if (StringUtils.isNumeric(item.asText("").trim())) {
                    out.add(Integer.parseInt(item.asText().trim()));
                }
            }
        } else if (node.isTextual()) {
            for (String part : node.asText().split("[,\\s]+")) {
                if (StringUtils.isNumeric(part.trim())) {
                    out.add(Integer.parseInt(part.trim()));
                }
            }
        }
        return out;
    }

    // endregion
}
