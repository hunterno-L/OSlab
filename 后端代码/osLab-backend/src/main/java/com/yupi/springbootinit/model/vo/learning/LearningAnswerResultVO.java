package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 普通题作答结果（提交后服务端判分返回；含解析与掌握度变化）。
 * 契约红线：本 VO 及其 JSON 中绝不出现标准答案或答案数组。
 */
@Data
public class LearningAnswerResultVO implements Serializable {

    /**
     * 作答记录 id
     */
    private Long attemptId;

    /**
     * 是否正确（契约 §3.2 响应字段名为 correct）
     */
    private Boolean correct;

    /**
     * 解析（判分后下发，解释正确结论；不携带答案字段）
     */
    private String analysis;

    /**
     * 常见误区
     */
    private String misconception;

    /**
     * 本次作答是否为该题首次作答
     */
    private Boolean isFirst;

    /**
     * 每个关联节点的掌握度变化
     */
    private List<MasteryChangeVO> masteryChanges;
}
