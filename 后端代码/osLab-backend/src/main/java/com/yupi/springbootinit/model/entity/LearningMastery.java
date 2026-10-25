package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 学生-节点掌握状态（个人图谱数字孪生）
 *
 * <p>conceptScore 由 ATTEMPT 事件驱动（概念理解），practiceScore 由 JUDGE/SIMULATION
 * 事件驱动（实践应用），pKnown 为综合 BKT 后验。status 推导规则见 MasteryStatusEnum。
 *
 * @TableName learning_mastery
 */
@TableName(value = "learning_mastery")
@Data
public class LearningMastery implements Serializable {
    private String nodeCode;

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
     * 知识节点业务键
     */
    private String nodeName;

    /**
     * 综合 BKT 掌握概率
     */
    private Double pKnown;

    /**
     * 可信度 0-1（证据量与来源种类）
     */
    private Double confidence;

    /**
     * 概念理解分 0-1（ATTEMPT 事件驱动）
     */
    private Double conceptScore;

    /**
     * 实践应用分 0-1（JUDGE/SIMULATION 事件驱动）
     */
    private Double practiceScore;

    /**
     * 概念证据数（独立观测）
     */
    private Integer conceptEvidenceCount;

    /**
     * 实践证据数（独立观测）
     */
    private Integer practiceEvidenceCount;

    /**
     * 状态：GRAY/RED/YELLOW/GREEN
     */
    private String status;

    /**
     * 最近一次事件 id
     */
    private Long lastEventId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
