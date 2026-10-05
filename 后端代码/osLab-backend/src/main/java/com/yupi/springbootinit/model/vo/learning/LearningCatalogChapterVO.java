package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目练习目录-章节聚合 VO（阶段 4）
 */
@Data
public class LearningCatalogChapterVO implements Serializable {

    /**
     * 章节名（question.chapter 原值；null/空白归入"未归类"）
     */
    private String chapter;

    /**
     * 该章节内去重题目数（按 questionId）
     */
    private Integer questionCount;

    /**
     * 章节内知识点列表（按 nodeName 中文排序）
     */
    private List<LearningCatalogNodeVO> nodes;
}
