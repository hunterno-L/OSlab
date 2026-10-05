package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 节点下钻单个编程实验统计（Phase 8 §12.1：按 questionSubmitId 去重、排除系统故障）
 */
@Data
public class NodeDrillExperimentVO implements Serializable {

    /**
     * 编程实验题 id（question 表）
     */
    private Long questionId;

    /**
     * 标题
     */
    private String title;

    /**
     * 班级有效提交数（终态 JUDGE 事件去重，排除系统故障）
     */
    private Integer submitCount;

    /**
     * 班级通过提交数
     */
    private Integer passCount;

    /**
     * 通过率；无有效提交为 null
     */
    private Double passRate;

    private static final long serialVersionUID = 1L;
}
