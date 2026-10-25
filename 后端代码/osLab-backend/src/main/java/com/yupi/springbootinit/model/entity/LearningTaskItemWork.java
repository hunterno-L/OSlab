package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 某接收人某一轮次对某个内容块的工作草稿。 */
@TableName("learning_task_item_work")
@Data
public class LearningTaskItemWork implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long recipientId;
    private Long itemId;
    private Integer roundNo;
    private String answerJson;
    private String textResponse;
    private String fileIdsJson;
    private Long lastAttemptId;
    private Long lastCodeSubmissionId;
    private Integer rowVersion;
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
