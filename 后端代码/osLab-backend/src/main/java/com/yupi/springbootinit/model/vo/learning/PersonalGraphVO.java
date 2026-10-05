package com.yupi.springbootinit.model.vo.learning;

import com.yupi.springbootinit.model.dto.knowledgegraph.CategoryDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 个人知识图谱 VO（WP3）：图谱结构 + 学生个性化掌握状态
 */
@Data
public class PersonalGraphVO implements Serializable {

    /**
     * 带掌握度状态的节点
     */
    private List<MasteryNodeVO> nodes;

    /**
     * 图谱边（复用现有 LinkDTO）
     */
    private List<LinkDTO> links;

    /**
     * 分类
     */
    private List<CategoryDTO> categories;

    // ---- Phase 2 契约（§6.2）----
    private Integer totalNodes;
    private Integer totalLinks;
    /** 有证据（非 GRAY）节点数 */
    private Integer evidenceCount;
    /** GRAY/RED/YELLOW/GREEN 计数 */
    private java.util.Map<String, Integer> statusCounts;
    /** 章节汇总 */
    private java.util.List<ChapterSummaryVO> chapters;
    /** 当前用户最近一次学习事件时间（无证据为 null） */
    private java.util.Date lastUpdatedAt;
    /** 推荐节点（薄弱节点中 pKnown 最低者） */
    private String recommendedNodeName;
}
