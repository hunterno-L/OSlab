package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 普通题服务端判分器（WP1）—— 纯函数，可独立测试。
 *
 * <p>判分规则（确定性，可复算）：
 * <ul>
 *   <li>JUDGE：答案为 "true"/"false"，提交与标准答案规范化（trim+小写）后相等；</li>
 *   <li>SINGLE：答案为选项 key 字符串（如 "A"），精确匹配；</li>
 *   <li>MULTIPLE：答案为 key 数组（如 ["A","C"]），集合完全相等；</li>
 *   <li>FILL_BLANK：答案为可接受答案数组，提交经规范化（trim、合并空白、小写）后命中任一即可。</li>
 * </ul>
 */
public final class LearningQuestionGrader {

    private LearningQuestionGrader() {
    }

    /**
     * 判分入口
     *
     * @param type       题型（LearningQuestionTypeEnum.name）
     * @param answerJson 标准答案的 JSON 字符串（learning_question.answer 列）
     * @param submitted  学生提交（String 或 List）
     * @return 是否答对；提交为空或格式不符一律判错
     */
    public static boolean grade(String type, String answerJson, Object submitted) {
        if (StringUtils.isAnyBlank(type, answerJson) || submitted == null) {
            return false;
        }
        switch (type) {
            case "JUDGE":
                return gradeJudge(answerJson, submitted);
            case "SINGLE":
                return gradeSingle(answerJson, submitted);
            case "MULTIPLE":
                return gradeMultiple(answerJson, submitted);
            case "FILL_BLANK":
                return gradeFillBlank(answerJson, submitted);
            default:
                return false;
        }
    }

    private static boolean gradeJudge(String answerJson, Object submitted) {
        String expected = parseString(answerJson);
        String actual = asString(submitted);
        if (expected == null || actual == null) {
            return false;
        }
        return normalize(expected).equals(normalize(actual));
    }

    private static boolean gradeSingle(String answerJson, Object submitted) {
        String expected = parseString(answerJson);
        String actual = asString(submitted);
        if (expected == null || actual == null) {
            return false;
        }
        return expected.trim().equals(actual.trim());
    }

    private static boolean gradeMultiple(String answerJson, Object submitted) {
        Set<String> expected = parseStringSet(answerJson);
        Set<String> actual = new HashSet<>();
        if (submitted instanceof List) {
            for (Object o : (List<?>) submitted) {
                if (o != null) {
                    actual.add(String.valueOf(o).trim());
                }
            }
        } else if (submitted instanceof String) {
            // 容错：逗号分隔字符串 "A,C"
            actual.addAll(Arrays.stream(((String) submitted).split("[,，]"))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return expected.equals(actual);
    }

    private static boolean gradeFillBlank(String answerJson, Object submitted) {
        String actual = asString(submitted);
        if (actual == null) {
            return false;
        }
        String normalized = normalize(asString(submitted));
        // 答案可为单个字符串或数组
        Set<String> accepted = new HashSet<>();
        String single = parseString(answerJson);
        if (single != null) {
            accepted.add(normalize(single));
        }
        accepted.addAll(parseStringSet(answerJson).stream()
                .map(LearningQuestionGrader::normalize).collect(Collectors.toSet()));
        return accepted.contains(normalized);
    }

    /**
     * 填空/判断规范化：trim、合并连续空白、转小写
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String asString(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static String parseString(String answerJson) {
        String s = answerJson.trim();
        // 受控数据：答案为 JSON 字符串字面量（带引号）或裸字符串
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Set<String> parseStringSet(String answerJson) {
        try {
            List<String> list = JSONUtil.toList(answerJson, String.class);
            if (list == null) {
                return new HashSet<>();
            }
            return list.stream().filter(StringUtils::isNotBlank)
                    .map(String::trim).collect(Collectors.toSet());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
