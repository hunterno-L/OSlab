package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目审核记录
 * @TableName learning_review
 */
@TableName(value = "learning_review")
@Data
public class LearningReview implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 普通题 id
     */
    private Long questionId;

    /**
     * 操作人用户 id
     */
    private Long reviewerId;

    /**
     * 动作：SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE
     */
    private String action;

    /**
     * 审核意见（驳回必填）
     */
    private String reason;

    /**
     * 操作时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
