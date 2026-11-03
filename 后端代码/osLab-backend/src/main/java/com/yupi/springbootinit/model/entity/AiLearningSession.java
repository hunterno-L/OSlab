package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 学习会话（v11）：课程作用域 + 目标 + 任务位置 + 计划 + 模型引用 + 版本。
 *
 * <p>持久化是硬要求：刷新、跳转、切换模型、服务重启后都要能解释地继续。
 * 摘要只用于压缩对话，不替代原始成绩。
 *
 * @TableName ai_learning_session
 */
@TableName(value = "ai_learning_session")
@Data
public class AiLearningSession implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String courseCode;

    /** 当前目标（自然语言，例如"能说明 LRU 的更新规则并预测一次换出"）。 */
    private String goal;

    /** LEARN / REVIEW —— 学习与复习共享同一套任务状态与证据。 */
    private String focus;

    /** 学生可用时间（分钟）；0 表示未设定。 */
    private Integer timeBudgetMinutes;

    private String unitCode;

    /** 任务位置（NextAction JSON），恢复"卡住→补学→返回原任务"。 */
    private String currentActionJson;

    /** 对话压缩摘要 JSON。 */
    private String summaryJson;

    /** NextAction[] 计划 JSON（计划 §6.3 允许计划先存 JSON）。 */
    private String planJson;

    /** 个人模型配置 id；null 表示平台默认。 */
    private Long modelProfileId;

    /** ACTIVE / CLOSED */
    private String status;

    /** 写操作乐观锁版本（契约 AiSession.rowVersion）。 */
    private Integer rowVersion;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
