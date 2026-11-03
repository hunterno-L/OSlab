package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 冻结契约：{@code GET /learning/ai/next-actions?courseCode=&limit=5}
 * 响应 {@code {actions:[NextAction], generatedAt}}。
 *
 * <p>首页、图谱与导师共用同一份计划；每次 3–5 个任务并按 timeBudget 截断。
 */
@Data
public class NextActionsVO implements Serializable {

    private List<NextAction> actions = new ArrayList<>();

    /** 带明确时区的 ISO-8601。 */
    private String generatedAt;

    /** LEARN / REVIEW，说明本次计划的侧重。 */
    private String focus;

    /** 计划所用的会话 id 字符串；无会话时为 null。 */
    private String sessionId;

    /** 可用时间（分钟）；0 表示未设定。 */
    private Integer timeBudgetMinutes;

    /** 到期复习项数量，供首页"待复习"提醒。 */
    private Integer dueReviewCount;
}
