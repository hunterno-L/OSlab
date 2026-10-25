package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.course.CourseRepository;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题目范围共享解析器：列表、catalog 与测评候选用同一套稳定代码语义。
 *
 * <p>单元到题目的正式关系只取已发布单元的 {@code questionMappings} 与
 * PRACTICE/ASSESSMENT + QUESTION 活动的并集；草稿表行与 usageSuggestions 不参与。
 * 小节范围按 course_section.parentSectionCode 递归后代计算。
 */
@Service
public class LearningQuestionScopeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int QUESTION_CODE_CHUNK = 500;

    @Resource
    private CourseRepository courseDb;

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private NodeIdentityService nodeIdentities;

    /** 单个已发布单元的正式题目集合（按 questionCode 去重后解析为题目 id）。 */
    @Getter
    public static class UnitQuestions {
        private final String unitCode;
        private final String sectionCode;
        private final String title;
        private final int sortOrder;
        private final Set<String> questionCodes = new LinkedHashSet<>();
        private final Set<Long> questionIds = new LinkedHashSet<>();

        UnitQuestions(String unitCode, String sectionCode, String title, int sortOrder) {
            this.unitCode = unitCode;
            this.sectionCode = sectionCode;
            this.title = title;
            this.sortOrder = sortOrder;
        }
    }

    /** 一次范围解析的结果；questionIds 为 null 表示没有目录/节点约束。 */
    @Getter
    public static class Scope {
        private final boolean constrained;
        private final boolean directoryConstrained;
        private final Set<Long> questionIds;
        private final String courseCode;
        private final Set<String> sectionCodes;
        private final List<UnitQuestions> units;
        /** 课程全部已发布单元（catalog 的完整目录计数使用）。 */
        private final List<UnitQuestions> allUnits;
        private final List<Map<String, Object>> sections;

        Scope(boolean constrained, boolean directoryConstrained, Set<Long> questionIds,
              String courseCode, Set<String> sectionCodes,
              List<UnitQuestions> units, List<UnitQuestions> allUnits,
              List<Map<String, Object>> sections) {
            this.constrained = constrained;
            this.directoryConstrained = directoryConstrained;
            this.questionIds = questionIds;
            this.courseCode = courseCode;
            this.sectionCodes = sectionCodes;
            this.units = units;
            this.allUnits = allUnits;
            this.sections = sections;
        }
    }

    public Scope resolve(String courseCode, String sectionCode, String unitCode, String nodeCode) {
        String course = trimToNull(courseCode);
        String section = trimToNull(sectionCode);
        String unit = trimToNull(unitCode);
        String node = trimToNull(nodeCode);
        boolean constrained = course != null || section != null || unit != null || node != null;
        if (!constrained) {
            return new Scope(false, false, null, null, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        if (section != null) {
            Map<String, Object> row = courseDb.one(
                    "SELECT sectionCode,courseCode FROM course_section WHERE sectionCode=? AND isDelete=0", section);
            if (row == null) return emptyScope(course);
            String rowCourse = courseDb.value(row, "courseCode");
            if (course != null && !course.equals(rowCourse)) return emptyScope(course);
            course = rowCourse;
        }

        JsonNode unitJson = null;
        if (unit != null) {
            Map<String, Object> row = courseDb.one(
                    "SELECT unitCode,courseCode,publishedJson FROM learning_unit WHERE unitCode=? AND publishedJson IS NOT NULL AND isDelete=0",
                    unit);
            if (row == null || row.get("publishedJson") == null) return emptyScope(course);
            String rowCourse = courseDb.value(row, "courseCode");
            if (course != null && !course.equals(rowCourse)) return emptyScope(course);
            course = rowCourse;
            unitJson = parse(courseDb.value(row, "publishedJson"));
        }

        List<Map<String, Object>> sections = course == null
                ? Collections.emptyList()
                : courseDb.rows("SELECT sectionCode,parentSectionCode,title,textbookNumber,sectionType,sortOrder "
                        + "FROM course_section WHERE courseCode=? AND isDelete=0 ORDER BY sortOrder,sectionCode", course);

        Set<String> sectionCodes = null;
        if (section != null) {
            sectionCodes = descendants(sections, section);
            if (sectionCodes.isEmpty()) sectionCodes.add(section);
        }
        if (unitJson != null && sectionCodes != null
                && !sectionCodes.contains(text(unitJson, "sectionCode"))) {
            return emptyScope(course);
        }

        List<UnitQuestions> allUnits = new ArrayList<>();
        if (course != null) {
            for (Map<String, Object> row : courseDb.rows(
                    "SELECT publishedJson FROM learning_unit WHERE courseCode=? AND publishedJson IS NOT NULL AND isDelete=0",
                    course)) {
                allUnits.add(toUnitQuestions(parse(courseDb.value(row, "publishedJson"))));
            }
            sortUnits(allUnits, sections);
        }
        resolveQuestionIds(allUnits);

        List<UnitQuestions> scopedUnits = new ArrayList<>();
        if (unit != null) {
            for (UnitQuestions candidate : allUnits) {
                if (candidate.getUnitCode().equals(unit)) {
                    scopedUnits.add(candidate);
                    break;
                }
            }
        } else if (course != null) {
            for (UnitQuestions candidate : allUnits) {
                if (sectionCodes == null || sectionCodes.contains(candidate.getSectionCode())) {
                    scopedUnits.add(candidate);
                }
            }
        }

        boolean directoryConstrained = course != null;
        Set<Long> allowed = null;
        if (directoryConstrained) {
            allowed = new LinkedHashSet<>();
            for (UnitQuestions candidate : scopedUnits) allowed.addAll(candidate.getQuestionIds());
        }
        if (node != null) {
            Set<Long> nodeQuestionIds = questionIdsForNode(node);
            if (allowed == null) allowed = new LinkedHashSet<>(nodeQuestionIds);
            else allowed.retainAll(nodeQuestionIds);
        }

        return new Scope(true, directoryConstrained, allowed, course, sectionCodes,
                scopedUnits, allUnits, sections);
    }

    private Scope emptyScope(String course) {
        return new Scope(true, true, Collections.emptySet(), course, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private UnitQuestions toUnitQuestions(JsonNode unit) {
        String unitCode = text(unit, "unitCode");
        String sectionCode = text(unit, "sectionCode");
        UnitQuestions result = new UnitQuestions(unitCode, sectionCode, text(unit, "title"),
                unit.path("sortOrder").asInt());
        for (JsonNode mapping : unit.path("questionMappings")) {
            String code = text(mapping, "questionCode");
            if (!code.isEmpty()) result.getQuestionCodes().add(code);
        }
        for (JsonNode activity : unit.path("activities")) {
            String type = text(activity, "activityType");
            if (!"PRACTICE".equals(type) && !"ASSESSMENT".equals(type)) continue;
            JsonNode reference = activity.path("reference");
            if (!"QUESTION".equals(text(reference, "kind"))) continue;
            String code = text(reference, "code");
            if (!code.isEmpty()) result.getQuestionCodes().add(code);
        }
        return result;
    }

    /** 把正式 questionCode 解析为当前仍发布且未删除的题目 id；草稿/下线题不会进入范围。 */
    private void resolveQuestionIds(List<UnitQuestions> units) {
        Set<String> codes = new LinkedHashSet<>();
        for (UnitQuestions unit : units) codes.addAll(unit.getQuestionCodes());
        Map<String, Long> idByCode = publishedQuestionIds(codes);
        for (UnitQuestions unit : units) {
            unit.getQuestionIds().clear();
            for (String code : unit.getQuestionCodes()) {
                Long id = idByCode.get(code);
                if (id != null) unit.getQuestionIds().add(id);
            }
        }
    }

    private Map<String, Long> publishedQuestionIds(Set<String> codes) {
        Map<String, Long> result = new HashMap<>();
        if (codes.isEmpty()) return result;
        List<String> list = new ArrayList<>(codes);
        for (int start = 0; start < list.size(); start += QUESTION_CODE_CHUNK) {
            List<String> chunk = list.subList(start, Math.min(start + QUESTION_CODE_CHUNK, list.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,questionCode FROM learning_question WHERE questionCode IN (" + placeholders
                            + ") AND reviewStatus='PUBLISHED' AND resourceScope='SHARED' AND isDelete=0", chunk.toArray());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                Object code = row.get("questionCode");
                if (id != null && code != null) {
                    result.put(String.valueOf(code), ((Number) id).longValue());
                }
            }
        }
        return result;
    }

    private Set<Long> questionIdsForNode(String nodeCodeOrName) {
        Set<Long> result = new LinkedHashSet<>();
        NodeIdentityService.Identity identity;
        try {
            identity = nodeIdentities.resolve(nodeCodeOrName);
        } catch (RuntimeException ignored) {
            return result;
        }
        if (identity == null) return result;
        Set<String> names = new LinkedHashSet<>();
        names.add(identity.name);
        names.add(identity.storageKey);
        names.add(nodeCodeOrName);
        List<String> nameList = new ArrayList<>(names);
        String placeholders = String.join(",", Collections.nCopies(nameList.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(identity.nodeCode);
        args.addAll(nameList);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT n.questionId FROM learning_question_node n "
                        + "JOIN learning_question q ON q.id=n.questionId "
                        + "WHERE q.reviewStatus='PUBLISHED' AND q.isDelete=0 AND q.resourceScope='SHARED' "
                        + "AND (n.nodeCode=? OR n.nodeName IN (" + placeholders + "))",
                args.toArray());
        for (Map<String, Object> row : rows) {
            Object id = row.get("questionId");
            if (id != null) result.add(((Number) id).longValue());
        }
        return result;
    }

    private Set<String> descendants(List<Map<String, Object>> sections, String root) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (Map<String, Object> row : sections) {
            String code = courseDb.value(row, "sectionCode");
            String parent = courseDb.value(row, "parentSectionCode");
            if (!code.isEmpty()) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(code);
        }
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (!result.add(code)) continue;
            List<String> childCodes = children.get(code);
            if (childCodes != null) queue.addAll(childCodes);
        }
        return result;
    }

    private void sortUnits(List<UnitQuestions> units, List<Map<String, Object>> sections) {
        Map<String, Integer> sectionOrder = new HashMap<>();
        int index = 0;
        for (Map<String, Object> row : sections) {
            sectionOrder.put(courseDb.value(row, "sectionCode"), index++);
        }
        units.sort(Comparator
                .comparingInt((UnitQuestions unit) -> sectionOrder.getOrDefault(unit.getSectionCode(), Integer.MAX_VALUE))
                .thenComparingInt(UnitQuestions::getSortOrder)
                .thenComparing(UnitQuestions::getUnitCode));
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(StringUtils.defaultIfBlank(json, "{}"));
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    private static String text(JsonNode node, String key) {
        return node == null ? "" : node.path(key).asText("");
    }

    private static String trimToNull(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed;
    }
}
