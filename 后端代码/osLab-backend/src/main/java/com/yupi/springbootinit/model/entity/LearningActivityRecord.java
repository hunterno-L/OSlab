package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 学生活动记录（与掌握证据账本分离）：当前承载普通算法演示的完整运行。
 */
@TableName("learning_activity_record")
@Data
public class LearningActivityRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** SIMULATION_RUN 等一般活动类型；不写入掌握证据 */
    private String activityType;

    private String title;

    private String algorithmKey;

    private String courseCode;

    private String unitCode;

    private String activityCode;

    /** 输入参数快照（JSON，结构化、有大小上限） */
    private String inputJson;

    /** 运行结果摘要（JSON，不含逐帧数据） */
    private String resultJson;

    private Date occurredAt;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
