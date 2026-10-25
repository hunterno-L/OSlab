package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 任务接收人及其当前状态。 */
@TableName("learning_task_recipient")
@Data
public class LearningTaskRecipient implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long userId;
    private String studentNumberSnapshot;
    private String classNameSnapshot;
    private String status;
    private Date acceptedAt;
    private Long latestSubmissionId;
    private Integer rowVersion;
    private Date createTime;
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
