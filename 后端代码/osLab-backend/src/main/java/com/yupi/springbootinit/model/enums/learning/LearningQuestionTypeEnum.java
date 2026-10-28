package com.yupi.springbootinit.model.enums.learning;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 普通题题型枚举（WP0 契约冻结）
 */
public enum LearningQuestionTypeEnum {

    JUDGE("判断题"),
    SINGLE("单选题"),
    MULTIPLE("多选题"),
    FILL_BLANK("填空题"),
    COMPREHENSIVE("综合应用题");

    private final String text;

    LearningQuestionTypeEnum(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * 获取全部题型值列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
    }

    /**
     * 校验 value 是否为合法题型
     */
    public static boolean isValid(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(e -> e.name().equals(value));
    }
}
