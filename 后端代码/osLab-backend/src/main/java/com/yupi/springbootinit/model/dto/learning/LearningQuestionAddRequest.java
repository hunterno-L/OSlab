package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 普通题创建请求（WP1）
 * answer 为 Object：JUDGE 传 "true"/"false"；SINGLE 传 "A"；
 * MULTIPLE 传 ["A","C"]；FILL_BLANK 传可接受答案数组。
 */
@Data
public class LearningQuestionAddRequest implements Serializable {

    /**
     * 标题
     */
    private String title;

    /**
     * 题干（支持 markdown）
     */
    private String content;

    /**
     * 题型：JUDGE/SINGLE/MULTIPLE/FILL_BLANK
     */
    private String type;

    /**
     * 选项 [{"key":"A","value":"..."}]，仅 SINGLE/MULTIPLE 必填
     */
    private List<QuestionOption> options;

    /**
     * 标准答案（仅作者/审核人可见）
     */
    private Object answer;

    /**
     * 解析
     */
    private String analysis;

    /**
     * 常见误区
     */
    private String misconception;

    /**
     * 所属章节
     */
    private String chapter;

    /**
     * 难度 1-5
     */
    private Integer difficulty;

    /**
     * 关联知识节点业务键列表（必须为图谱真实节点）
     */
    private List<String> nodeNames;

    @Data
    public static class QuestionOption implements Serializable {
        private String key;
        private String value;
    }
}
