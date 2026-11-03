package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 导师轮次（v11）：请求幂等键 + 执行状态 + 回答 + 来源/工具/诊断 JSON + 调用统计。
 *
 * <p>状态机：RUNNING → SUCCEEDED / FAILED_RETRYABLE / FAILED。
 * 进程重启后仍为 RUNNING 的轮次必须被标成可重试，不能永远挂着。
 *
 * @TableName ai_tutor_turn
 */
@TableName(value = "ai_tutor_turn")
@Data
public class AiTutorTurn implements Serializable {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String STATUS_FAILED = "FAILED";

    public static final String MODE_AI = "AI";
    public static final String MODE_FALLBACK = "FALLBACK";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    /** 幂等键：同一 (userId, sessionId, clientRequestId) 返回同一轮次。 */
    private String clientRequestId;

    private String status;

    private String message;

    private String answer;

    /** AI / FALLBACK —— 降级必须在响应与界面明确标注。 */
    private String mode;

    private String contextJson;

    /** SourceRef[] JSON */
    private String sourcesJson;

    /** ToolTraceEntry[] JSON：真实工具名、耗时、来源与核验结论 */
    private String toolTraceJson;

    /** Diagnosis JSON */
    private String diagnosisJson;

    /** NextAction[] JSON */
    private String nextActionsJson;

    /** usage JSON：{model,latencyMs,tokens} */
    private String usageJson;

    private Long modelProfileId;

    /** 脱敏后的模型标签，不含密钥。 */
    private String modelLabel;

    private Integer modelCalls;

    private Integer toolCalls;

    private Long latencyMs;

    private String errorMessage;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
