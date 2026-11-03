package com.yupi.springbootinit.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型网关（计划 §6.2 B 侧接口）。
 *
 * <p>旧的全局助手 {@code /chat/ask} 与图谱导师 {@code /learning/tutor/ask}
 * 都必须经过本网关调用模型，避免"新入口能配置、旧入口另一套模型"。
 *
 * <p>网关只做传输与协议适配：不写分数、不写掌握度、不判断教学结论。
 */
public interface ModelGateway {

    /** 平台默认端点（{@code ai.ark.*}）；未配置密钥时返回 null。 */
    ModelEndpoint platformEndpoint();

    /** 端点是否可发起真实调用（有地址、有模型、有密钥）。 */
    boolean available(ModelEndpoint endpoint);

    /**
     * 一次聊天补全。失败抛 {@link ModelCallException}，由调用方决定降级，
     * 网关自身绝不返回编造内容。
     */
    ChatResult chat(ModelEndpoint endpoint, ChatRequest request);

    /** 真实探测能力：chat / structured / nativeTools 分别验证。 */
    CapabilityProbe probe(ModelEndpoint endpoint);

    /** 请求体（结构化输出与原生工具共用同一入口）。 */
    @Data
    class ChatRequest implements Serializable {
        private String system;
        private String user;
        private List<ChatTurn> history = new ArrayList<>();
        private Integer maxTokens;
        private Double temperature;
        /** true 时要求 JSON 对象输出（response_format=json_object）。 */
        private boolean jsonMode;
        /** 非空时按原生工具协议下发；模型不支持则调用方需退回结构化协议。 */
        private List<ToolSpec> tools = new ArrayList<>();

        public static ChatRequest of(String system, String user) {
            ChatRequest request = new ChatRequest();
            request.setSystem(system);
            request.setUser(user);
            return request;
        }
    }

    @Data
    class ChatTurn implements Serializable {
        private String role;
        private String content;

        public ChatTurn() {
        }

        public ChatTurn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    class ToolSpec implements Serializable {
        private String name;
        private String description;
        /** JSON Schema（对象），以 Map 表达避免额外依赖。 */
        private Map<String, Object> parameters;

        public ToolSpec() {
        }

        public ToolSpec(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
    }

    @Data
    class ChatResult implements Serializable {
        private String content;
        private String model;
        private long latencyMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String finishReason;
        private List<ToolCall> toolCalls = new ArrayList<>();

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    @Data
    class ToolCall implements Serializable {
        private String id;
        private String name;
        /** 原始参数 JSON 字符串；由编排器做 schema 校验，不合法可修复一次。 */
        private String argumentsJson;
    }

    @Data
    class CapabilityProbe implements Serializable {
        private boolean ok;
        private long latencyMs;
        private ModelCapabilities capabilities = ModelCapabilities.none();
        private String error;
    }

    /** 模型调用失败：调用方据此降级为 FALLBACK 或标记 FAILED_RETRYABLE。 */
    class ModelCallException extends RuntimeException {
        private final boolean retryable;

        public ModelCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public ModelCallException(String message, Throwable cause, boolean retryable) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
