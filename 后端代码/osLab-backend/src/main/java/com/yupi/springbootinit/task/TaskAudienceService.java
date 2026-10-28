package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.mapper.StudentsMapper;
import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.dto.task.TaskAudienceRequest;
import com.yupi.springbootinit.model.entity.Students;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.UserRoleEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从 students 解析发布对象，去重并剔除无账号/封禁/已删除条目。 */
@Service
public class TaskAudienceService {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private StudentsMapper studentsMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private com.yupi.springbootinit.service.ClassManagementService classManagementService;

    public static class Resolved {
        private final List<Map<String, Object>> students = new ArrayList<>();
        private final List<Map<String, Object>> excluded = new ArrayList<>();

        public List<Map<String, Object>> getStudents() { return students; }
        public List<Map<String, Object>> getExcluded() { return excluded; }
        public int getValidCount() { return students.size(); }
    }

    public TaskAudienceRequest parse(String json) {
        if (StringUtils.isBlank(json)) return new TaskAudienceRequest();
        try {
            TaskAudienceRequest parsed = JSON.readValue(json, TaskAudienceRequest.class);
            return parsed == null ? new TaskAudienceRequest() : parsed;
        } catch (Exception e) {
            return new TaskAudienceRequest();
        }
    }

    public Resolved resolve(TaskAudienceRequest request) {
        Resolved result = new Resolved();
        if (request == null) return result;
        List<String> classNames = new ArrayList<>();
        if (request.getClassNames() != null) {
            for (String name : request.getClassNames()) {
                if (StringUtils.isNotBlank(name) && !classNames.contains(name.trim())) {
                    classNames.add(name.trim());
                }
            }
        }
        Set<Long> userIds = new HashSet<>();
        if (request.getUserIds() != null) {
            for (Long id : request.getUserIds()) if (id != null) userIds.add(id);
        }
        if (classNames.isEmpty() && userIds.isEmpty()) return result;

        LambdaQueryWrapper<Students> wrapper = new LambdaQueryWrapper<Students>()
                .eq(Students::getIsDelete, 0)
                .and(w -> {
                    boolean first = true;
                    if (!classNames.isEmpty()) {
                        w.in(Students::getClassName, classNames);
                        first = false;
                    }
                    if (!userIds.isEmpty()) {
                        if (!first) w.or();
                        w.in(Students::getUserId, userIds);
                    }
                })
                .orderByAsc(Students::getClassName)
                .orderByAsc(Students::getStudentNumber);
        List<Students> rows = studentsMapper.selectList(wrapper);

        Set<Long> foundUserIds = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        // Managed classes are the authoritative source for the new class workflow.
        // Keep the legacy students roster below so existing task recipients remain valid.
        for (Map<String, Object> managed : classManagementService.audienceRows(classNames, new ArrayList<>(userIds))) {
            Object rawUserId = managed.get("userId");
            if (!(rawUserId instanceof Number)) continue;
            long userId = ((Number) rawUserId).longValue();
            if (!seen.add(userId)) continue;
            foundUserIds.add(userId);
            Map<String, Object> student = new LinkedHashMap<>();
            student.put("userId", String.valueOf(userId));
            student.put("name", StringUtils.defaultString((String) managed.get("userName"), "学生"));
            student.put("studentNumber", null);
            student.put("className", managed.get("className"));
            student.put("studentId", null);
            result.students.add(student);
        }
        for (Students row : rows) {
            if (row.getUserId() == null) {
                result.excluded.add(excluded(row.getStudentNumber(), row.getName(), row.getClassName(),
                        "未绑定登录账号"));
                continue;
            }
            if (!seen.add(row.getUserId())) continue;
            foundUserIds.add(row.getUserId());
            User user = userMapper.selectById(row.getUserId());
            if (user == null || Integer.valueOf(1).equals(user.getIsDelete())) {
                result.excluded.add(excluded(row.getStudentNumber(), row.getName(), row.getClassName(),
                        "账号不存在或已删除"));
                continue;
            }
            if (UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                result.excluded.add(excluded(row.getStudentNumber(), row.getName(), row.getClassName(),
                        "账号已被封禁"));
                continue;
            }
            Map<String, Object> student = new LinkedHashMap<>();
            student.put("userId", String.valueOf(row.getUserId()));
            student.put("name", StringUtils.defaultIfBlank(user.getUserName(), row.getName()));
            student.put("studentNumber", row.getStudentNumber());
            student.put("className", row.getClassName());
            student.put("studentId", row.getId() == null ? null : String.valueOf(row.getId()));
            result.students.add(student);
        }
        for (Long selected : userIds) {
            if (!foundUserIds.contains(selected) && !containsExcludedUser(result, selected)) {
                result.excluded.add(excluded(null, null, null, "不在有效学生名册或所选班级中"));
            }
        }
        result.students.sort(Comparator
                .comparing((Map<String, Object> s) -> StringUtils.defaultString((String) s.get("className")))
                .thenComparing(s -> StringUtils.defaultString((String) s.get("studentNumber"))));
        return result;
    }

    private boolean containsExcludedUser(Resolved result, Long userId) {
        for (Map<String, Object> row : result.excluded) {
            if (String.valueOf(userId).equals(row.get("userId"))) return true;
        }
        return false;
    }

    public Map<String, Object> describe(TaskAudienceRequest request) {
        Resolved resolved = resolve(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("validCount", resolved.getValidCount());
        out.put("students", resolved.getStudents());
        out.put("excluded", resolved.getExcluded());
        out.put("excludedCount", resolved.getExcluded().size());
        return out;
    }

    private Map<String, Object> excluded(String studentNumber, String name, String className, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentNumber", studentNumber);
        row.put("name", name);
        row.put("className", className);
        row.put("reason", reason);
        return row;
    }

    public boolean hasSelection(TaskAudienceRequest request) {
        return request != null
                && (CollectionUtils.isNotEmpty(request.getClassNames()) || CollectionUtils.isNotEmpty(request.getUserIds()));
    }
}
