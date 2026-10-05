package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 节点下钻单个测评题统计（Phase 8 §12.1）
 */
@Data
public class NodeDrillQuestionVO implements Serializable {

    /**
     * 普通题 id
     */
    private Long questionId;

    /**
     * 标题
     */
    private String title;

    /**
     * 题型
     */
    private String type;

    /**
     * 审核状态
     */
    private String reviewStatus;

    /**
     * 班级全部作答次数
     */
    private Integer attemptCount;

    /**
     * 班级首次有效作答数（分母）
     */
    private Integer firstAttemptCount;

    /**
     * 班级首次作答正确数（分子）
     */
    private Integer firstCorrectCount;

    /**
     * 首次正确率；无首次作答样本为 null（不显示 0%）
     */
    private Double firstCorrectRate;

    private static final long serialVersionUID = 1L;
}
