package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识测评会话（Phase 4 §8.3）：队列固定在服务端，顺序与首次结果由作答记录读取。
 *
 * @TableName learning_assessment_run
 */
@TableName(value = "learning_assessment_run")
@Data
public class LearningAssessmentRun implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 学生用户 id
     */
    private Long userId;

    /**
     * 章节（可空=混合）
     */
    private String chapter;

    /**
     * 知识节点（可空=多节点）
     */
    private String nodeName;

    /**
     * 本轮题目队列 JSON（服务端固定顺序）
     */
    private String questionIdsJson;

    /**
     * 状态：ACTIVE/FINISHED
     */
    private String status;

    private Date createTime;

    private Date finishTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
