package com.yupi.springbootinit.model.dto.learning;

import com.yupi.springbootinit.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 普通题分页查询请求
 * 学生视角：仅返回 PUBLISHED；作者视角：myOnly=true 返回自己的题；
 * 审核队列：reviewQueue=true（管理员）返回 SUBMITTED/APPROVED 等非学生视角全量。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LearningQuestionQueryRequest extends PageRequest {

    /**
     * 标题关键词
     */
    private String title;

    /**
     * 题型
     */
    private String type;

    /**
     * 章节
     */
    private String chapter;

    /**
     * 审核状态
     */
    private String reviewStatus;

    /**
     * 知识节点业务键
     */
    private String nodeName;

    /**
     * 难度
     */
    private Integer difficulty;

    /**
     * 只看自己创建的题
     */
    private Boolean myOnly;

    /**
     * 审核队列模式（管理员）
     */
    private Boolean reviewQueue;
    /** 学习状态筛选：ALL/UNANSWERED/NEEDS_REVIEW/CORRECT（按当前用户最近有效作答，服务端过滤后再分页） */
    private String studyState;
}
