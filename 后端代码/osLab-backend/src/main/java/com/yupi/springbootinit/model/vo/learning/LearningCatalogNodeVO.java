package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目练习目录-知识点聚合 VO（阶段 4）：计数为该章节内的节点局部口径
 */
@Data
public class LearningCatalogNodeVO implements Serializable {

    /**
     * 稳定知识节点代码（learning_question_node.nodeCode；旧映射可能为空）
     */
    private String nodeCode;

    /**
     * 知识节点存储键（来自 learning_question_node.nodeName）
     */
    private String nodeName;

    /**
     * 节点显示名（稳定代码解析；无绑定时退回 nodeName）
     */
    private String displayName;

    /**
     * 该章节内映射到该节点的去重题目数（按 questionId）
     */
    private Integer questionCount;

    /**
     * 本人已有作答的题目数
     */
    private Integer attempted;

    /**
     * 本人最新一次作答错误的题目数
     */
    private Integer needsReview;

    /**
     * 本人最新一次作答正确的题目数
     */
    private Integer correct;
}
