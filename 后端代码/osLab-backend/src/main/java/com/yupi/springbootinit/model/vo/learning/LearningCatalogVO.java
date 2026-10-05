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
     * 去重后的题目总数（= 返回各章节 questionCount 之和）
     */
    private Integer totalQuestions;

    /**
     * 章节列表（按章节名中文排序，"未归类"恒排最后）
     */
    private List<LearningCatalogChapterVO> chapters;
}
