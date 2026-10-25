package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目练习目录聚合请求（阶段 4）：请求体可选。
 * 约定：
 * - 缺省/空体/空白 chapter：返回全部章节（旧字段，供兼容入口使用）；
 * - 新学生入口使用稳定 courseCode/sectionCode/unitCode 定位教材目录；
 * - studyState 与列表共用同一“本人最新有效作答”谓词，先过滤再计数；
 * - chapter 传"未归类"时返回章节为空（null/空白）的题目。
 */
@Data
public class LearningCatalogRequest implements Serializable {

    /**
     * 章节过滤（旧字段，可选）
     */
    private String chapter;

    /**
     * 稳定课程代码
     */
    private String courseCode;

    /**
     * 稳定小节代码（含后代小节）
     */
    private String sectionCode;

    /**
     * 稳定学习单元代码
     */
    private String unitCode;

    /**
     * 学习状态筛选：ALL/UNANSWERED/NEEDS_REVIEW/CORRECT
     */
    private String studyState;
}
