package com.yupi.springbootinit.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建请求
 */
@Data
public class QuestionSubmitAddRequest implements Serializable {
    private String sourceUnitCode;
    private String sourceActivityCode;
    /** 任务上下文；由任务接口服务端补齐，普通实验提交保持为空。 */
    private String sourceTaskCode;
    private String sourceTaskItemCode;
    private Integer sourceTaskRound;
    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;

}
