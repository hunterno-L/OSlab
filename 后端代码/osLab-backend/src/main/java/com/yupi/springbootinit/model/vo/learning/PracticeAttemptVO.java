package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 普通练习尝试上下文：把“题卡 → 帮助 → 提交”绑定到同一服务端证据上下文。
 */
@Data
public class PracticeAttemptVO implements Serializable {

    private String attemptContextId;
    private String questionId;
    private Integer questionRevision;
    /** 提交幂等键：同一次尝试重发返回既有结果。 */
    private String submissionKey;
    /** NONE / HINT / ANSWER_REVEALED / UNKNOWN */
    private String helpLevel;
    private String status;
}
