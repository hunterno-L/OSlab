package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 掌握度变化摘要（作答/判题/模拟预测后返回）
 */
@Data
public class MasteryChangeVO implements Serializable {

    /**
     * 知识节点业务键
     */
    private String nodeName;
    private String nodeCode;

    /**
     * 事件前 pKnown
     */
    private Double masteryBefore;

    /**
     * 事件后 pKnown
     */
    private Double masteryAfter;

    /**
     * 变化后状态：GRAY/GREEN/YELLOW/RED
     */
    private String status;

    /**
     * 本次是否作为独立 BKT 观测（重复刷题为 false）
     */
    private Boolean countedAsObservation;
}
