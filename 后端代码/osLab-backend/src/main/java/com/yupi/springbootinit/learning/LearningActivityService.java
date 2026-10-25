package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.course.CourseRepository;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningActivityRecordMapper;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.model.dto.learning.LearningSimulationRunRequest;
import com.yupi.springbootinit.model.entity.LearningActivityRecord;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.ActivityNodeRefVO;
import com.yupi.springbootinit.model.vo.learning.LearningActivityPageVO;
import com.yupi.springbootinit.model.vo.learning.LearningActivityVO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 学生学习活动记录：按“一次学习活动”聚合事件、任务、单元完成与普通演示运行。
 * 活动记录只描述发生过什么；掌握证据仍以 learning_event / 数字孪生为准。
 */
@Service
public class LearningActivityService {

    private static final Logger log = LoggerFactory.getLogger(LearningActivityService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_EVENT_ROWS = 5000;
    private static final int MAX_INPUT_JSON = 16 * 1024;
    private static final int MAX_RESULT_JSON = 4 * 1024;
    private static final Set<String> EVENT_ACTIVITY_TYPES = new HashSet<>(
            List.of("ATTEMPT", "JUDGE", "SIMULATION"));

    @Resource
    private LearningEventMapper learningEventMapper;

    @Resource
    private LearningActivityRecordMapper activityRecordMapper;

    @Resource
    private CourseRepository db;

    /**
     * 存量库可能尚未执行 v9 迁移：启动时幂等建表，避免学习记录页整页 500。
     * DDL 权限不足时只告警，不阻止应用启动；活动查询仍会做降级容错。
     */
    @PostConstruct
    void ensureActivityRecordTable() {
        try {
            db.update("CREATE TABLE IF NOT EXISTS learning_activity_record ("
                    + "id BIGINT NOT NULL PRIMARY KEY,"
                    + "userId BIGINT NOT NULL,"
                    + "activityType VARCHAR(32) NOT NULL,"
                    + "title VARCHAR(200) NOT NULL,"
                    + "algorithmKey VARCHAR(64) NULL,"
                    + "courseCode VARCHAR(128) NULL,"
                    + "unitCode VARCHAR(128) NULL,"
                    + "activityCode VARCHAR(128) NULL,"
                    + "inputJson JSON NULL,"
                    + "resultJson JSON NULL,"
                    + "occurredAt DATETIME NOT NULL,"
                    + "createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "KEY idx_activity_record_user_time (userId, occurredAt),"
                    + "KEY idx_activity_record_user_type (userId, activityType)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("[learning] learning_activity_record schema check failed; "
                    + "records will fall back to event timeline: {}", e.getMessage());
        }
    }

    /**
     * 保存普通算法演示的一次完整运行。纯演示不写掌握证据，也不影响 BKT。
     */
    public Long recordSimulationRun(User user, LearningSimulationRunRequest request) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "运行记录不能为空");
        }
        String algorithmKey = StringUtils.trimToEmpty(request.getAlgorithmKey());
        if (!algorithmKey.matches("^[a-z0-9._-]{2,64}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "算法 key 不合法");
        }
        String inputJson = request.getInput() == null ? null : request.getInput().toString();
        String resultJson = request.getResultSummary() == null ? null : request.getResultSummary().toString();
        if (inputJson != null && inputJson.length() > MAX_INPUT_JSON) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入快照过大");
        }
        if (resultJson != null && resultJson.length() > MAX_RESULT_JSON) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "结果摘要过大");
        }
        LearningActivityRecord record = new LearningActivityRecord();
        record.setUserId(user.getId());
        record.setActivityType("SIMULATION_RUN");
        record.setAlgorithmKey(algorithmKey);
        record.setTitle(StringUtils.defaultIfBlank(
                StringUtils.abbreviate(StringUtils.trimToEmpty(request.getTitle()), 200),
                algorithmKey));
        record.setCourseCode(trimToLength(request.getCourseCode(), 128));
        record.setUnitCode(trimToLength(request.getUnitCode(), 128));
        record.setActivityCode(trimToLength(request.getActivityCode(), 128));
        record.setInputJson(inputJson);
        record.setResultJson(resultJson);
        record.setOccurredAt(new Date());
        record.setCreateTime(new Date());
        activityRecordMapper.insert(record);
        return record.getId();
    }

    /**
     * 分页查询当前学生的活动记录。只接收当前登录用户，不接受外部 userId。
     */
    public LearningActivityPageVO activities(User user, String type, String courseCode,
                                             String unitCode, String nodeCode,
                                             String startTime, String endTime,
                                             long current, long pageSize) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        long safePageSize = Math.min(100, Math.max(1, pageSize));
        long safeCurrent = Math.max(1, current);
        String filterType = StringUtils.trimToEmpty(type).toUpperCase(Locale.ROOT);
        String filterCourse = StringUtils.trimToNull(courseCode);
        String filterUnit = StringUtils.trimToNull(unitCode);
        String filterNode = StringUtils.trimToNull(nodeCode);
        Date start = parseTime(startTime);
        Date end = parseTime(endTime);
        if (start != null && end != null && start.after(end)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }

        Map<String, String> unitToCourse = loadUnitCourses();
        Map<String, String> taskToCourse = new HashMap<>();
        Map<String, String> taskToUnit = new HashMap<>();
        loadTaskScopes(taskToCourse, taskToUnit);

        List<LearningActivityVO> activities = new ArrayList<>();
        boolean wantsEvents = filterType.isEmpty() || EVENT_ACTIVITY_TYPES.contains(filterType);
        boolean wantsRuns = filterType.isEmpty() || "SIMULATION_RUN".equals(filterType);
        boolean wantsTasks = filterType.isEmpty() || "TASK".equals(filterType);
        boolean wantsUnits = filterType.isEmpty() || "UNIT".equals(filterType);

        if (wantsEvents) {
            activities.addAll(eventActivities(user.getId(), filterType, filterNode,
                    filterCourse, filterUnit, start, end, unitToCourse, taskToCourse, taskToUnit));
        }
        if (wantsRuns && filterNode == null) {
            activities.addAll(runActivities(user.getId(), filterCourse, filterUnit, start, end));
        }
        if (wantsTasks && filterNode == null) {
            try {
                activities.addAll(taskActivities(user.getId(), filterCourse, filterUnit, start, end));
            } catch (RuntimeException e) {
                log.warn("[learning] task activities unavailable, keep other sources: {}", e.getMessage());
            }
        }
        if (wantsUnits && filterNode == null) {
            try {
                activities.addAll(unitActivities(user.getId(), filterCourse, filterUnit, start, end, unitToCourse));
            } catch (RuntimeException e) {
                log.warn("[learning] unit activities unavailable, keep other sources: {}", e.getMessage());
            }
        }

        activities.sort(Comparator
                .comparing(LearningActivityVO::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LearningActivityVO::getActivityId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        long total = activities.size();
        int from = (int) Math.min(total, (safeCurrent - 1) * safePageSize);
        int to = (int) Math.min(total, from + safePageSize);
        LearningActivityPageVO page = new LearningActivityPageVO();
        page.setTotal(total);
        page.setRecords(from >= to ? new ArrayList<>() : activities.subList(from, to));
        return page;
    }

    private List<LearningActivityVO> eventActivities(Long userId, String filterType, String nodeCode,
                                                     String courseCode, String unitCode,
                                                     Date start, Date end,
                                                     Map<String, String> unitToCourse,
                                                     Map<String, String> taskToCourse,
                                                     Map<String, String> taskToUnit) {
        LambdaQueryWrapper<LearningEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningEvent::getUserId, userId);
        wrapper.ne(LearningEvent::getEventType, "STATE_BASELINE");
        if (!filterType.isEmpty()) {
            wrapper.eq(LearningEvent::getEventType, filterType);
        }
        if (start != null) {
            wrapper.ge(LearningEvent::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(LearningEvent::getCreateTime, end);
        }
        wrapper.orderByDesc(LearningEvent::getCreateTime)
                .orderByDesc(LearningEvent::getId)
                .last("LIMIT " + MAX_EVENT_ROWS);
        List<LearningEvent> events = learningEventMapper.selectList(wrapper);
        if (events.isEmpty()) return new ArrayList<>();

        Map<String, List<LearningEvent>> groups = new LinkedHashMap<>();
        for (LearningEvent event : events) {
            String key = StringUtils.isNotBlank(event.getEventGroupKey())
                    ? "g:" + event.getEventGroupKey()
                    : "e:" + event.getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }
        String search = nodeCode == null ? null : nodeCode.toLowerCase(Locale.ROOT);
        List<LearningActivityVO> out = new ArrayList<>();
        for (Map.Entry<String, List<LearningEvent>> entry : groups.entrySet()) {
            List<LearningEvent> group = entry.getValue();
            if (search != null && group.stream().noneMatch(event -> matchesNode(event, search))) {
                continue;
            }
            if (!group.stream().anyMatch(event -> matchesScope(event, courseCode, unitCode,
                    unitToCourse, taskToCourse, taskToUnit))) {
                continue;
            }
            out.add(toEventActivity(entry.getKey(), group, unitToCourse, taskToCourse));
        }
        return out;
    }

    private boolean matchesNode(LearningEvent event, String search) {
        return StringUtils.defaultString(event.getNodeName()).toLowerCase(Locale.ROOT).contains(search)
                || StringUtils.defaultString(event.getNodeCode()).toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean matchesScope(LearningEvent event, String courseCode, String unitCode,
                                 Map<String, String> unitToCourse,
                                 Map<String, String> taskToCourse,
                                 Map<String, String> taskToUnit) {
        String eventUnit = StringUtils.trimToNull(event.getSourceUnitCode());
        String eventTask = StringUtils.trimToNull(event.getSourceTaskCode());
        String taskUnit = eventTask == null ? null : taskToUnit.get(eventTask);
        String taskCourse = eventTask == null ? null : taskToCourse.get(eventTask);
        if (unitCode != null
                && !unitCode.equals(eventUnit)
                && !unitCode.equals(taskUnit)) {
            return false;
        }
        if (courseCode != null) {
            String courseOfUnit = eventUnit == null ? null : unitToCourse.get(eventUnit);
            if (!courseCode.equals(courseOfUnit) && !courseCode.equals(taskCourse)) {
                return false;
            }
        }
        return true;
    }

    private LearningActivityVO toEventActivity(String groupKey, List<LearningEvent> group,
                                               Map<String, String> unitToCourse,
                                               Map<String, String> taskToCourse) {
        // group 按 createTime,id 倒序：首条为该活动最新事件，作为回放锚点。
        LearningEvent anchor = group.get(0);
        LearningActivityVO vo = new LearningActivityVO();
        vo.setActivityId("event:" + groupKey.substring(2));
        vo.setActivityType(anchor.getEventType());
        vo.setTitle(eventTitle(anchor));
        vo.setStatus(anchor.getResult());
        vo.setResult(anchor.getResult());
        vo.setOccurredAt(anchor.getCreateTime());
        for (LearningEvent event : group) {
            if (vo.getUnitCode() == null) vo.setUnitCode(StringUtils.trimToNull(event.getSourceUnitCode()));
            if (vo.getActivityCode() == null) vo.setActivityCode(StringUtils.trimToNull(event.getSourceActivityCode()));
            if (vo.getTaskCode() == null) vo.setTaskCode(StringUtils.trimToNull(event.getSourceTaskCode()));
            if (vo.getTaskItemCode() == null) vo.setTaskItemCode(StringUtils.trimToNull(event.getSourceTaskItemCode()));
            if (vo.getRoundNo() == null) vo.setRoundNo(event.getSourceTaskRound());
            if (vo.getResourceType() == null) vo.setResourceType(event.getResourceType());
            if (vo.getResourceId() == null) vo.setResourceId(event.getResourceId());
        }
        // 课程码由单元映射补全；事件本身不保存 courseCode。
        if (vo.getUnitCode() != null) {
            vo.setCourseCode(StringUtils.trimToNull(unitToCourse.get(vo.getUnitCode())));
        }
        if (vo.getCourseCode() == null && vo.getTaskCode() != null) {
            vo.setCourseCode(StringUtils.trimToNull(taskToCourse.get(vo.getTaskCode())));
        }
        vo.setSummary(evidenceSummary(anchor.getEvidence(), anchor.getEventType()));
        vo.setReplayEventId(anchor.getId());
        List<ActivityNodeRefVO> nodeRefs = new ArrayList<>();
        Set<String> seenNodes = new LinkedHashSet<>();
        List<Long> evidenceRefs = new ArrayList<>();
        for (LearningEvent event : group) {
            evidenceRefs.add(event.getId());
            String nodeKey = StringUtils.defaultIfBlank(event.getNodeCode(), event.getNodeName());
            if (!seenNodes.add(nodeKey)) continue;
            ActivityNodeRefVO ref = new ActivityNodeRefVO();
            ref.setNodeCode(StringUtils.trimToNull(event.getNodeCode()));
            ref.setNodeName(event.getNodeName());
            ref.setMasteryBefore(event.getMasteryBefore());
            ref.setMasteryAfter(event.getMasteryAfter());
            ref.setResult(event.getResult());
            ref.setIndependent(event.getIsFirst() != null && event.getIsFirst() == 1
                    && !"SYSTEM_ERROR".equals(event.getResult()));
            ref.setEventId(event.getId());
            nodeRefs.add(ref);
        }
        evidenceRefs.sort(Long::compareTo);
        vo.setNodeRefs(nodeRefs);
        vo.setEvidenceRefs(evidenceRefs);
        vo.setReturnRoute(eventReturnRoute(anchor));
        return vo;
    }

    private String eventTitle(LearningEvent event) {
        String title = evidenceField(event.getEvidence(), "title");
        if (StringUtils.isNotBlank(title)) return StringUtils.abbreviate(title, 200);
        if ("ATTEMPT".equals(event.getEventType())) return "题目练习 #" + event.getResourceId();
        if ("JUDGE".equals(event.getEventType())) return "提交 #" + event.getResourceId();
        if ("SIMULATION".equals(event.getEventType())) return "交互模拟";
        return "学习活动";
    }

    private String evidenceSummary(String evidence, String eventType) {
        String source = evidenceField(evidence, "source");
        String resultText = evidenceField(evidence, "resultText");
        String note = evidenceField(evidence, "note");
        String language = evidenceField(evidence, "language");
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(source)) parts.add(source);
        if (StringUtils.isNotBlank(resultText)) parts.add(resultText);
        if (StringUtils.isNotBlank(language)) parts.add(language);
        if (StringUtils.isNotBlank(note) && !note.equals(resultText)) parts.add(note);
        if (parts.isEmpty()) {
            return "SYSTEM_ERROR".equals(eventType) ? "系统错误" : "";
        }
        return StringUtils.abbreviate(String.join(" · ", parts), 500);
    }

    private String eventReturnRoute(LearningEvent event) {
        if ("ATTEMPT".equals(event.getEventType()) && event.getResourceId() != null) {
            // 同一活动可能关联多个节点，回跳只锚定题目，不猜测某个节点上下文。
            return "/assessments?questionId=" + event.getResourceId();
        }
        if ("SIMULATION".equals(event.getEventType())) {
            return "/experiments/simulation"
                    + (StringUtils.isNotBlank(event.getNodeName())
                    ? "?nodeName=" + encode(event.getNodeName()) : "");
        }
        // JUDGE 由记录页打开提交详情弹窗；不构造无法验证的详情路由。
        return null;
    }

    private List<LearningActivityVO> runActivities(Long userId, String courseCode, String unitCode,
                                                   Date start, Date end) {
        LambdaQueryWrapper<LearningActivityRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningActivityRecord::getUserId, userId)
                .eq(LearningActivityRecord::getActivityType, "SIMULATION_RUN");
        if (courseCode != null) wrapper.eq(LearningActivityRecord::getCourseCode, courseCode);
        if (unitCode != null) wrapper.eq(LearningActivityRecord::getUnitCode, unitCode);
        if (start != null) wrapper.ge(LearningActivityRecord::getOccurredAt, start);
        if (end != null) wrapper.le(LearningActivityRecord::getOccurredAt, end);
        wrapper.orderByDesc(LearningActivityRecord::getOccurredAt)
                .orderByDesc(LearningActivityRecord::getId);
        List<LearningActivityRecord> rows;
        try {
            rows = activityRecordMapper.selectList(wrapper);
        } catch (RuntimeException e) {
            // 表未迁移/权限不足时只丢弃普通运行记录，题目/判题/任务/单元活动继续返回。
            log.warn("[learning] activity record table unavailable, skip run records: {}",
                    e.getMessage());
            return new ArrayList<>();
        }
        List<LearningActivityVO> out = new ArrayList<>();
        for (LearningActivityRecord row : rows) {
            LearningActivityVO vo = new LearningActivityVO();
            vo.setActivityId("run:" + row.getId());
            vo.setActivityType("SIMULATION_RUN");
            vo.setTitle(row.getTitle());
            vo.setStatus("COMPLETED");
            vo.setResult("SIMULATION_RUN");
            vo.setOccurredAt(row.getOccurredAt());
            vo.setCourseCode(row.getCourseCode());
            vo.setUnitCode(row.getUnitCode());
            vo.setActivityCode(row.getActivityCode());
            vo.setAlgorithmKey(row.getAlgorithmKey());
            vo.setResourceType("SIMULATION_RUN");
            vo.setResourceId(row.getId());
            vo.setInputSnapshot(row.getInputJson());
            vo.setResultSnapshot(row.getResultJson());
            vo.setSummary("普通算法演示运行完成（不计掌握证据）");
            vo.setNodeRefs(new ArrayList<>());
            vo.setEvidenceRefs(new ArrayList<>());
            vo.setReturnRoute("/experiments/simulation?algorithmKey=" + encode(row.getAlgorithmKey()));
            out.add(vo);
        }
        return out;
    }

    private List<LearningActivityVO> taskActivities(Long userId, String courseCode, String unitCode,
                                                    Date start, Date end) {
        List<Map<String, Object>> rows = db.rows(
                "SELECT r.id recipientId,r.status recipientStatus,r.acceptedAt,"
                        + "t.taskCode,t.title taskTitle,"
                        + "t.courseCode,t.unitCode,s.id submissionId,s.roundNo,s.status submissionStatus,"
                        + "s.isLate,s.autoScore,s.manualScore,s.totalScore,s.submittedAt,s.reviewedAt,"
                        + "s.submittedSnapshotJson "
                        + "FROM learning_task_recipient r "
                        + "JOIN learning_task t ON t.id=r.taskId AND t.isDelete=0 "
                        + "LEFT JOIN learning_task_submission s ON s.recipientId=r.id "
                        + "WHERE r.userId=? ORDER BY r.id,s.roundNo",
                userId);
        List<LearningActivityVO> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String rowCourse = db.value(row, "courseCode");
            String rowUnit = db.value(row, "unitCode");
            if (courseCode != null && !courseCode.equals(rowCourse)) continue;
            if (unitCode != null && !unitCode.equals(rowUnit)) continue;
            Date acceptedAt = toDate(row.get("acceptedAt"));
            if (acceptedAt != null && inRange(acceptedAt, start, end)) {
                LearningActivityVO vo = new LearningActivityVO();
                vo.setActivityId("task-accept:" + db.value(row, "recipientId"));
                vo.setActivityType("TASK_ACCEPTED");
                vo.setTitle(db.value(row, "taskTitle") + " · 接受任务");
                vo.setStatus("ACCEPTED");
                vo.setResult("ACCEPTED");
                vo.setOccurredAt(acceptedAt);
                vo.setCourseCode(StringUtils.trimToNull(rowCourse));
                vo.setUnitCode(StringUtils.trimToNull(rowUnit));
                vo.setTaskCode(db.value(row, "taskCode"));
                vo.setResourceType("LEARNING_TASK");
                vo.setSummary(taskAcceptSummary(row));
                vo.setNodeRefs(new ArrayList<>());
                vo.setEvidenceRefs(new ArrayList<>());
                vo.setReturnRoute("/tasks/" + encode(db.value(row, "taskCode")));
                out.add(vo);
            }
            Date submittedAt = toDate(row.get("submittedAt"));
            if (submittedAt == null || !inRange(submittedAt, start, end)) continue;
            LearningActivityVO vo = new LearningActivityVO();
            vo.setActivityId("task-submit:" + db.value(row, "submissionId"));
            vo.setActivityType("TASK_SUBMISSION");
            vo.setTitle(db.value(row, "taskTitle") + " · 第 " + db.value(row, "roundNo") + " 轮提交");
            vo.setStatus(db.value(row, "submissionStatus"));
            vo.setResult(db.value(row, "submissionStatus"));
            vo.setOccurredAt(submittedAt);
            vo.setCourseCode(StringUtils.trimToNull(rowCourse));
            vo.setUnitCode(StringUtils.trimToNull(rowUnit));
            vo.setTaskCode(db.value(row, "taskCode"));
            vo.setRoundNo(parseInt(row.get("roundNo")));
            vo.setResourceType("LEARNING_TASK_SUBMISSION");
            vo.setResourceId(parseLong(row.get("submissionId")));
            vo.setSummary(taskSubmissionSummary(row));
            vo.setNodeRefs(new ArrayList<>());
            vo.setEvidenceRefs(new ArrayList<>());
            vo.setReturnRoute("/tasks/" + encode(db.value(row, "taskCode")));
            out.add(vo);
        }
        return out;
    }

    private List<LearningActivityVO> unitActivities(Long userId, String courseCode, String unitCode,
                                                    Date start, Date end,
                                                    Map<String, String> unitToCourse) {
        List<Map<String, Object>> rows = db.rows(
                "SELECT p.id,p.unitCode,p.activityCode,p.completed,p.passed,"
                        + "p.evidenceType,p.evidenceId,p.updateTime,u.courseCode,u.title unitTitle "
                        + "FROM learning_activity_progress p "
                        + "JOIN learning_unit u ON u.unitCode=p.unitCode AND u.isDelete=0 "
                        + "WHERE p.userId=? AND p.isDelete=0 AND p.completed=1 "
                        + "ORDER BY p.updateTime DESC",
                userId);
        List<LearningActivityVO> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String rowUnit = db.value(row, "unitCode");
            String rowCourse = db.value(row, "courseCode");
            if (StringUtils.isBlank(rowCourse)) {
                rowCourse = unitToCourse.getOrDefault(rowUnit, "");
            }
            if (courseCode != null && !courseCode.equals(rowCourse)) continue;
            if (unitCode != null && !unitCode.equals(rowUnit)) continue;
            Date occurredAt = toDate(row.get("updateTime"));
            if (!inRange(occurredAt, start, end)) continue;
            LearningActivityVO vo = new LearningActivityVO();
            vo.setActivityId("unit:" + db.value(row, "id"));
            vo.setActivityType("UNIT_COMPLETED");
            vo.setTitle(activityTitle(rowUnit, db.value(row, "activityCode"),
                    db.value(row, "unitTitle")));
            vo.setStatus("COMPLETED");
            vo.setResult("COMPLETED");
            vo.setOccurredAt(occurredAt);
            vo.setCourseCode(StringUtils.trimToNull(rowCourse));
            vo.setUnitCode(rowUnit);
            vo.setActivityCode(db.value(row, "activityCode"));
            vo.setResourceType("COURSE_ACTIVITY");
            vo.setSummary("课程活动已完成");
            vo.setNodeRefs(new ArrayList<>());
            vo.setEvidenceRefs(new ArrayList<>());
            vo.setReturnRoute("/courses/" + encode(rowCourse) + "/units/" + encode(rowUnit)
                    + "?activity=" + encode(db.value(row, "activityCode")));
            out.add(vo);
        }
        return out;
    }

    private String activityTitle(String unitCode, String activityCode, String unitTitle) {
        Map<String, Object> row = db.one(
                "SELECT publishedJson FROM learning_unit WHERE unitCode=? AND isDelete=0", unitCode);
        if (row != null && row.get("publishedJson") != null) {
            try {
                JsonNode root = MAPPER.readTree(String.valueOf(row.get("publishedJson")));
                for (JsonNode activity : root.path("activities")) {
                    if (activityCode.equals(activity.path("activityCode").asText())) {
                        String title = activity.path("title").asText("");
                        if (StringUtils.isNotBlank(title)) return title;
                    }
                }
            } catch (Exception ignored) {
                // 内容快照不可解析时使用稳定兜底标题，不回退猜测。
            }
        }
        return StringUtils.defaultIfBlank(unitTitle, "单元活动") + " · " + activityCode;
    }

    private String taskSubmissionSummary(Map<String, Object> row) {
        List<String> parts = new ArrayList<>();
        parts.add(taskStatusText(db.value(row, "submissionStatus")));
        String total = db.value(row, "totalScore");
        if (StringUtils.isNotBlank(total)) parts.add("得分 " + total);
        if ("1".equals(db.value(row, "isLate"))) parts.add("逾期提交");
        if (toDate(row.get("reviewedAt")) != null) parts.add("已批阅");
        List<String> fileNames = submissionFileNames(db.value(row, "submittedSnapshotJson"));
        if (!fileNames.isEmpty()) {
            parts.add("文件：" + String.join("、", fileNames));
        }
        return String.join(" · ", parts);
    }

    /** 只展示原文件名，不暴露任务文件存储 key/路径。 */
    private List<String> submissionFileNames(String snapshotJson) {
        Set<String> names = new LinkedHashSet<>();
        if (StringUtils.isBlank(snapshotJson)) return new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(snapshotJson);
            for (JsonNode item : root.path("items")) {
                for (JsonNode file : item.path("files")) {
                    String name = file.path("originalName").asText("");
                    if (StringUtils.isNotBlank(name)) names.add(name);
                    if (names.size() >= 5) return new ArrayList<>(names);
                }
            }
        } catch (Exception ignored) {
            // 历史快照不可解析时不展示文件，不影响活动本身。
        }
        return new ArrayList<>(names);
    }

    /** 接受记录只展示任务当前状态与草稿块数，不把每次草稿保存追加成新活动。 */
    private String taskAcceptSummary(Map<String, Object> row) {
        String summary = "已接受任务，当前状态："
                + taskStatusText(db.value(row, "recipientStatus"));
        List<Map<String, Object>> works = db.rows(
                "SELECT COUNT(*) workCount,MAX(roundNo) lastRound FROM learning_task_item_work "
                        + "WHERE recipientId=?", row.get("recipientId"));
        if (!works.isEmpty()) {
            Integer count = parseInt(works.get(0).get("workCount"));
            Integer round = parseInt(works.get(0).get("lastRound"));
            if (count != null && count > 0) {
                summary += "；第 " + (round == null ? 1 : round) + " 轮已有 "
                        + count + " 个内容块草稿";
            }
        }
        return summary;
    }

    private String taskStatusText(String status) {
        switch (StringUtils.defaultString(status)) {
            case "PENDING": return "待接受";
            case "IN_PROGRESS": return "进行中";
            case "SUBMITTED": return "已提交";
            case "RETURNED": return "已退回";
            case "REVIEWED": return "已批阅";
            default: return StringUtils.defaultIfBlank(status, "未知");
        }
    }

    private Map<String, String> loadUnitCourses() {
        Map<String, String> map = new HashMap<>();
        for (Map<String, Object> row : db.rows(
                "SELECT unitCode,courseCode FROM learning_unit WHERE isDelete=0")) {
            map.put(db.value(row, "unitCode"), db.value(row, "courseCode"));
        }
        return map;
    }

    private void loadTaskScopes(Map<String, String> courseMap, Map<String, String> unitMap) {
        for (Map<String, Object> row : db.rows(
                "SELECT taskCode,courseCode,unitCode FROM learning_task WHERE isDelete=0")) {
            courseMap.put(db.value(row, "taskCode"), db.value(row, "courseCode"));
            unitMap.put(db.value(row, "taskCode"), db.value(row, "unitCode"));
        }
    }

    private String evidenceField(String evidence, String field) {
        if (StringUtils.isBlank(evidence)) return "";
        try {
            JsonNode root = MAPPER.readTree(evidence);
            if (root.hasNonNull(field)) return root.get(field).asText("");
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private Date parseTime(String value) {
        if (StringUtils.isBlank(value)) return null;
        try {
            return cn.hutool.core.date.DateUtil.parse(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private boolean inRange(Date time, Date start, Date end) {
        if (time == null) return false;
        if (start != null && time.before(start)) return false;
        if (end != null && time.after(end)) return false;
        return true;
    }

    private Date toDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date) return (Date) value;
        try {
            return cn.hutool.core.date.DateUtil.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(StringUtils.defaultString(value),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.abbreviate(trimmed, maxLength);
    }
}
