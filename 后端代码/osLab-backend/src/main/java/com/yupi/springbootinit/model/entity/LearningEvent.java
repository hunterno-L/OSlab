package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 学习事件账本（不可变，只追加不修改）
 * @TableName learning_event
 */
@TableName(value = "learning_event")
@Data
public class LearningEvent implements Serializable {

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
     * 事件类型：ATTEMPT/JUDGE/SIMULATION
     */
    private String eventType;

    /**
     * 来源类型：LEARNING_QUESTION/QUESTION_SUBMIT/SIMULATION
     */
    private String resourceType;

    /**
     * 来源资源 id
     */
    private Long resourceId;

    /**
     * 结果：PASS/FAIL/SYSTEM_ERROR
     */
    private String result;

    /**
     * 是否独立观测（重复刷题只记录事件不重复计 BKT）0/1
     */
    private Integer isFirst;

    /**
     * 事件前 BKT 后验
     */
    private Double masteryBefore;

    /**
     * 事件后 BKT 后验
     */
    private Double masteryAfter;

    /**
     * 证据描述（JSON：来源、错因、摘要等）
     */
    private String evidence;

    /**
     * 幂等键
     */
    private String idempotencyKey;

    /**
     * 事件前完整掌握快照（JSON，Phase 7 §11.2；历史事件为 NULL）
     */
    private String snapshotBefore;

    /**
     * 事件后完整掌握快照（JSON，Phase 7 §11.2；历史事件为 NULL）
     */
    private String snapshotAfter;

    /**
     * 事件组键：attempt:{attemptId} / judge:{questionSubmitId} /
     * sim:{userId}:{simulationKey} / baseline:{userId}:{nodeName}
     */
    private String eventGroupKey;

    /**
     * 创建时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
