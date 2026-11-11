package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/** 普通练习尝试上下文创建请求。 */
@Data
public class PracticeAttemptCreateRequest implements Serializable {

    private Long questionId;
    private String courseCode;
    private String sourceUnitCode;
    private String sourceActivityCode;
    /** 客户端生成的尝试幂等键：同键重发复用同一上下文。 */
    private String clientAttemptKey;
}
