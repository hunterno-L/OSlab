package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 章节资源完整度 VO（Phase 8 §12.2）
 */
@Data
public class ChapterCoverageVO implements Serializable {

    /**
     * 章节名称
     */
    private String chapter;

    /**
     * 章节知识节点数（图谱快照）
     */
    private Integer nodeCount;

    /**
     * 已发布素材节点数（learning_node_content PUBLISHED）
     */
    private Integer publishedContentCount;

    /**
     * 有已发布测评题的节点数（映射表精确匹配）
     */
    private Integer assessmentCoveredCount;

    /**
     * 有编程实验的节点数（tags JSON 精确匹配）
     */
    private Integer experimentCoveredCount;

    /**
     * 未关联资源节点（无素材、无测评、无实验），最多返回 20 个
     */
    private List<String> uncoveredNodes;

    /**
     * 未关联资源节点总数
     */
    private Integer uncoveredCount;

    private static final long serialVersionUID = 1L;
}
