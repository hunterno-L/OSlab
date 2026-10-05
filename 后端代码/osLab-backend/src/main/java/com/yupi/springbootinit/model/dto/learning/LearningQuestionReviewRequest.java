package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目审核/流转请求（作者提交、管理员审核、发布/下线）
 */
@Data
public class LearningQuestionReviewRequest implements Serializable {

    /**
     * 普通题 id
     */
    private Long questionId;

    /**
     * 动作：SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE
     */
    private String action;

    /**
     * 审核意见（REJECT 时必填）
     */
    private String reason;
}
