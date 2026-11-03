package com.yupi.springbootinit.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Embedding 客户端（计划 §5.2）。
 *
 * <p>向量模型由平台维护：个人配置只切换生成模型，不触发全量重建索引，
 * 因此本接口只使用平台 {@code ai.ark.*} 端点，不接受个人 profile。
 *
 * <p>不可用时必须让检索诚实降级为关键词+图谱并标注 mode=FALLBACK，
 * 不得把降级路径包装成语义检索。
 */
public interface EmbeddingClient {

    /** 平台是否配置了可用的 embedding 模型。 */
    boolean available();

    /** 当前 embedding 模型标识；不可用时返回 null。 */
    String model();

    /**
     * 批量向量化。保持与入参等长同序。
     *
     * @throws EmbeddingUnavailableException 未配置或调用失败（调用方据此降级）
     */
    EmbeddingResult embed(List<String> texts);

    @Data
    class EmbeddingResult implements Serializable {
        private String model;
        private int dims;
        private List<float[]> vectors;
        private Integer promptTokens;
        private Integer totalTokens;
        private long latencyMs;
    }

    class EmbeddingUnavailableException extends RuntimeException {
        public EmbeddingUnavailableException(String message) {
            super(message);
        }

        public EmbeddingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
