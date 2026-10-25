package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;

/** 退回修改；禁止逾期且已超期时必须提供新的截止时间。 */
@Data
public class TaskReturnRequest implements Serializable {
    private String reason;
    private String dueAt;

    private static final long serialVersionUID = 1L;
}
