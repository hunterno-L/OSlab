package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 普通题（与编程实验题 question 表分开存储）
 * @TableName learning_question
 */
@TableName(value = "learning_question")
@Data
public class LearningQuestion implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String questionCode;
    private String sectionCode;
    private Integer revision;
    /** SHARED（资源库）| TASK（任务私有执行副本） */
    private String resourceScope;
    private String ownerTaskCode;
    private String ownerTaskItemCode;

    /**
     * 标题
     */
    private String title;

    /**
     * 题干（支持 markdown）
     */
    private String content;

    /**
     * 题型：JUDGE/SINGLE/MULTIPLE/FILL_BLANK
     */
    private String type;

    /**
     * 选项（JSON 数组 [{"key":"A","value":"..."}]，仅 SINGLE/MULTIPLE）
     */
    private String options;

    /**
     * 标准答案（JSON；仅服务端判分使用，禁止下发学生）
     */
    private String answer;

    /**
     * 解析
     */
    private String analysis;

    /**
     * 常见误区
     */
    private String misconception;

    /**
     * 所属章节
     */
    private String chapter;

    /**
     * 难度 1-5
     */
    private Integer difficulty;

    /**
     * 审核状态：DRAFT/SUBMITTED/APPROVED/REJECTED/PUBLISHED
     */
    private String reviewStatus;

    /**
     * 作者用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
