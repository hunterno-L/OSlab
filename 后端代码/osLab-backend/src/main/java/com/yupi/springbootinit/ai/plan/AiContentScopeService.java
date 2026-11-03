package com.yupi.springbootinit.ai.plan;

import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.LearningQuestionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 规划可引用的内容范围（计划 §5.6：只引用实际存在、可访问的单元和活动）。
 *
 * <p>单元与活动只取已发布：{@code reviewStatus='PUBLISHED'} 且 publishedJson 非空且未逻辑删除，
 * 与教材发布口径一致；草稿、下线与未发布单元不会进入下一步建议。
 *
 * <p>题目一律复用 {@link LearningQuestionService#listEligibleQuestionIds}，
 * 即学生口径的已发布谓词（含任务作用域过滤），不在这里另写一套可见性判断，
 * 避免规划器把越权或草稿题目推荐给学生。
 */
@Slf4j
@Service
public class AiContentScopeService {

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Data
    public static class UnitRef {
        private String unitCode;
        private String courseCode;
        private String sectionCode;
        private String title;
        private int estimatedMinutes;
        private int sortOrder;
    }

    @Data
    public static class ActivityRef {
        private String activityCode;
        private String unitCode;
        /** READING / QUESTION / SIMULATION / EXPERIMENT 等，取自内容包。 */
        private String activityType;
        /** QUESTION / SIMULATION / CHUNK / EXPERIMENT */
        private String referenceKind;
        private String referenceCode;
        private int sortOrder;
    }

    /** 某课程已发布单元（按 sortOrder 升序）。 */
    public List<UnitRef> publishedUnits(String courseCode) {
        if (StringUtils.isBlank(courseCode)) {
            return Collections.emptyList();
        }
        List<UnitRef> out = new ArrayList<>();
        try {
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT unitCode, courseCode, sectionCode, title, estimatedMinutes, sortOrder "
                            + "FROM learning_unit WHERE courseCode=? AND reviewStatus='PUBLISHED' "
                            + "AND publishedJson IS NOT NULL AND isDelete=0 ORDER BY sortOrder, id",
                    courseCode)) {
                UnitRef unit = new UnitRef();
                unit.setUnitCode(str(row.get("unitCode")));
                unit.setCourseCode(str(row.get("courseCode")));
                unit.setSectionCode(str(row.get("sectionCode")));
                unit.setTitle(str(row.get("title")));
                unit.setEstimatedMinutes(intValue(row.get("estimatedMinutes")));
                unit.setSortOrder(intValue(row.get("sortOrder")));
                out.add(unit);
            }
        } catch (RuntimeException e) {
            log.warn("[ai-scope] published units unavailable course={}: {}", courseCode, e.getClass().getSimpleName());
        }
        return out;
    }

    /** 某课程全部已发布单元下的活动。 */
    public List<ActivityRef> publishedActivities(String courseCode) {
        if (StringUtils.isBlank(courseCode)) {
            return Collections.emptyList();
        }
        List<ActivityRef> out = new ArrayList<>();
        try {
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT a.activityCode, a.unitCode, a.activityType, a.referenceKind, a.referenceCode, a.sortOrder "
                            + "FROM learning_unit_activity a JOIN learning_unit u ON u.unitCode=a.unitCode "
                            + "WHERE u.courseCode=? AND u.reviewStatus='PUBLISHED' AND u.publishedJson IS NOT NULL "
                            + "AND u.isDelete=0 AND a.isDelete=0 ORDER BY u.sortOrder, a.sortOrder",
                    courseCode)) {
                ActivityRef activity = new ActivityRef();
                activity.setActivityCode(str(row.get("activityCode")));
                activity.setUnitCode(str(row.get("unitCode")));
                activity.setActivityType(str(row.get("activityType")));
                activity.setReferenceKind(str(row.get("referenceKind")));
                activity.setReferenceCode(str(row.get("referenceCode")));
                activity.setSortOrder(intValue(row.get("sortOrder")));
                out.add(activity);
            }
        } catch (RuntimeException e) {
            log.warn("[ai-scope] published activities unavailable course={}: {}", courseCode,
                    e.getClass().getSimpleName());
        }
        return out;
    }

    /**
     * 课程标题：提示词必须由当前课程生成，不再写死"操作系统教师"身份。
     *
     * @return 标题；课程不存在或不可读时返回 null
     */
    public String courseTitle(String courseCode) {
        if (StringUtils.isBlank(courseCode)) {
            return null;
        }
        try {
            List<String> titles = jdbc.queryForList(
                    "SELECT title FROM course_catalog WHERE courseCode=? AND isDelete=0 LIMIT 1",
                    String.class, courseCode);
            return titles.isEmpty() ? null : titles.get(0);
        } catch (RuntimeException e) {
            log.warn("[ai-scope] course title unavailable course={}: {}", courseCode, e.getClass().getSimpleName());
            return null;
        }
    }

    public boolean isPublishedUnit(String unitCode) {
        if (StringUtils.isBlank(unitCode)) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM learning_unit WHERE unitCode=? AND reviewStatus='PUBLISHED' "
                            + "AND publishedJson IS NOT NULL AND isDelete=0", Integer.class, unitCode);
            return count != null && count > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 由图谱节点反查所属课程（只认已发布单元）。
     *
     * <p>旧入口 {@code /learning/tutor/ask} 的请求里没有 courseCode，
     * 但统一检索必须先限定课程，因此用节点归属反查；查不到就返回 null，
     * 由调用方退回既有检索路径，绝不猜一个课程去检索。
     */
    public String courseCodeForNode(String nodeCode) {
        if (StringUtils.isBlank(nodeCode)) {
            return null;
        }
        try {
            List<String> codes = jdbc.queryForList(
                    "SELECT u.courseCode FROM learning_unit_node n "
                            + "JOIN learning_unit u ON u.unitCode=n.unitCode "
                            + "WHERE n.nodeCode=? AND u.reviewStatus='PUBLISHED' AND u.isDelete=0 LIMIT 1",
                    String.class, nodeCode);
            return codes.isEmpty() ? null : codes.get(0);
        } catch (RuntimeException e) {
            log.warn("[ai-scope] course of node unavailable node={}: {}", nodeCode, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 平台当前唯一未删除的课程；多于一个时返回 null（不猜）。
     */
    public String singleCourseCode() {
        try {
            List<String> codes = jdbc.queryForList(
                    "SELECT courseCode FROM course_catalog WHERE isDelete=0 ORDER BY courseCode", String.class);
            return codes.size() == 1 ? codes.get(0) : null;
        } catch (RuntimeException e) {
            log.warn("[ai-scope] course catalog unavailable: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 学生口径的已发布题目 id（复用既有谓词，含任务作用域过滤）。
     *
     * @param nodeCode 可选，限定知识点
     */
    public List<Long> publishedQuestionIds(User user, String courseCode, String nodeCode, int limit) {
        if (user == null || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            LearningQuestionQueryRequest query = new LearningQuestionQueryRequest();
            query.setCourseCode(StringUtils.trimToNull(courseCode));
            query.setNodeCode(StringUtils.trimToNull(nodeCode));
            List<Long> ids = learningQuestionService.listEligibleQuestionIds(query, user);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            return ids.size() > limit ? new ArrayList<>(ids.subList(0, limit)) : ids;
        } catch (RuntimeException e) {
            log.warn("[ai-scope] eligible questions unavailable course={} node={}: {}",
                    courseCode, nodeCode, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
