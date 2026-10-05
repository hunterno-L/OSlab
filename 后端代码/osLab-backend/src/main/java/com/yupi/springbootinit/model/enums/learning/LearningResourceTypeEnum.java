package com.yupi.springbootinit.model.enums.learning;

/**
 * 学习事件来源资源类型枚举（learning_event.resourceType）
 */
public enum LearningResourceTypeEnum {

    LEARNING_QUESTION("普通题"),
    QUESTION_SUBMIT("编程实验提交"),
    SIMULATION("模拟器练习"),
    /** 状态基线事件（Phase 7 §11.2）：不指向具体学习资源 */
    STATE_BASELINE("状态基线");

    private final String text;

    LearningResourceTypeEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
