package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目练习目录-已发布学习单元题量（稳定 unitCode，按目录顺序）
 */
@Data
public class LearningCatalogUnitVO implements Serializable {

    private String unitCode;
    private String sectionCode;
    private String title;
    private Integer sortOrder;

    /**
     * 该单元正式题目集合（questionMappings ∪ QUESTION 活动）中当前范围内的去重题数
     */
    private Integer questionCount;
}
