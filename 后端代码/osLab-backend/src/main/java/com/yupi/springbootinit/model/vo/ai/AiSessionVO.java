package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约 AiSession（任务书 §3）：
 * {@code sessionId, courseCode, goal, focus, timeBudgetMinutes, currentAction, modelProfileId, rowVersion}。
 *
 * <p>长整型 ID 在 JSON 中为字符串；写操作必须带 {@code rowVersion}，
 * 过期上下文应刷新后再执行。
 */
@Data
public class AiSessionVO implements Serializable {

    private String sessionId;

    private String courseCode;

    private String goal;

    /** LEARN / REVIEW */
    private String focus;

    private Integer timeBudgetMinutes;

    /** 当前任务位置；无则为 null。 */
    private NextAction currentAction;

    /** 个人配置 id 字符串；平台默认为 null。 */
    private String modelProfileId;

    private Integer rowVersion;

    private String status;

    private String unitCode;

    /** 服务端时间（带明确时区）。 */
    private String createdAt;

    private String updatedAt;
}
