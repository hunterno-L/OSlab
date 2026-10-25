package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 教师布置的学习任务。 */
@TableName("learning_task")
@Data
public class LearningTask implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String taskCode;
    private Long creatorId;
    private String courseCode;
    private String unitCode;
    private String title;
    private String description;
    private String status;
    private String audienceJson;
    private String publishedPolicyJson;
    private Date dueAt;
    private Integer allowLate;
    private String supplementsJson;
    private Integer rowVersion;
    private Date publishedAt;
    private Date closedAt;
    private Integer isArchived;
    private Date createTime;
    private Date updateTime;
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
