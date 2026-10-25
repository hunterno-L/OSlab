package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;

/** 代码实验提交（任务上下文由服务端从路径补齐）。 */
@Data
public class TaskCodeSubmitRequest implements Serializable {
    private Integer roundNo;
    private String submissionKey;
    private String language;
    private String code;

    private static final long serialVersionUID = 1L;
}
