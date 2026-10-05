package com.yupi.springbootinit.model.enums.learning;

/**
 * 学习事件类型枚举（learning_event.eventType）
 */
public enum LearningEventTypeEnum {

    ATTEMPT("普通题作答"),
    JUDGE("实验判题终态"),
    SIMULATION("模拟器预测练习"),
    /** 状态基线（Phase 7 §11.2）：非学习观测，仅作为可信回放起点 */
    STATE_BASELINE("状态基线");

    private final String text;

    LearningEventTypeEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
