package com.yupi.springbootinit.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embedding 客户端回归（计划 §5.2）：
 * 未配置时必须显式不可用（让检索诚实降级），向量维度不一致必须拒绝，
 * 批量响应按 index 排序而不是信任服务端顺序。
 */
class OpenAiCompatEmbeddingClientTest {

    private static class RecordingTransport implements HttpJsonTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<Map<String, String>> requestHeaders = new ArrayList<>();
        int status = 200;
        String body = "{}";
        /** 每次调用返回不同响应，用于批量测试。 */
        List<String> scripted;
        int call;

        @Override
        public Response post(String url, Map<String, String> headers, String body,
                             int connectTimeoutMs, int readTimeoutMs) {
            urls.add(url);
            bodies.add(body);
            requestHeaders.add(new java.util.HashMap<>(headers));
            String response = scripted != null && call < scripted.size() ? scripted.get(call) : this.body;
            call++;
            return new Response(status, response);
        }
    }

    private static AiProperties properties(String embeddingModel) {
        AiProperties properties = new AiProperties();
        properties.getArk().setApiKey("sk-key");
        properties.getArk().setBaseUrl("https://ark.cn-beijing.volces.com/api/v3");
        properties.getArk().setEmbeddingModel(embeddingModel);
        return properties;
    }

    @Test
    void unavailableWhenNoEmbeddingModelConfigured() {
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties(""), new RecordingTransport());
        assertFalse(client.available(), "未配置 embedding 模型必须显式不可用");
        assertNull(client.model());
        EmbeddingClient.EmbeddingUnavailableException failure =
                assertThrows(EmbeddingClient.EmbeddingUnavailableException.class,
                        () -> client.embed(Collections.singletonList("LRU")));
        assertTrue(failure.getMessage().contains("AI_ARK_EMBEDDING_MODEL"),
                "失败原因要指出缺失的环境变量，便于演示准备");
    }

    @Test
    void unavailableWhenNoApiKey() {
        AiProperties properties = properties("doubao-embedding-text-240715");
        properties.getArk().setApiKey("");
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties, new RecordingTransport());
        assertFalse(client.available());
    }

    @Test
    void emptyInputDoesNotCallTheNetwork() {
        RecordingTransport transport = new RecordingTransport();
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);
        EmbeddingClient.EmbeddingResult result = client.embed(Collections.emptyList());
        assertTrue(result.getVectors().isEmpty());
        assertTrue(transport.urls.isEmpty());
    }

    @Test
    void parsesVectorsDimsAndUsage() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"model\":\"emb-model\",\"data\":["
                + "{\"index\":0,\"embedding\":[0.1,0.2,0.3]},"
                + "{\"index\":1,\"embedding\":[0.4,0.5,0.6]}],"
                + "\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}";
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);

        EmbeddingClient.EmbeddingResult result = client.embed(Arrays.asList("LRU", "FIFO"));
        assertEquals(2, result.getVectors().size());
        assertEquals(3, result.getDims());
        assertEquals(0.1f, result.getVectors().get(0)[0], 1e-6);
        assertEquals(0.6f, result.getVectors().get(1)[2], 1e-6);
        assertEquals(5, result.getTotalTokens().intValue());
        assertEquals("emb-model", result.getModel());
        assertEquals("https://ark.cn-beijing.volces.com/api/v3/embeddings", transport.urls.get(0));
        assertTrue(transport.bodies.get(0).contains("\"model\":\"emb-model\""));
        assertEquals("Bearer sk-key", transport.requestHeaders.get(0).get("Authorization"),
                "未单独配置时复用对话端点的密钥");
    }

    @Test
    void dedicatedEmbeddingEndpointUsesItsOwnBaseUrlAndKey() {
        AiProperties properties = properties("qwen3.7-text-embedding");
        properties.getArk().setEmbeddingBaseUrl("https://ws.example.com/compatible-mode/v1");
        properties.getArk().setEmbeddingApiKey("sk-embed");
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}";
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties, transport);

        client.embed(Collections.singletonList("LRU"));

        assertEquals("https://ws.example.com/compatible-mode/v1/embeddings", transport.urls.get(0),
                "独立 embedding 端点必须优先于对话端点");
        assertEquals("Bearer sk-embed", transport.requestHeaders.get(0).get("Authorization"));
        assertTrue(transport.bodies.get(0).contains("\"model\":\"qwen3.7-text-embedding\""));
    }

    @Test
    void reordersBatchByIndexInsteadOfTrustingOrder() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"data\":["
                + "{\"index\":1,\"embedding\":[9.0]},"
                + "{\"index\":0,\"embedding\":[1.0]}]}";
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);
        EmbeddingClient.EmbeddingResult result = client.embed(Arrays.asList("first", "second"));
        assertEquals(1.0f, result.getVectors().get(0)[0], 1e-6, "index=0 必须对应第一条文本");
        assertEquals(9.0f, result.getVectors().get(1)[0], 1e-6);
    }

    @Test
    void rejectsDimensionMismatchSoModelsNeverMix() {
        RecordingTransport transport = new RecordingTransport();
        transport.body = "{\"data\":["
                + "{\"index\":0,\"embedding\":[0.1,0.2]},"
                + "{\"index\":1,\"embedding\":[0.3]}]}";
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);
        EmbeddingClient.EmbeddingUnavailableException failure = assertThrows(
                EmbeddingClient.EmbeddingUnavailableException.class,
                () -> client.embed(Arrays.asList("a", "b")));
        assertTrue(failure.getMessage().contains("维度"), "维度不一致必须拒绝，禁止混用向量");
    }

    @Test
    void httpFailureIsReportedAsUnavailableWithoutBodyLeak() {
        RecordingTransport transport = new RecordingTransport();
        transport.status = 401;
        transport.body = "{\"error\":{\"message\":\"The API key format is incorrect\"}}";
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);
        EmbeddingClient.EmbeddingUnavailableException failure = assertThrows(
                EmbeddingClient.EmbeddingUnavailableException.class,
                () -> client.embed(Collections.singletonList("a")));
        assertTrue(failure.getMessage().contains("401"));
        assertFalse(failure.getMessage().contains("key format"), "不得把上游报文透传到界面");
    }

    @Test
    void batchesLargeRequests() {
        RecordingTransport transport = new RecordingTransport();
        List<String> scripted = new ArrayList<>();
        // 40 条文本按 16 条一批 → 3 批
        for (int batch = 0; batch < 3; batch++) {
            int size = batch < 2 ? 16 : 8;
            StringBuilder sb = new StringBuilder("{\"data\":[");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"index\":").append(i).append(",\"embedding\":[0.5]}");
            }
            scripted.add(sb.append("]}").toString());
        }
        transport.scripted = scripted;
        OpenAiCompatEmbeddingClient client =
                new OpenAiCompatEmbeddingClient(properties("emb-model"), transport);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            texts.add("text-" + i);
        }
        EmbeddingClient.EmbeddingResult result = client.embed(texts);
        assertEquals(40, result.getVectors().size());
        assertEquals(3, transport.bodies.size(), "必须分批，避免小内存部署构造过大请求体");
    }

    @Test
    void embeddingUrlHandlesTrailingSlash() {
        assertEquals("https://a.com/v3/embeddings", OpenAiCompatEmbeddingClient.url("https://a.com/v3/"));
        assertEquals("https://a.com/v3/embeddings",
                OpenAiCompatEmbeddingClient.url("https://a.com/v3/embeddings"));
    }
}
