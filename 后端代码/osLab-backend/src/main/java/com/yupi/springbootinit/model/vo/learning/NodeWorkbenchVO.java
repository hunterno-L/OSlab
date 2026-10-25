package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 节点工作台聚合 VO（WP3）：概念卡、关联题目、事件证据、推荐
 */
@Data
public class NodeWorkbenchVO implements Serializable {

    /**
     * 节点掌握状态（可能为 null：无任何证据）
     */
    private MasteryNodeVO mastery;

    /**
     * 前置知识节点（图谱 DEPENDENT/相关 关系，名称列表）
     */
    private List<String> prerequisiteNodes;

    /**
     * 关联的已发布普通题
     */
    private List<LearningQuestionVO> questions;

    /**
     * 当前学生在该节点的学习事件（最近在前，上限 50 条）
     */
    private List<LearningEventVO> events;

    /**
     * 下一步推荐
     */
    private List<RecommendationVO> recommendations;

    // ---- Phase 2 契约（§6.3）----
    /** 前置节点（带状态） */
    private java.util.List<NodeRefVO> prerequisites;
    /** 后继节点（带状态） */
    private java.util.List<NodeRefVO> successors;
    /** 普通题统计 */
    private QuestionStatsVO questionStats;
    /** 常见误区（来自关联题目解析，去重 ≤3 条） */
    private java.util.List<String> misconceptions;
    /** 关联编程实验（含当前用户最近提交终态） */
    private java.util.List<ExperimentSummaryVO> experiments;
    /** 最近失败证据（指向真实资源 id） */
    private LastFailureVO lastFailure;
    /** 页面置换模拟器入口参数 */
    private SimulationEntryVO simulationEntry;
    /** 共享学习素材正文（仅 PUBLISHED；未配置为 null，页面回落动态模板） */
    private String contentMd;
    /** 共享素材关联资料 JSON 数组 [{title,url}]（仅 PUBLISHED） */
    private String resourceLinks;
    /** Published snapshots with sourceRef, assets and publication revision. */
    private List<java.util.Map<String,Object>> textbookChunks;
    private List<java.util.Map<String,Object>> learningUnits;
}
