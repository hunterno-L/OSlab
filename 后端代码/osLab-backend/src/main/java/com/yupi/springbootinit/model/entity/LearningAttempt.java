package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 普通题作答记录
 * @TableName learning_attempt
 */
@TableName(value = "learning_attempt")
@Data
public class LearningAttempt implements Serializable {

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
     * 作答用户 id
     */
    private Long userId;

    /**
     * 学生提交答案（JSON 字符串）
     */
    private String submittedAnswer;

    /**
     * 是否正确 0/1
     */
    private Integer isCorrect;

    /**
     * 是否该题首次作答 0/1
     */
    private Integer isFirst;

    /**
     * 是否已查看解析 0/1
     */
    private Integer hasViewedAnalysis;

    /**
     * 所属测评会话 id（旧数据/独立练习为 NULL）
     */
    private Long assessmentRunId;

    /**
     * 提交幂等键（userId 维度唯一；主动重试使用新键）
     */
    private String submissionKey;
    private String sourceUnitCode;
    private String sourceActivityCode;
    private Integer questionRevision;
    private String questionSnapshot;
    private String sourceTaskCode;
    private String sourceTaskItemCode;
    private Integer sourceTaskRound;
    /** 跨执行副本的稳定题目身份，用于重复观测去重 */
    private String evaluationIdentity;

    /**
     * 创建时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
