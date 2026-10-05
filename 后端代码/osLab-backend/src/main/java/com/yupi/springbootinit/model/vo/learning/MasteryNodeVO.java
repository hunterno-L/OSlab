package com.yupi.springbootinit.model.vo.learning;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 个人图谱节点 VO：在图谱节点基础上叠加当前学生的掌握状态（契约 §3.3）
 */
@Data
public class MasteryNodeVO implements Serializable {

    /**
     * 节点 id（Neo4j 内部 id 字符串）
     */
    private String id;

    /**
     * 节点业务键（EntityNode.name）
     */
    private String name;

    /**
     * 分类（EntityNode.label）
     */
    private String category;

    /**
     * 所属章节（缺省"未分类"）
     */
    private String chapter;

    private String description;

    /**
     * 综合 BKT 掌握概率（无证据为 null）。
     * JavaBeans 陷阱命名：Lombok 生成的 getPKnown() 被 Jackson 推导为 "pknown"，
     * 与字段名 "pKnown" 不一致会导致同一对象输出两个键（PowerShell ConvertFrom-Json
     * 直接拒绝整个响应）。显式手写 getter 并固定 JSON 键名为 pKnown，@Data 不会再重复生成。
     */
    @JsonProperty("pKnown")
    private Double pKnown;

    /**
     * 显式 getter：与字段共用同一个 Jackson 属性 "pKnown"，避免 pknown/pKnown 双键。
     */
    @JsonProperty("pKnown")
    public Double getPKnown() {
        return pKnown;
    }

    /**
     * 可信度 0-1（无证据为 null）
     */
    private Double confidence;

    /**
     * 概念理解分 0-1（无证据为 null）
     */
    private Double conceptScore;

    /**
     * 实践应用分 0-1（无证据为 null）
     */
    private Double practiceScore;

    /**
     * 概念证据数
     */
    private Integer conceptEvidenceCount;

    /**
     * 实践证据数
     */
    private Integer practiceEvidenceCount;

    /**
     * 状态：GRAY/RED/YELLOW/GREEN
     */
    private String status;

    /**
     * 最近变化原因（来自最近一次事件的证据摘要）
     */
    private String recentReason;

    private Long lastEventId;

    /** 最近事件类型（ATTEMPT/JUDGE/SIMULATION/STATE_BASELINE） */
    private String lastEventType;
    /** 最近事件结果（PASS/FAIL/SYSTEM_ERROR/BASELINE） */
    private String lastResult;

    /**
     * 回放专用（§11.2）：true=有完整快照；false=该时点只有历史事件（细分状态未记录）；
     * null=非回放视图或该节点当时未学习。
     */
    private Boolean snapshotAvailable;
}
