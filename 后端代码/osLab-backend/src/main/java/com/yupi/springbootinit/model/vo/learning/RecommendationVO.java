package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 学习推荐 VO（每条推荐必须显示原因）
 */
@Data
public class RecommendationVO implements Serializable {

    /**
     * 推荐的知识节点业务键
     */
    private String nodeName;

    /**
     * 推荐类型：WEAK_NODE（薄弱节点）/ PREREQUISITE（前置缺口）/ PRACTICE（练习巩固）
     */
    private String type;

    /**
     * 推荐原因（人类可读，必须展示）
     */
    private String reason;

    /**
     * 排序分（越高越优先）
     */
    private Double score;
}
