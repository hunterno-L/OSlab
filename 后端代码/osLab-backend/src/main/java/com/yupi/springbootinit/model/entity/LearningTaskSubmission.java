package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 每轮正式提交的冻结记录。 */
@TableName("learning_task_submission")
@Data
public class LearningTaskSubmission implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long recipientId;
    private Integer roundNo;
    private String submissionKey;
    private String submittedSnapshotJson;
    private String status;
    private Integer isLate;
    private BigDecimal autoScore;
    private BigDecimal manualScore;
    private BigDecimal totalScore;
    private String reviewJson;
    private Long reviewerId;
    private Date reviewedAt;
    private Date submittedAt;

    private static final long serialVersionUID = 1L;
}
