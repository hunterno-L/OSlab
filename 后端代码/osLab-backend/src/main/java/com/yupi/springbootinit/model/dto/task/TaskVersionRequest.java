package com.yupi.springbootinit.model.dto.task;

import lombok.Data;

import java.io.Serializable;

/** 携带乐观锁版本的操作请求。 */
@Data
public class TaskVersionRequest implements Serializable {
    private Integer rowVersion;

    private static final long serialVersionUID = 1L;
}
