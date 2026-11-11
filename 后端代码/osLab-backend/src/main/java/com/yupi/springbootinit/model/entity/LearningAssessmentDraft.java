package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 整组自测草稿：只保存学生作答与“不确定”标记，不判分、不生成学习事件。
 *
 * @TableName learning_assessment_draft
 */
@TableName(value = "learning_assessment_draft")
@Data
public class LearningAssessmentDraft implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long runId;

    private Long userId;

    private Long questionId;

    /** 学生答案 JSON（string / string[] / null） */
    private String answerJson;

    private Integer markedUnsure;

    private Integer questionRevision;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
