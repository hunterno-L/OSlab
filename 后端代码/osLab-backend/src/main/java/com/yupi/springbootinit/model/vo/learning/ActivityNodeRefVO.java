package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动中的一个知识节点证据（同一活动可影响多个节点）。
 */
@Data
public class ActivityNodeRefVO implements Serializable {

    private String nodeCode;

    private String nodeName;

    private Double masteryBefore;

    private Double masteryAfter;

    private String result;

    /** 是否独立观测（首答/首判且非系统错误） */
    private Boolean independent;

    /** 对应学习事件 id，可用于历史快照回放 */
    private Long eventId;

    private static final long serialVersionUID = 1L;
}
