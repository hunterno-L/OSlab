package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目练习目录聚合 VO（阶段 4）：章节 -> 知识点 -> 题目数/完成数/待复习数。
 * 契约：
 * 1. 仅统计 PUBLISHED 且未逻辑删除的题目；
 * 2. 所有计数按 questionId 去重，一题映射多节点时在节点维度各计一次、章节维度只计一次；
 * 3. attempted/needsReview/correct 与 studyState 过滤同口径（本人最新有效作答）；
 * 4. 未作答数不下发，前端用 questionCount - attempted 计算；
 * 5. 不含任何答案/解析字段。
 */
@Data
public class LearningCatalogVO implements Serializable {

    /**
     * 去重后的题目总数（当前教材范围 + 学习状态过滤后的口径）
     */
    private Integer totalQuestions;

    /**
     * 稳定课程代码（未传课程范围时为空）
     */
    private String courseCode;

    /**
     * 旧章节聚合（兼容旧入口；新学生目录使用 sections/units）
     */
    private List<LearningCatalogChapterVO> chapters;

    /**
     * 教材小节树（稳定 sectionCode；仅课程范围请求时返回）
     */
    private List<LearningCatalogSectionVO> sections;

    /**
     * 当前范围内的知识节点及本人在该范围内的计数
     */
    private List<LearningCatalogNodeVO> nodes;

    /**
     * 当前范围内本人已有作答的题目数
     */
    private Integer attempted;

    /**
     * 当前范围内本人最新作答错误的题目数
     */
    private Integer needsReview;

    /**
     * 当前范围内本人最新作答正确的题目数
     */
    private Integer correct;
}
