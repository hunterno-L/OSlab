package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 班级节点下钻聚合 VO（Phase 8 §12.1：映射表/标签精确关联，口径见字段注释）
 */
@Data
public class NodeDrillVO implements Serializable {

    /**
     * 班级名称
     */
    private String className;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 全班人数（students 表有效绑定）
     */
    private Integer studentCount;

    /**
     * 有证据人数（learning_mastery 存在该节点行）
     */
    private Integer evidenceCount;

    /**
     * 学习覆盖率（evidenceCount / studentCount；全班为 0 时为 0）
     */
    private Double coverage;

    /**
     * 薄弱人数（掌握状态 RED）
     */
    private Integer weakCount;

    /**
     * 平均掌握度；无证据为 null
     */
    private Double avgPKnown;

    /**
     * 首次测评正确率（分母=映射题首次有效作答数）；无样本为 null
     */
    private Double firstAccuracy;

    /**
     * 实验有效通过率（该节点 JUDGE 事件按提交去重、排除系统故障）；无样本为 null
     */
    private Double experimentPassRate;

    /**
     * 关联测评题（learning_question_node 映射表精确匹配，非标题模糊）
     */
    private List<NodeDrillQuestionVO> questions;

    /**
     * 关联编程实验（question.tags JSON 精确匹配）
     */
    private List<NodeDrillExperimentVO> experiments;

    /**
     * 错误类型（班级错误作答关联的常见误区，按次数降序）
     */
    private List<MisconceptionStatVO> misconceptionStats;

    private static final long serialVersionUID = 1L;
}
