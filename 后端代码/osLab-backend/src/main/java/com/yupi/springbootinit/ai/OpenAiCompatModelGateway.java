package com.yupi.springbootinit.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI 兼容协议模型网关（计划 §5.7）。
 *
 * <p>火山方舟 v3 与绝大多数"兼容接口"个人配置都走 {@code /chat/completions}，
 * 因此平台默认与个人配置共用同一条实现路径：旧入口与新入口调用的是同一套模型解析逻辑。
 *
 * <p>能力探测是真实调用：chat 用最小补全，structured 加 {@code response_format=json_object}，
 * nativeTools 加一个最小工具定义。服务端接受即视为具备该能力，
 * 不接受（4xx 且报参数不支持）则记为不具备，由编排器退回受约束的结构化协议。
 *
 * <p>网关不生成任何教学内容；调用失败只抛异常，降级决策留给编排器，
 * 因此不存在"用模拟回复冒充真实模型"的路径。
 */
@Slf4j
@Component
public class OpenAiCompatModelGateway implements ModelGateway {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CHAT_PATH = "/chat/completions";

    @Resource
    private AiProperties properties;

    @Resource
    private HttpJsonTransport transport;

    /** 测试注入 seam。 */
    public OpenAiCompatModelGateway() {
    }

    public OpenAiCompatModelGateway(AiProperties properties, HttpJsonTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public ModelEndpoint platformEndpoint() {
        AiProperties.Ark ark = properties.getArk();
        if (ark == null || StringUtils.isBlank(ark.getApiKey())) {
            return null;
        }
        return ModelEndpoint.platform(ark.getBaseUrl(), ark.getModel(), ark.getApiKey());
    }

    @Override
    public boolean available(ModelEndpoint endpoint) {
        return endpoint != null
                && StringUtils.isNotBlank(endpoint.getBaseUrl())
                && StringUtils.isNotBlank(endpoint.getModel())
                && StringUtils.isNotBlank(endpoint.getApiKey());
    }

    @Override
    public ChatResult chat(ModelEndpoint endpoint, ChatRequest request) {
        if (!available(endpoint)) {
            throw new ModelCallException("模型未配置（缺少地址、模型或密钥）", false);
        }
        String payload = buildChatPayload(endpoint, request, false);
        long started = System.currentTimeMillis();
        HttpJsonTransport.Response response = post(endpoint, payload);
        long latency = System.currentTimeMillis() - started;
        JsonNode root = parseOrFail(response, endpoint);
        ChatResult result = new ChatResult();
        result.setLatencyMs(latency);
        result.setModel(text(root.path("model"), endpoint.getModel()));
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new ModelCallException("模型响应缺少 choices", true);
        }
        JsonNode message = choices.get(0).path("message");
        result.setContent(message.path("content").asText(""));
        result.setFinishReason(choices.get(0).path("finish_reason").asText(null));
        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            // 价格未知时只记用量，不编造费用（计划 §5.7）
            result.setPromptTokens(intOrNull(usage, "prompt_tokens"));
            result.setCompletionTokens(intOrNull(usage, "completion_tokens"));
            result.setTotalTokens(intOrNull(usage, "total_tokens"));
        }
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode call : toolCalls) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId(call.path("id").asText(null));
                toolCall.setName(call.path("function").path("name").asText(null));
                toolCall.setArgumentsJson(call.path("function").path("arguments").asText(""));
                if (StringUtils.isNotBlank(toolCall.getName())) {
                    result.getToolCalls().add(toolCall);
                }
            }
        }
        return result;
    }

    @Override
    public CapabilityProbe probe(ModelEndpoint endpoint) {
        CapabilityProbe probe = new CapabilityProbe();
        long started = System.currentTimeMillis();
        if (!available(endpoint)) {
            probe.setOk(false);
            probe.setError("模型未配置（缺少地址、模型或密钥）");
            probe.setCapabilities(ModelCapabilities.none());
            return probe;
        }
        try {
            JsonNode chatRoot = sendProbe(endpoint, plainProbePayload(endpoint));
            if (chatRoot == null) {
                probe.setOk(false);
                probe.setError("基础对话调用失败");
                probe.setCapabilities(ModelCapabilities.none());
                probe.setLatencyMs(System.currentTimeMillis() - started);
                return probe;
            }
            ModelCapabilities capabilities = new ModelCapabilities(true, false, false);
            capabilities.setStructured(probeJsonMode(endpoint));
            capabilities.setNativeTools(probeTools(endpoint));
            probe.setCapabilities(capabilities);
            probe.setOk(true);
            probe.setLatencyMs(System.currentTimeMillis() - started);
            return probe;
        } catch (ModelCallException e) {
            probe.setOk(false);
            probe.setError(e.getMessage());
            probe.setCapabilities(ModelCapabilities.none());
            probe.setLatencyMs(System.currentTimeMillis() - started);
            return probe;
        }
    }

    // region 内部实现

    private JsonNode sendProbe(ModelEndpoint endpoint, String payload) {
        HttpJsonTransport.Response response = post(endpoint, payload);
        if (response.getStatus() == 200) {
            return parseOrNull(response.getBody());
        }
        throw new ModelCallException(describeFailure(response, endpoint), isRetryableStatus(response.getStatus()));
    }

    private boolean probeJsonMode(ModelEndpoint endpoint) {
        ChatRequest request = new ChatRequest();
        request.setSystem("You answer with a single JSON object.");
        request.setUser("Return JSON {\"ok\":true}");
        request.setMaxTokens(200);
        request.setJsonMode(true);
        try {
            HttpJsonTransport.Response response = post(endpoint, buildChatPayload(endpoint, request, true));
            if (response.getStatus() != 200) {
                return false;
            }
            JsonNode root = parseOrNull(response.getBody());
            if (root == null) {
                return false;
            }
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            // 推理型模型可能把预算用在思考上返回空串：服务端接受了该参数即视为可用
            return StringUtils.isBlank(content) || parseOrNull(content) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean probeTools(ModelEndpoint endpoint) {
        ChatRequest request = new ChatRequest();
        request.setSystem("You may call the probe tool.");
        request.setUser("Call the probe tool now.");
        request.setMaxTokens(200);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new HashMap<String, Object>());
        parameters.put("required", new ArrayList<String>());
        request.getTools().add(new ToolSpec("probe", "capability probe", parameters));
        try {
            HttpJsonTransport.Response response = post(endpoint, buildChatPayload(endpoint, request, true));
            return response.getStatus() == 200 && parseOrNull(response.getBody()) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String plainProbePayload(ModelEndpoint endpoint) {
        ChatRequest request = new ChatRequest();
        request.setUser("Reply with the single word: ok");
        request.setMaxTokens(64);
        return buildChatPayload(endpoint, request, false);
    }

    private HttpJsonTransport.Response post(ModelEndpoint endpoint, String payload) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + endpoint.getApiKey());
        return transport.post(chatUrl(endpoint.getBaseUrl()), headers, payload,
                properties.getConnectTimeoutMs(), properties.getRequestTimeoutMs());
    }

    static String chatUrl(String baseUrl) {
        String trimmed = StringUtils.stripEnd(StringUtils.trimToEmpty(baseUrl), "/");
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(CHAT_PATH)) {
            return trimmed;
        }
        return trimmed + CHAT_PATH;
    }

    /**
     * @param probeRequest true 时容忍个别兼容实现对 response_format/tools 的严格校验差异
     */
    private String buildChatPayload(ModelEndpoint endpoint, ChatRequest request, boolean probeRequest) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", endpoint.getModel());
        ArrayNode messages = root.putArray("messages");
        if (StringUtils.isNotBlank(request.getSystem())) {
            messages.addObject().put("role", "system").put("content", request.getSystem());
        }
        if (request.getHistory() != null) {
            for (ChatTurn turn : request.getHistory()) {
                if (turn == null || StringUtils.isBlank(turn.getRole())) {
                    continue;
                }
                messages.addObject().put("role", turn.getRole())
                        .put("content", StringUtils.defaultString(turn.getContent()));
            }
        }
        messages.addObject().put("role", "user").put("content", StringUtils.defaultString(request.getUser()));
        if (request.getMaxTokens() != null && request.getMaxTokens() > 0) {
            root.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.isJsonMode()) {
            root.putObject("response_format").put("type", "json_object");
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolSpec spec : request.getTools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", spec.getName());
                function.put("description", StringUtils.defaultString(spec.getDescription()));
                function.set("parameters", spec.getParameters() == null
                        ? JSON.createObjectNode() : JSON.valueToTree(spec.getParameters()));
            }
            if (!probeRequest) {
                root.put("tool_choice", "auto");
            }
        }
        root.put("stream", false);
        try {
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new ModelCallException("请求体构造失败", false);
        }
    }

    private JsonNode parseOrFail(HttpJsonTransport.Response response, ModelEndpoint endpoint) {
        if (response.getStatus() != 200) {
            throw new ModelCallException(describeFailure(response, endpoint),
                    isRetryableStatus(response.getStatus()));
        }
        JsonNode root = parseOrNull(response.getBody());
        if (root == null) {
            throw new ModelCallException("模型响应不是合法 JSON", true);
        }
        return root;
    }

    /** 失败原因可展示、可记录，但绝不包含密钥或完整响应体。 */
    private String describeFailure(HttpJsonTransport.Response response, ModelEndpoint endpoint) {
        int status = response.getStatus();
        if (status == 0) {
            return "模型服务不可达或超时（" + StringUtils.abbreviate(response.getBody(), 120) + "）";
        }
        if (status == 401 || status == 403) {
            return "模型鉴权失败（API Key 无效或无该模型权限）";
        }
        if (status == 404) {
            return "模型或接口不存在：" + StringUtils.defaultString(endpoint.getModel());
        }
        if (status == 429) {
            return "模型限流或额度不足";
        }
        String detail = "";
        JsonNode root = parseOrNull(response.getBody());
        if (root != null) {
            JsonNode error = root.path("error");
            detail = error.path("message").asText("");
            if (StringUtils.isBlank(detail)) {
                detail = error.path("code").asText("");
            }
        }
        return "模型调用失败（HTTP " + status + "）"
                + (StringUtils.isBlank(detail) ? "" : "：" + StringUtils.abbreviate(detail, 160));
    }

    private static boolean isRetryableStatus(int status) {
        return status == 0 || status == 408 || status == 429 || status >= 500;
    }

    private static JsonNode parseOrNull(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String fallback) {
        String value = node.asText(null);
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    // endregion
}
