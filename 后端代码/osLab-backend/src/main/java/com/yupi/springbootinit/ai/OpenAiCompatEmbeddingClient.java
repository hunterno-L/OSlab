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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** OpenAI 兼容 {@code /embeddings} 实现；只走平台端点。 */
@Slf4j
@Component
public class OpenAiCompatEmbeddingClient implements EmbeddingClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EMBEDDING_PATH = "/embeddings";
    /** 单次请求最多提交的文本条数，避免小内存部署下一次性构造过大请求体。 */
    private static final int BATCH_LIMIT = 16;

    @Resource
    private AiProperties properties;

    @Resource
    private HttpJsonTransport transport;

    public OpenAiCompatEmbeddingClient() {
    }

    public OpenAiCompatEmbeddingClient(AiProperties properties, HttpJsonTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public boolean available() {
        AiProperties.Ark ark = properties.getArk();
        return ark != null
                && StringUtils.isNotBlank(resolvedApiKey(ark))
                && StringUtils.isNotBlank(resolvedBaseUrl(ark))
                && StringUtils.isNotBlank(ark.getEmbeddingModel());
    }

    @Override
    public String model() {
        return available() ? properties.getArk().getEmbeddingModel() : null;
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            EmbeddingResult empty = new EmbeddingResult();
            empty.setModel(model());
            empty.setDims(0);
            empty.setVectors(Collections.emptyList());
            return empty;
        }
        if (!available()) {
            throw new EmbeddingUnavailableException(
                    "未配置可用的 embedding 模型（AI_ARK_EMBEDDING_MODEL，"
                            + "独立端点另需 AI_EMBEDDING_BASE_URL / AI_EMBEDDING_API_KEY）");
        }
        AiProperties.Ark ark = properties.getArk();
        List<String> sanitized = new ArrayList<>(texts.size());
        for (String text : texts) {
            sanitized.add(StringUtils.isBlank(text) ? " " : StringUtils.left(text, 8000));
        }
        List<float[]> vectors = new ArrayList<>(sanitized.size());
        String resolvedModel = ark.getEmbeddingModel();
        int dims = 0;
        Integer promptTokens = null;
        Integer totalTokens = null;
        long started = System.currentTimeMillis();
        for (int from = 0; from < sanitized.size(); from += BATCH_LIMIT) {
            List<String> batch = sanitized.subList(from, Math.min(from + BATCH_LIMIT, sanitized.size()));
            JsonNode root = postBatch(ark, batch);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != batch.size()) {
                throw new EmbeddingUnavailableException("embedding 响应条数与请求不一致");
            }
            List<JsonNode> ordered = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                ordered.add(item);
            }
            // 部分实现不保证顺序，按 index 显式排序
            ordered.sort((a, b) -> Integer.compare(a.path("index").asInt(0), b.path("index").asInt(0)));
            for (JsonNode item : ordered) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.size() == 0) {
                    throw new EmbeddingUnavailableException("embedding 响应缺少向量");
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                if (dims == 0) {
                    dims = vector.length;
                } else if (dims != vector.length) {
                    throw new EmbeddingUnavailableException("embedding 维度不一致，禁止混用");
                }
                vectors.add(vector);
            }
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                promptTokens = sum(promptTokens, usage.path("prompt_tokens"));
                totalTokens = sum(totalTokens, usage.path("total_tokens"));
            }
            if (StringUtils.isNotBlank(root.path("model").asText(null))) {
                resolvedModel = root.path("model").asText();
            }
        }
        EmbeddingResult result = new EmbeddingResult();
        result.setModel(resolvedModel);
        result.setDims(dims);
        result.setVectors(vectors);
        result.setPromptTokens(promptTokens);
        result.setTotalTokens(totalTokens);
        result.setLatencyMs(System.currentTimeMillis() - started);
        return result;
    }

    private JsonNode postBatch(AiProperties.Ark ark, List<String> batch) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", ark.getEmbeddingModel());
        ArrayNode input = root.putArray("input");
        batch.forEach(input::add);
        String body;
        try {
            body = JSON.writeValueAsString(root);
        } catch (Exception e) {
            throw new EmbeddingUnavailableException("embedding 请求体构造失败", e);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + resolvedApiKey(ark));
        HttpJsonTransport.Response response = transport.post(url(resolvedBaseUrl(ark)), headers, body,
                properties.getConnectTimeoutMs(), properties.getRequestTimeoutMs());
        if (response.getStatus() != 200) {
            // 不把响应体与密钥写进日志；只保留状态码与简短原因
            log.warn("[embedding] request failed status={}", response.getStatus());
            throw new EmbeddingUnavailableException("embedding 服务不可用（HTTP "
                    + response.getStatus() + "）");
        }
        try {
            JsonNode parsed = JSON.readTree(response.getBody());
            if (parsed == null) {
                throw new EmbeddingUnavailableException("embedding 响应为空");
            }
            return parsed;
        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingUnavailableException("embedding 响应不是合法 JSON", e);
        }
    }

    /** 独立 embedding 端点优先；未单独配置时复用对话端点，保持单端点部署兼容。 */
    private static String resolvedBaseUrl(AiProperties.Ark ark) {
        return StringUtils.defaultIfBlank(ark.getEmbeddingBaseUrl(), ark.getBaseUrl());
    }

    private static String resolvedApiKey(AiProperties.Ark ark) {
        return StringUtils.defaultIfBlank(ark.getEmbeddingApiKey(), ark.getApiKey());
    }

    static String url(String baseUrl) {
        String trimmed = StringUtils.stripEnd(StringUtils.trimToEmpty(baseUrl), "/");
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(EMBEDDING_PATH)) {
            return trimmed;
        }
        return trimmed + EMBEDDING_PATH;
    }

    private static Integer sum(Integer current, JsonNode node) {
        if (!node.isNumber()) {
            return current;
        }
        return (current == null ? 0 : current) + node.asInt();
    }
}
