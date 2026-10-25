package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 学习事件 VO（时间轴/证据展示）
 */
@Data
public class LearningEventVO implements Serializable {

    private Long id;

    private Long userId;

    /**
     * 匿名化展示名（教师大屏用，如“学生 7”）；学生本人查看时为 null
     */
    private String anonymousName;

    private String nodeName;
    private String nodeCode;
    private String sourceUnitCode;
    private String sourceActivityCode;

    private String eventType;

    private String resourceType;

    private Long resourceId;

    private String result;

    private Boolean isFirst;

    private Double masteryBefore;

    private Double masteryAfter;

    /**
     * 证据描述（JSON 字符串）
     */
    private String evidence;

    /**
     * 事件组键（§11.2）：同一学习事务影响多节点时共享，如 attempt:{attemptId}
     */
    private String eventGroupKey;

    private Date createTime;
}
