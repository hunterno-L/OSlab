package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * GraphRAG 引用来源 VO：每条来源必须能打开对应节点/题目/本人事件
 */
@Data
public class TutorSourceVO implements Serializable {

    /**
     * 来源类型：NODE（图谱节点）/ QUESTION（已发布题目）/ EVENT（本人学习事件）/ EXPERIMENT（实验题）
     */
    private String sourceType;

    /**
     * 来源 id（节点名为业务键；题目/事件为数字 id 字符串）
     */
    private String sourceId;

    /**
     * 来源标题
     */
    private String title;

    /**
     * 摘要片段
     */
    private String snippet;

    /**
     * 行动类型（阶段 5 §5.4，稳定枚举）：NODE/QUESTION/EXPERIMENT/EVENT/RESOURCE。
     * 前端据此跳转到节点工作台/题目/实验/学习记录/资源，而不是只显示不可点击的引用编号。
     */
    private String actionType;

    /**
     * 行动目标 id（视类型而定）：NODE=节点名；QUESTION/EXPERIMENT/EVENT=数字 id 字符串
     */
    private String targetId;

    /**
     * 行动目标 URL（可直跳的外链资源时提供；节点/题目等按 actionType+targetId 跳转时为空）
     */
    private String targetUrl;
}
