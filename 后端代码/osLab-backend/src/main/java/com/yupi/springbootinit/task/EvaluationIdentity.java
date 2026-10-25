package com.yupi.springbootinit.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 跨执行副本的稳定题目身份：来源稳定代码 + 规范化判分内容指纹。 */
public final class EvaluationIdentity {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EvaluationIdentity() {
    }

    public static String forQuestion(JsonNode content, String sourceQuestionCode) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("title", text(content, "title").trim());
        canonical.put("content", text(content, "content").trim());
        canonical.put("type", text(content, "type").trim().toUpperCase());
        canonical.set("options", normalizedOptions(content.path("options")));
        canonical.set("answer", normalizedAnswer(content, text(content, "type")));
        List<String> nodes = new ArrayList<>();
        for (JsonNode node : content.path("nodeCodes")) {
            String code = node.asText("").trim();
            if (!code.isEmpty()) nodes.add(code);
        }
        Collections.sort(nodes);
        ArrayNode nodeArray = canonical.putArray("nodes");
        nodes.forEach(nodeArray::add);
        String prefix = sourceQuestionCode == null || sourceQuestionCode.trim().isEmpty()
                ? "authored" : sourceQuestionCode.trim();
        return "q:" + sanitize(prefix) + ":" + sha256(canonical.toString()).substring(0, 32);
    }

    /** 由数据库实体构造同一身份（共享题目首次作答也写入身份，便于任务副本去重）。 */
    public static String forLearningQuestion(com.yupi.springbootinit.model.entity.LearningQuestion question,
                                             List<String> nodeCodes) {
        ObjectNode content = JSON.createObjectNode();
        content.put("title", question.getTitle());
        content.put("content", question.getContent());
        content.put("type", question.getType());
        content.set("options", parse(question.getOptions()));
        content.set("answer", parse(question.getAnswer()));
        ArrayNode nodes = content.putArray("nodeCodes");
        if (nodeCodes != null) nodeCodes.forEach(nodes::add);
        return forQuestion(content, question.getQuestionCode());
    }

    private static JsonNode parse(String json) {
        if (json == null || json.trim().isEmpty()) return JSON.nullNode();
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return JSON.getNodeFactory().textNode(json);
        }
    }

    /** 代码实验不参与题目重复观测去重，仅保留内容指纹用于溯源。 */
    public static String forExperiment(JsonNode content) {
        ObjectNode canonical = JSON.createObjectNode();
        canonical.put("title", text(content, "title").trim());
        canonical.put("content", text(content, "content").trim());
        canonical.set("judgeCase", content.path("judgeCase"));
        canonical.set("judgeConfig", content.path("judgeConfig"));
        List<String> languages = new ArrayList<>();
        for (JsonNode language : content.path("languages")) languages.add(language.asText(""));
        Collections.sort(languages);
        ArrayNode languageArray = canonical.putArray("languages");
        languages.forEach(languageArray::add);
        return "x:" + sha256(canonical.toString()).substring(0, 40);
    }

    private static ArrayNode normalizedOptions(JsonNode options) {
        List<JsonNode> entries = new ArrayList<>();
        options.forEach(entries::add);
        entries.sort((a, b) -> text(a, "key").compareTo(text(b, "key")));
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode entry : entries) {
            ObjectNode option = result.addObject();
            option.put("key", text(entry, "key"));
            option.put("value", text(entry, "value"));
        }
        return result;
    }

    private static JsonNode normalizedAnswer(JsonNode content, String type) {
        JsonNode answer = content.path("answer");
        if (answer == null || answer.isNull() || answer.isMissingNode()) {
            return JSON.nullNode();
        }
        if (answer.isArray()) {
            List<String> values = new ArrayList<>();
            answer.forEach(item -> values.add(item.asText("")));
            if ("MULTIPLE".equalsIgnoreCase(type)) Collections.sort(values);
            ArrayNode result = JSON.createArrayNode();
            values.forEach(result::add);
            return result;
        }
        if (answer.isBoolean()) {
            return JSON.getNodeFactory().booleanNode(answer.asBoolean());
        }
        return JSON.getNodeFactory().textNode(answer.asText(""));
    }

    private static String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private static String sanitize(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 80 ? sha256(cleaned).substring(0, 24) : cleaned;
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
