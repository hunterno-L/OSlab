package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import java.util.*;

/** Small explicit JSON boundary for the versioned authoring protocol. */
final class CourseJson {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static JsonNode parse(String text) {
        try { return MAPPER.readTree(text == null || text.trim().isEmpty() ? "{}" : text); }
        catch (Exception e) { throw bad("JSON格式无效"); }
    }
    static String json(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw bad("JSON序列化失败"); }
    }
    static String text(JsonNode n, String key) { return n.path(key).asText(""); }
    static String required(JsonNode n, String key) {
        if(!n.path(key).isTextual())throw bad(key+"必须为字符串");
        String s = text(n, key).trim();
        if (s.isEmpty()) throw bad(key + "不能为空");
        return s;
    }
    static String code(JsonNode n, String key) {
        String s = required(n, key);
        if (s.length() > 128 || !s.matches("[a-z][a-z0-9._-]*")) throw bad(key + "编号无效");
        return s;
    }
    static BusinessException bad(String message) { return new BusinessException(ErrorCode.PARAMS_ERROR, message); }
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String)pairs[i], pairs[i+1]);
        return out;
    }
    static ObjectNode object(JsonNode n) {
        if (!n.isObject()) throw bad("对象不能为空");
        return ((ObjectNode)n).deepCopy();
    }
    private CourseJson() { }
}
