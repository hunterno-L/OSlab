package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;

/** 学生正式提交。 */
@Data
public class TaskSubmitRequest implements Serializable {
    private Integer roundNo;
    private String submissionKey;

    private static final long serialVersionUID = 1L;
}
