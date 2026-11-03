package com.yupi.springbootinit.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型网关回归（计划 §5.7 / 任务书 §4）：
 * 真实响应解析、能力探测、失败分类与降级依据。
 *
 * <p>用注入的传输层返回真实协议形状的报文，覆盖"模型失败"风险；
 * 这不模拟教学回答，只验证网关不把失败伪装成成功。
 */
class OpenAiCompatModelGatewayTest {

    private static final String API_KEY = "sk-secret-value-do-not-leak";

    /** 记录请求，返回预设响应。 */
    private static class RecordingTransport implements HttpJsonTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<Map<String, String>> headerSets = new ArrayList<>();
        int status = 200;
        String body = "{}";

        @Override
        public Response post(String url, Map<String, String> headers, String body,
                             int connectTimeoutMs, int readTimeoutMs) {
            urls.add(url);
            bodies.add(body);
            headerSets.add(headers);
            // 注意：形参 body 是请求体，响应体取字段 this.body
            return new Response(status, this.body);
        }
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getArk().setApiKey(API_KEY);
        properties.getArk().setBaseUrl("https://ark.cn-beijing.volces.com/api/v3");
        properties.getArk().setModel("deepseek-flash");
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(2000);
        return properties;
    }

    private static OpenAiCompatModelGateway gateway(RecordingTransport transport) {
        return new OpenAiCompatModelGateway(properties(), transport);
    }

    private static final String CHAT_OK = "{\"id\":\"x\",\"model\":\"deepseek-flash\",\"choices\":[{\"index\":0,"
            + "\"message\":{\"role\":\"assistant\",\"content\":\"LRU 会更新最近使用顺序\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":36,\"completion_tokens\":12,\"total_tokens\":48}}";

    @Test
    void platformEndpointComesFromArkConfig() {
        OpenAiCompatModelGateway gateway = gateway(new RecordingTransport());
        ModelEndpoint endpoint = gateway.platformEndpoint();
        assertNotNull(endpoint);
        assertTrue(endpoint.isPlatformDefault());
        assertEquals("deepseek-flash", endpoint.getModel());
        assertTrue(gateway.available(endpoint));
    }

    @Test
    void platformEndpointIsNullWithoutApiKey() {
        AiProperties properties = properties();
        properties.getArk().setApiKey("");
        OpenAiCompatModelGateway gateway = new OpenAiCompatModelGateway(properties, new RecordingTransport());
        assertNull(gateway.platformEndpoint(), "未配置密钥时不得伪造可用端点");
    }

    @Test
    void chatParsesContentUsageAndBuildsOpenAiCompatibleRequest() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = CHAT_OK;
        OpenAiCompatModelGateway gateway = gateway(transport);

        ModelGateway.ChatRequest request = ModelGateway.ChatRequest.of("你是课程导师", "为什么 LRU 换出 2？");
        request.setMaxTokens(900);
        ModelGateway.ChatResult result = gateway.chat(gateway.platformEndpoint(), request);

        assertEquals("LRU 会更新最近使用顺序", result.getContent());
        assertEquals("deepseek-flash", result.getModel());
        assertEquals(48, result.getTotalTokens().intValue());
        assertEquals(36, result.getPromptTokens().intValue());
        assertEquals("stop", result.getFinishReason());
        assertFalse(result.hasToolCalls());

        assertEquals("https://ark.cn-beijing.volces.com/api/v3/chat/completions", transport.urls.get(0));
        assertEquals("Bearer " + API_KEY, transport.headerSets.get(0).get("Authorization"));
        assertTrue(transport.bodies.get(0).contains("\"model\":\"deepseek-flash\""));
        assertTrue(transport.bodies.get(0).contains("\"max_tokens\":900"));
        assertTrue(transport.bodies.get(0).contains("你是课程导师"));
    }

    @Test
    void chatSerializesHistoryJsonModeAndTools() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.body = CHAT_OK;
        OpenAiCompatModelGateway gateway = gateway(transport);

        ModelGateway.ChatRequest request = ModelGateway.ChatRequest.of("system", "user");
        request.getHistory().add(new ModelGateway.ChatTurn("assistant", "上一轮回答"));
        request.setJsonMode(true);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        request.getTools().add(new ModelGateway.ToolSpec("retrieve_sources", "检索", parameters));

        gateway.chat(gateway.platformEndpoint(), request);
        JsonNode sent = AiJson.mapper().readTree(transport.bodies.get(0));
        assertEquals(3, sent.path("messages").size(), "system + history + user");
        assertEquals("assistant", sent.path("messages").get(1).path("role").asText());
        assertEquals("json_object", sent.path("response_format").path("type").asText());
        assertEquals("retrieve_sources", sent.path("tools").get(0).path("function").path("name").asText());
        assertEquals("auto", sent.path("tool_choice").asText());
        assertFalse(sent.path("stream").asBoolean(true));
    }

    @Test
    void chatParsesNativeToolCalls() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"check_page_trace\",\"arguments\":\"{\\\"algorithm\\\":\\\"LRU\\\"}\"}}]}}],"
                + "\"usage\":{\"total_tokens\":10}}";
        OpenAiCompatModelGateway gateway = gateway(transport);
        ModelGateway.ChatResult result = gateway.chat(gateway.platformEndpoint(),
                ModelGateway.ChatRequest.of("s", "u"));
        assertTrue(result.hasToolCalls());
        assertEquals("check_page_trace", result.getToolCalls().get(0).getName());
        assertEquals("call_1", result.getToolCalls().get(0).getId());
        assertTrue(result.getToolCalls().get(0).getArgumentsJson().contains("LRU"));
    }

    @Test
    void authFailureIsNotRetryableAndNeverLeaksTheKey() {
        RecordingTransport transport = new RecordingTransport();
        transport.status = 401;
        transport.body = "{\"error\":{\"code\":\"AuthenticationError\",\"message\":\"key " + API_KEY + "\"}}";
        OpenAiCompatModelGateway gateway = gateway(transport);
        ModelGateway.ModelCallException failure = assertThrows(ModelGateway.ModelCallException.class,
                () -> gateway.chat(gateway.platformEndpoint(), ModelGateway.ChatRequest.of("s", "u")));
        assertFalse(failure.isRetryable(), "鉴权失败重试无意义");
        assertFalse(failure.getMessage().contains(API_KEY), "失败原因不得包含密钥");
        assertFalse(failure.getMessage().contains("sk-secret"), "失败原因不得包含密钥片段");
        assertTrue(failure.getMessage().contains("鉴权"));
    }

    @Test
    void transientFailuresAreRetryableSoTheTurnCanDegrade() {
        for (int status : new int[]{0, 408, 429, 500, 502, 503}) {
            RecordingTransport transport = new RecordingTransport();
            transport.status = status;
            transport.body = status == 0 ? "SocketTimeoutException: read timed out" : "{}";
            OpenAiCompatModelGateway retryGateway = gateway(transport);
            ModelGateway.ModelCallException failure = assertThrows(ModelGateway.ModelCallException.class,
                    () -> retryGateway.chat(retryGateway.platformEndpoint(), ModelGateway.ChatRequest.of("s", "u")),
                    "status " + status);
            assertTrue(failure.isRetryable(), "status " + status + " 应可重试");
        }
    }

    @Test
    void missingChoicesAndBadJsonAreFailuresNotSilentSuccess() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"model\":\"m\",\"choices\":[]}";
        OpenAiCompatModelGateway gateway = gateway(transport);
        assertThrows(ModelGateway.ModelCallException.class,
                () -> gateway.chat(gateway.platformEndpoint(), ModelGateway.ChatRequest.of("s", "u")));

        transport.body = "not json at all";
        assertThrows(ModelGateway.ModelCallException.class,
                () -> gateway.chat(gateway.platformEndpoint(), ModelGateway.ChatRequest.of("s", "u")));
    }

    @Test
    void unavailableEndpointFailsFastWithoutHttpCall() {
        RecordingTransport transport = new RecordingTransport();
        OpenAiCompatModelGateway gateway = gateway(transport);
        assertThrows(ModelGateway.ModelCallException.class,
                () -> gateway.chat(null, ModelGateway.ChatRequest.of("s", "u")));
        assertTrue(transport.urls.isEmpty(), "未配置端点不得发起请求");
    }

    @Test
    void probeDetectsAllThreeCapabilities() {
        RecordingTransport transport = new RecordingTransport() {
            @Override
            public Response post(String url, Map<String, String> headers, String body,
                                 int connectTimeoutMs, int readTimeoutMs) {
                super.post(url, headers, body, connectTimeoutMs, readTimeoutMs);
                return new Response(200, "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{\\\"ok\\\":true}\"}}]}");
            }
        };
        OpenAiCompatModelGateway gateway = gateway(transport);
        ModelGateway.CapabilityProbe probe = gateway.probe(gateway.platformEndpoint());
        assertTrue(probe.isOk());
        assertTrue(probe.getCapabilities().isChat());
        assertTrue(probe.getCapabilities().isStructured());
        assertTrue(probe.getCapabilities().isNativeTools());
        assertEquals(3, transport.bodies.size(), "chat + structured + tools 三次真实探测");
        assertFalse(transport.bodies.get(1).contains("\"tools\""), "结构化探测不带工具");
        assertTrue(transport.bodies.get(2).contains("\"tools\""), "工具探测必须带工具定义");
        assertFalse(transport.bodies.get(2).contains("tool_choice"), "探测不强制工具调用");
    }

    @Test
    void probeReportsUnsupportedCapabilitiesInsteadOfAssuming() {
        RecordingTransport transport = new RecordingTransport() {
            private int call;

            @Override
            public Response post(String url, Map<String, String> headers, String body,
                                 int connectTimeoutMs, int readTimeoutMs) {
                super.post(url, headers, body, connectTimeoutMs, readTimeoutMs);
                call++;
                if (call == 1) {
                    return new Response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
                }
                if (call == 2) {
                    return new Response(400, "{\"error\":{\"message\":\"response_format is not supported\"}}");
                }
                return new Response(400, "{\"error\":{\"message\":\"tools is not supported\"}}");
            }
        };
        OpenAiCompatModelGateway gateway = gateway(transport);
        ModelGateway.CapabilityProbe probe = gateway.probe(gateway.platformEndpoint());
        assertTrue(probe.isOk(), "基础对话可用即算测试通过");
        assertTrue(probe.getCapabilities().isChat());
        assertFalse(probe.getCapabilities().isStructured(), "不支持结构化必须如实记录");
        assertFalse(probe.getCapabilities().isNativeTools(), "不支持原生工具必须如实记录");
    }

    @Test
    void probeFailureReturnsReasonWithoutThrowing() {
        RecordingTransport transport = new RecordingTransport();
        transport.status = 401;
        transport.body = "{\"error\":{\"message\":\"bad key\"}}";
        OpenAiCompatModelGateway gateway = gateway(transport);
        ModelGateway.CapabilityProbe probe = gateway.probe(gateway.platformEndpoint());
        assertFalse(probe.isOk());
        assertNotNull(probe.getError());
        assertFalse(probe.getCapabilities().isChat());
    }

    @Test
    void probeRejectsUnconfiguredEndpoint() {
        OpenAiCompatModelGateway gateway = gateway(new RecordingTransport());
        ModelGateway.CapabilityProbe probe = gateway.probe(null);
        assertFalse(probe.isOk());
        assertNotNull(probe.getError());
    }

    @Test
    void chatUrlHandlesTrailingSlashAndExplicitPath() {
        assertEquals("https://a.com/v3/chat/completions",
                OpenAiCompatModelGateway.chatUrl("https://a.com/v3"));
        assertEquals("https://a.com/v3/chat/completions",
                OpenAiCompatModelGateway.chatUrl("https://a.com/v3/"));
        assertEquals("https://a.com/v3/chat/completions",
                OpenAiCompatModelGateway.chatUrl("https://a.com/v3/chat/completions"));
    }
}
