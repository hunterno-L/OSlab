package com.yupi.springbootinit.model.enums.learning;

/**
 * 学习事件结果枚举（learning_event.result）
 * SYSTEM_ERROR 表示平台基础设施故障，不计学生知识失误、不参与 BKT 更新。
 * COMPILE_ERROR 表示编译错误：仍是学生提交产生的诊断事件（供查看代码问题），
 * 但不是知识观测——不更新 BKT/独立证据/置信度（前后快照相同）。
 *
 * <p>观测性质（阶段 5 §5.3）：掌握度更新决策必须走 {@link #isKnowledgeObservation()}
 * 等显式分类方法，不得在调用方逐处比较结果字符串。
 */
public enum LearningResultEnum {

    PASS("通过", ObservationNature.KNOWLEDGE),
    FAIL("失败", ObservationNature.KNOWLEDGE),
    /** 编译错误：诊断事件，不计学生知识失误，不参与 BKT/证据/置信度更新 */
    COMPILE_ERROR("编译错误", ObservationNature.DIAGNOSTIC),
    SYSTEM_ERROR("系统错误", ObservationNature.DIAGNOSTIC),
    /** 状态基线（Phase 7 §11.2）：非通过/失败，不参与任何观测统计 */
    BASELINE("状态基线", ObservationNature.BASELINE);

    /**
     * 观测性质：KNOWLEDGE=独立知识观测（驱动 BKT/证据/置信度）；
     * DIAGNOSTIC=仅诊断（保留事件详情，不改变学习状态）；BASELINE=状态基线。
     */
    public enum ObservationNature {
        KNOWLEDGE, DIAGNOSTIC, BASELINE
    }

    private final String text;
    private final ObservationNature nature;

    LearningResultEnum(String text, ObservationNature nature) {
        this.text = text;
        this.nature = nature;
    }

    public String getText() {
        return text;
    }

    public ObservationNature getNature() {
        return nature;
    }

    /**
     * 是否为有效的独立知识观测（编译错误、系统错误、状态基线都不是）
     */
    public boolean isKnowledgeObservation() {
        return this.nature == ObservationNature.KNOWLEDGE;
    }

    /**
     * 是否为仅诊断事件（编译错误/系统错误：写事件与详情，不改变学习状态）
     */
    public boolean isDiagnostic() {
        return this.nature == ObservationNature.DIAGNOSTIC;
    }

    /**
     * 事件 result 字符串安全解析（空/未知返回 null，不抛异常）
     */
    public static LearningResultEnum parse(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
