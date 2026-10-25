package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 任务内容块；发布后执行副本与快照冻结。 */
@TableName("learning_task_item")
@Data
public class LearningTaskItem implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String itemCode;
    private Integer sortOrder;
    private String itemType;
    private String sourceMode;
    private Integer required;
    private BigDecimal maxScore;
    private String draftJson;
    private String publishedSnapshotJson;
    private Long runtimeResourceId;
    private String runtimeResourceType;
    private Date createTime;
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
