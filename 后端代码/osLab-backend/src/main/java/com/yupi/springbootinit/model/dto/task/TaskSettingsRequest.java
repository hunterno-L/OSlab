package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;

/** 发布后允许的变更：截止时间、逾期开关、追加说明。 */
@Data
public class TaskSettingsRequest implements Serializable {
    private String dueAt;
    private Boolean allowLate;
    private String supplement;
    private Integer rowVersion;

    private static final long serialVersionUID = 1L;
}
