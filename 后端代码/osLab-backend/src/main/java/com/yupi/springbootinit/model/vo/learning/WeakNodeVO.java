package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 待巩固节点 VO（Phase 7 §11.3 / 学习记录-待巩固页签）：
 * 薄弱（RED/YELLOW）节点 + 失败归类（当前失误/历史失误/已纠正/无失败）。
 */
@Data
public class WeakNodeVO implements Serializable {

    private String nodeName;

    /**
     * 所属章节（当前课程结构）
     */
    private String chapter;

    /**
     * RED / YELLOW
     */
    private String status;

    private Double pKnown;

    private Double conceptScore;

    private Double practiceScore;

    private Integer conceptEvidenceCount;

    private Integer practiceEvidenceCount;

    /**
     * 最近一次学习活动时间
     */
    private Date lastActivityTime;

    /**
     * 失败归类：CURRENT / HISTORICAL / CORRECTED / NONE
     */
    private String failureState;

    /**
     * 最近失败证据（含归因、依据强度标签；无失败为 null）
     */
    private LastFailureVO lastFailure;
}
