package com.yupi.springbootinit.learning;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 普通题判分器测试（WP1 验收：四题型服务端判分正确；答案判分前不出现在响应）
 */
class LearningQuestionGraderTest {

    @Test
    void judge_trueFalse() {
        Assertions.assertTrue(LearningQuestionGrader.grade("JUDGE", "\"true\"", "true"));
        Assertions.assertTrue(LearningQuestionGrader.grade("JUDGE", "\"true\"", "  True "));
        Assertions.assertFalse(LearningQuestionGrader.grade("JUDGE", "\"true\"", "false"));
        Assertions.assertFalse(LearningQuestionGrader.grade("JUDGE", "\"true\"", null));
    }

    @Test
    void single_exactKeyMatch() {
        Assertions.assertTrue(LearningQuestionGrader.grade("SINGLE", "\"A\"", "A"));
        // 仅 trim，不改大小写
        Assertions.assertTrue(LearningQuestionGrader.grade("SINGLE", "\"A\"", "  A  "));
        Assertions.assertFalse(LearningQuestionGrader.grade("SINGLE", "\"A\"", "B"));
        // 大小写敏感（选项 key 精确匹配）
        Assertions.assertFalse(LearningQuestionGrader.grade("SINGLE", "\"A\"", "a"));
    }

    @Test
    void multiple_setEquality() {
        Assertions.assertTrue(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", Arrays.asList("A", "C")));
        Assertions.assertTrue(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", Arrays.asList("C", "A")));
        Assertions.assertFalse(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", Collections.singletonList("A")));
        Assertions.assertFalse(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", Arrays.asList("A", "C", "D")));
        // 逗号分隔字符串容错
        Assertions.assertTrue(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", "A,C"));
        Assertions.assertFalse(LearningQuestionGrader.grade("MULTIPLE", "[\"A\",\"C\"]", ""));
    }

    @Test
    void fillBlank_normalizedMatch() {
        // 多个可接受答案，命中任一即可
        Assertions.assertTrue(LearningQuestionGrader.grade("FILL_BLANK", "[\"页面置换算法\",\"置换算法\"]", "页面置换算法"));
        // 规范化：trim + 合并连续空白 + 大小写（空格合并但不会删除）
        Assertions.assertTrue(LearningQuestionGrader.grade("FILL_BLANK", "[\"LRU\"]", "  lru  "));
        Assertions.assertTrue(LearningQuestionGrader.grade("FILL_BLANK", "[\"最近 最 久未使用\"]", "最近  最 久未使用"));
        Assertions.assertFalse(LearningQuestionGrader.grade("FILL_BLANK", "[\"最近 最 久未使用\"]", "最近最久未使用"));
        Assertions.assertFalse(LearningQuestionGrader.grade("FILL_BLANK", "[\"LRU\"]", "FIFO"));
        Assertions.assertFalse(LearningQuestionGrader.grade("FILL_BLANK", "[\"LRU\"]", null));
    }

    @Test
    void invalidTypeOrEmptyInputsAreWrong() {
        Assertions.assertFalse(LearningQuestionGrader.grade("UNKNOWN", "\"A\"", "A"));
        Assertions.assertFalse(LearningQuestionGrader.grade("SINGLE", "", "A"));
        Assertions.assertFalse(LearningQuestionGrader.grade(null, "\"A\"", "A"));
    }

    @Test
    void normalize_rule() {
        Assertions.assertEquals("a b", LearningQuestionGrader.normalize("  A   B "));
        Assertions.assertEquals("", LearningQuestionGrader.normalize(null));
    }
}
