package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 普通题作答请求（服务端判分）
 * answer：JUDGE 传 "true"/"false"；SINGLE 传 "A"；MULTIPLE 传 ["A","C"]；FILL_BLANK 传字符串。
 */
@Data
public class LearningAnswerRequest implements Serializable {

    /**
     * 普通题 id
     */
    private Long questionId;

    /**
     * 学生提交的答案（JSON 反序列化后可能是 String 或 List）
     */
    private Object answer;

    /** 所属测评会话 id（旧入口不带该字段仍可用） */
    private Long assessmentRunId;

    /** 提交幂等键（同键重发服务端去重；主动重试使用新键） */
    private String submissionKey;
}
