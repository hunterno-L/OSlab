package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 任务私有文件元数据；文件本体在私有存储目录。 */
@TableName("learning_task_file")
@Data
public class LearningTaskFile implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String itemCode;
    private Long recipientId;
    private Long ownerId;
    private String purpose;
    private String originalName;
    private String storageKey;
    private String ext;
    private String mime;
    private Long size;
    private String sha256;
    private Date createTime;
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
