package com.yupi.springbootinit.model.enums.learning;

/**
 * 普通题审核状态枚举
 * 状态机：DRAFT -> SUBMITTED -> APPROVED / REJECTED -> PUBLISHED
 * REJECTED 修改后可经 SUBMIT 回到 SUBMITTED；PUBLISHED 可下线回 DRAFT。
 */
public enum LearningReviewStatusEnum {

    DRAFT("草稿"),
    SUBMITTED("已提交待审核"),
    APPROVED("审核通过待发布"),
    REJECTED("已驳回"),
    PUBLISHED("已发布"),
    OFFLINE("已下线");

    private final String text;

    LearningReviewStatusEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * 学生可见（可作答）的状态：仅 PUBLISHED
     */
    public static boolean isStudentVisible(String status) {
        return PUBLISHED.name().equals(status);
    }
}
