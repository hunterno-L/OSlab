package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 节点下钻错误类型统计（Phase 8 §12.1：班级错误作答关联的题目常见误区）
 */
@Data
public class MisconceptionStatVO implements Serializable {

    /**
     * 常见误区描述（来自映射题 misconception）
     */
    private String misconception;

    /**
     * 班级错误作答次数
     */
    private Integer wrongCount;

    private static final long serialVersionUID = 1L;
}
