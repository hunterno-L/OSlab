package com.yupi.springbootinit.model.enums.learning;

/**
 * 审核动作枚举（learning_review.action），契约 §3.5：
 * SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE
 */
public enum ReviewActionEnum {

    SUBMIT("提交审核"),
    APPROVE("审核通过"),
    REJECT("驳回"),
    PUBLISH("发布"),
    OFFLINE("下线");

    private final String text;

    ReviewActionEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static boolean isValid(String value) {
        for (ReviewActionEnum action : values()) {
            if (action.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
