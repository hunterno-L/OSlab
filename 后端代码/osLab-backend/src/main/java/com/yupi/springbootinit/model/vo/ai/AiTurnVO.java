package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约：轮次响应（任务书 §3）。
 *
 * <p>{@code status=RUNNING} 时前端按 &ge;1.5s 间隔轮询 {@code GET /learning/ai/turns/{turnId}}；
 * {@code mode=FALLBACK} 表示 embedding 或模型不可用，界面必须能识别该降级，
 * 不得把降级结果当成语义检索/模型回答。
 */
@Data
public class AiTurnVO implements Serializable {

    private String turnId;

    /** RUNNING / SUCCEEDED / FAILED_RETRYABLE / FAILED */
    private String status;

    private String answer;

    /** AI / FALLBACK */
    private String mode;

    private List<SourceRef> sources = new ArrayList<>();

    private List<ToolTraceEntry> toolTrace = new ArrayList<>();

    private Diagnosis diagnosis;

    private List<NextAction> nextActions = new ArrayList<>();

    private UsageVO usage;

    /** 失败原因（可展示、不含密钥）。 */
    private String errorMessage;

    /** 服务端时间（带明确时区），用于前端排序与"生成于"展示。 */
    private String generatedAt;

    @Data
    public static class UsageVO implements Serializable {
        private String model;
        private Long latencyMs;
        private TokenUsageVO tokens;
        /** 本轮实际模型调用次数（预算 2）。 */
        private Integer modelCalls;
        /** 本轮实际工具执行次数（预算 3）。 */
        private Integer toolCalls;
    }

    @Data
    public static class TokenUsageVO implements Serializable {
        private Integer prompt;
        private Integer completion;
        private Integer total;
    }
}
