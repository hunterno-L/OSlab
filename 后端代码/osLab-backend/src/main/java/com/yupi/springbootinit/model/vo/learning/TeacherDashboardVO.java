package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 教师班级大屏聚合 VO（WP5）：所有数值由真实学习事件/掌握度聚合而来，可与 SQL 对照
 */
@Data
public class TeacherDashboardVO implements Serializable {

    /**
     * 班级名称
     */
    private String className;

    /**
     * 班级学生数
     */
    private Integer studentCount;

    /**
     * 班级节点热力（节点 × 平均掌握度 × 薄弱人数）
     */
    private List<NodeHeatVO> nodeHeat;

    /**
     * 薄弱点 Top 5
     */
    private List<NodeHeatVO> weakTop;

    /**
     * 普通题题型正确率（按题聚合）
     */
    private List<QuestionAccuracyVO> questionAccuracy;

    /**
     * 实验有效通过率（JUDGE 事件按提交去重、排除系统故障）；无样本为 null
     */
    private Double experimentPassRate;

    /**
     * 最近学习事件（上限 20 条）
     */
    private List<LearningEventVO> recentEvents;
}
