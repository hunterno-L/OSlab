package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningTaskItemMapper;
import com.yupi.springbootinit.mapper.LearningTaskMapper;
import com.yupi.springbootinit.mapper.LearningTaskRecipientMapper;
import com.yupi.springbootinit.mapper.LearningTaskSubmissionMapper;
import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.dto.task.TaskAudienceRequest;
import com.yupi.springbootinit.model.dto.task.TaskItemRequest;
import com.yupi.springbootinit.model.dto.task.TaskReturnRequest;
import com.yupi.springbootinit.model.dto.task.TaskReviewRequest;
import com.yupi.springbootinit.model.dto.task.TaskSaveRequest;
import com.yupi.springbootinit.model.dto.task.TaskSettingsRequest;
import com.yupi.springbootinit.model.dto.task.TaskVersionRequest;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import com.yupi.springbootinit.model.entity.LearningTaskRecipient;
import com.yupi.springbootinit.model.entity.LearningTaskSubmission;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.QuestionSubmitService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 教师任务：草稿、预览、名单、发布、发布后设置、批阅与退回。 */
@Service
public class TeacherTaskService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Resource
    private LearningTaskMapper taskMapper;
    @Resource
    private LearningTaskItemMapper itemMapper;
    @Resource
    private LearningTaskRecipientMapper recipientMapper;
    @Resource
    private LearningTaskSubmissionMapper submissionMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TaskContentService contentService;
    @Resource
    private TaskAudienceService audienceService;
    @Resource
    private TaskFileService fileService;
    @Resource
    private TaskAccessService access;
    @Resource
    private QuestionSubmitService questionSubmitService;
    @Resource
    private JdbcTemplate jdbc;

    // ---------------- 列表 / 详情 ----------------

    public Map<String, Object> list(User user, String title, String status, String className,
                                    boolean archived, long current, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 50);
        LambdaQueryWrapper<LearningTask> wrapper = new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getCreatorId, user.getId())
                .eq(LearningTask::getIsDelete, 0)
                .eq(LearningTask::getIsArchived, archived ? 1 : 0)
                .like(StringUtils.isNotBlank(title), LearningTask::getTitle, title)
                .eq(StringUtils.isNotBlank(status), LearningTask::getStatus, status)
                .orderByDesc(LearningTask::getUpdateTime);
        if (StringUtils.isNotBlank(className)) {
            Set<Long> taskIds = new HashSet<>();
            for (LearningTaskRecipient row : recipientMapper.selectList(
                    new LambdaQueryWrapper<LearningTaskRecipient>()
                            .eq(LearningTaskRecipient::getClassNameSnapshot, className.trim()))) {
                taskIds.add(row.getTaskId());
            }
            if (taskIds.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0);
                empty.put("current", current);
                empty.put("pageSize", size);
                return empty;
            }
            wrapper.in(LearningTask::getId, taskIds);
        }
        IPage<LearningTask> page = taskMapper.selectPage(new Page<>(Math.max(current, 1), size), wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (LearningTask task : page.getRecords()) records.add(summary(task));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", page.getTotal());
        out.put("current", page.getCurrent());
        out.put("pageSize", page.getSize());
        return out;
    }

    public Map<String, Object> get(User user, String taskCode) {
        LearningTask task = access.requireCreator(taskCode, user);
        Map<String, Object> out = detail(task);
        out.put("allowed", allowed(task));
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(User user, TaskSaveRequest request) {
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getTitle()),
                ErrorCode.PARAMS_ERROR, "任务标题不能为空");
        LearningTask task = new LearningTask();
        task.setId(IdWorker.getId());
        task.setTaskCode(newTaskCode());
        task.setCreatorId(user.getId());
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setCourseCode(blankToNull(request.getCourseCode()));
        task.setUnitCode(blankToNull(request.getUnitCode()));
        task.setStatus(TaskConstants.TASK_DRAFT);
        task.setDueAt(parseDate(request.getDueAt()));
        task.setAllowLate(Boolean.TRUE.equals(request.getAllowLate()) ? 1 : 0);
        task.setAudienceJson(writeAudience(request.getAudience()));
        task.setRowVersion(0);
        task.setIsArchived(0);
        task.setIsDelete(0);
        task.setSupplementsJson("[]");
        taskMapper.insert(task);
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            replaceItems(task, request.getItems());
        }
        return get(user, task.getTaskCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(User user, String taskCode, TaskSaveRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        ThrowUtils.throwIf(!TaskConstants.TASK_DRAFT.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "只有草稿可以完整修改，发布后请使用设置或复制新任务");
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getTitle()),
                ErrorCode.PARAMS_ERROR, "任务标题不能为空");
        checkVersion(task, request.getRowVersion());
        Date dueAt = parseDate(request.getDueAt());
        int expected = task.getRowVersion();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setCourseCode(blankToNull(request.getCourseCode()));
        task.setUnitCode(blankToNull(request.getUnitCode()));
        task.setDueAt(dueAt);
        task.setAllowLate(Boolean.TRUE.equals(request.getAllowLate()) ? 1 : 0);
        task.setAudienceJson(writeAudience(request.getAudience()));
        task.setRowVersion(expected + 1);
        task.setUpdateTime(null);
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, task.getId())
                .eq(LearningTask::getRowVersion, expected));
        ThrowUtils.throwIf(updated == 0, ErrorCode.OPERATION_ERROR, "任务已被其他编辑保存，请刷新");
        replaceItems(task, request.getItems());
        return get(user, taskCode);
    }

    private void replaceItems(LearningTask task, List<TaskItemRequest> requests) {
        itemMapper.delete(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, task.getId()));
        if (requests == null) return;
        Set<String> codes = new HashSet<>();
        int order = 0;
        for (TaskItemRequest request : requests) {
            ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "内容块不能为空");
            String itemCode = StringUtils.defaultIfBlank(request.getItemCode(), "item." + IdWorker.getId());
            itemCode = itemCode.replaceAll("[^A-Za-z0-9._-]", "_");
            ThrowUtils.throwIf(itemCode.length() > 64, ErrorCode.PARAMS_ERROR, "内容块代码过长");
            ThrowUtils.throwIf(!codes.add(itemCode), ErrorCode.PARAMS_ERROR, "内容块代码重复：" + itemCode);
            request.setItemCode(itemCode);
            boolean required = request.getRequired() == null || request.getRequired();
            request.setRequired(required);
            ObjectNode content = contentService.normalizeItem(request);
            validateTeacherAttachments(task, request, content);
            LearningTaskItem item = new LearningTaskItem();
            item.setId(IdWorker.getId());
            item.setTaskId(task.getId());
            item.setItemCode(itemCode);
            item.setSortOrder(order += 10);
            item.setItemType(request.getItemType());
            item.setSourceMode(StringUtils.defaultIfBlank(request.getSourceMode(),
                    TaskConstants.ITEM_QUESTION.equals(request.getItemType())
                            || TaskConstants.ITEM_EXPERIMENT.equals(request.getItemType())
                            ? TaskConstants.MODE_AUTHORED : null));
            item.setRequired(required ? 1 : 0);
            item.setMaxScore(request.getMaxScore());
            item.setDraftJson(content.toString());
            itemMapper.insert(item);
        }
    }

    private void validateTeacherAttachments(LearningTask task, TaskItemRequest request, ObjectNode content) {
        JsonNode files = content.path("files");
        if (!files.isArray()) return;
        List<Long> ids = new ArrayList<>();
        for (JsonNode file : files) {
            try {
                ids.add(Long.parseLong(file.path("fileId").asText()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "资料文件 id 无效");
            }
        }
        fileService.validateTeacherFiles(task.getId(), request.getItemCode(), ids);
    }

    // ---------------- 预览 / 名单 ----------------

    public Map<String, Object> audiencePreview(User user, String taskCode, TaskAudienceRequest request) {
        access.requireCreator(taskCode, user);
        TaskAudienceRequest selected = request == null
                ? audienceService.parse(access.task(taskCode).getAudienceJson()) : request;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audience", audienceService.describe(selected));
        return out;
    }

    public Map<String, Object> preview(User user, String taskCode) {
        LearningTask task = access.requireCreator(taskCode, user);
        List<LearningTaskItem> items = items(task.getId());
        List<Map<String, Object>> visible = new ArrayList<>();
        for (LearningTaskItem item : items) {
            LearningTaskItem view = new LearningTaskItem();
            view.setItemCode(item.getItemCode());
            view.setItemType(item.getItemType());
            view.setSortOrder(item.getSortOrder());
            view.setRequired(item.getRequired());
            view.setMaxScore(item.getMaxScore());
            view.setPublishedSnapshotJson(TaskConstants.TASK_DRAFT.equals(task.getStatus())
                    ? item.getDraftJson() : item.getPublishedSnapshotJson());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemType", item.getItemType());
            row.put("sortOrder", item.getSortOrder());
            row.put("required", item.getRequired() == 1);
            row.put("maxScore", item.getMaxScore());
            row.put("content", contentService.studentContent(view));
            visible.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", task.getTitle());
        out.put("description", task.getDescription());
        out.put("dueAt", formatDate(task.getDueAt()));
        out.put("allowLate", Integer.valueOf(1).equals(task.getAllowLate()));
        out.put("items", visible);
        out.put("audience", audienceService.describe(
                audienceService.parse(task.getAudienceJson())));
        return out;
    }

    // ---------------- 发布 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(User user, String taskCode, TaskVersionRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        if (TaskConstants.TASK_PUBLISHED.equals(task.getStatus())) {
            return get(user, taskCode);
        }
        ThrowUtils.throwIf(TaskConstants.TASK_CLOSED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "已关闭任务不能重新发布");
        checkVersion(task, request == null ? null : request.getRowVersion());
        List<LearningTaskItem> items = items(task.getId());
        ThrowUtils.throwIf(items.isEmpty(), ErrorCode.OPERATION_ERROR, "任务至少需要一个内容块");
        TaskAudienceService.Resolved resolved = audienceService.resolve(
                audienceService.parse(task.getAudienceJson()));
        ThrowUtils.throwIf(resolved.getValidCount() == 0, ErrorCode.OPERATION_ERROR,
                "没有有效的接收学生，不能发布");

        ObjectNode policy = JSON.createObjectNode();
        BigDecimal totalMaxScore = BigDecimal.ZERO;
        boolean scored = false;
        boolean manualRequired = false;
        ArrayNode policyItems = policy.putArray("items");
        for (LearningTaskItem item : items) {
            ObjectNode draft;
            try {
                draft = (ObjectNode) JSON.readTree(StringUtils.defaultIfBlank(item.getDraftJson(), "{}"));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务草稿内容损坏");
            }
            contentService.validateDraftForPublish(item, draft);
            validateTeacherAttachments(task, toItemRequest(item), draft);
            boolean manual = TaskContentService.manualRequired(item.getItemType(), draft);
            manualRequired |= manual;
            ObjectNode snapshot = contentService.createExecutionCopy(task, item, draft, user.getId());
            snapshot.put("itemCode", item.getItemCode());
            snapshot.put("itemType", item.getItemType());
            snapshot.put("sortOrder", item.getSortOrder());
            snapshot.put("required", item.getRequired() == 1);
            snapshot.put("manual", manual);
            if (item.getMaxScore() == null) snapshot.putNull("maxScore");
            else snapshot.put("maxScore", item.getMaxScore());
            item.setPublishedSnapshotJson(snapshot.toString());
            if (snapshot.hasNonNull("runtimeResourceId")) {
                item.setRuntimeResourceId(Long.parseLong(snapshot.path("runtimeResourceId").asText()));
                item.setRuntimeResourceType(snapshot.path("runtimeResourceType").asText());
            } else {
                item.setRuntimeResourceId(null);
                item.setRuntimeResourceType(null);
            }
            itemMapper.updateById(item);

            ObjectNode policyItem = policyItems.addObject();
            policyItem.put("itemCode", item.getItemCode());
            policyItem.put("itemType", item.getItemType());
            policyItem.put("title", draft.path("title").asText(""));
            policyItem.put("required", item.getRequired() == 1);
            policyItem.put("manual", manual);
            policyItem.put("requirePass", TaskContentService.requirePass(item.getItemType(), draft));
            if (item.getMaxScore() == null) policyItem.putNull("maxScore");
            else {
                policyItem.put("maxScore", item.getMaxScore());
                totalMaxScore = totalMaxScore.add(item.getMaxScore());
                scored = true;
            }
        }
        policy.put("totalMaxScore", totalMaxScore);
        policy.put("scored", scored);
        policy.put("manualRequired", manualRequired);

        for (Map<String, Object> student : resolved.getStudents()) {
            LearningTaskRecipient recipient = new LearningTaskRecipient();
            recipient.setId(IdWorker.getId());
            recipient.setTaskId(task.getId());
            recipient.setUserId(Long.valueOf(String.valueOf(student.get("userId"))));
            recipient.setStudentNumberSnapshot((String) student.get("studentNumber"));
            recipient.setClassNameSnapshot((String) student.get("className"));
            recipient.setStatus(TaskConstants.R_PENDING);
            recipient.setRowVersion(0);
            recipientMapper.insert(recipient);
        }

        int expected = task.getRowVersion();
        task.setStatus(TaskConstants.TASK_PUBLISHED);
        task.setPublishedPolicyJson(policy.toString());
        task.setPublishedAt(new Date());
        task.setRowVersion(expected + 1);
        task.setUpdateTime(null);
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, task.getId())
                .eq(LearningTask::getRowVersion, expected));
        ThrowUtils.throwIf(updated == 0, ErrorCode.OPERATION_ERROR, "任务已被其他编辑保存，请刷新");
        return get(user, taskCode);
    }

    private TaskItemRequest toItemRequest(LearningTaskItem item) {
        TaskItemRequest request = new TaskItemRequest();
        request.setItemCode(item.getItemCode());
        request.setItemType(item.getItemType());
        request.setSourceMode(item.getSourceMode());
        request.setRequired(item.getRequired() == 1);
        request.setMaxScore(item.getMaxScore());
        return request;
    }

    // ---------------- 发布后操作 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settings(User user, String taskCode, TaskSettingsRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "只有已发布任务可以调整设置");
        checkVersion(task, request.getRowVersion());
        Date dueAt = request.getDueAt() == null ? task.getDueAt() : parseDate(request.getDueAt());
        int expected = task.getRowVersion();
        task.setDueAt(dueAt);
        if (request.getAllowLate() != null) task.setAllowLate(request.getAllowLate() ? 1 : 0);
        if (StringUtils.isNotBlank(request.getSupplement())) {
            task.setSupplementsJson(appendSupplement(task.getSupplementsJson(), request.getSupplement()));
        }
        task.setRowVersion(expected + 1);
        task.setUpdateTime(null);
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, task.getId())
                .eq(LearningTask::getRowVersion, expected));
        ThrowUtils.throwIf(updated == 0, ErrorCode.OPERATION_ERROR, "任务已被其他编辑保存，请刷新");
        return get(user, taskCode);
    }

    private String appendSupplement(String json, String text) {
        ArrayNode array;
        try {
            JsonNode parsed = JSON.readTree(StringUtils.defaultIfBlank(json, "[]"));
            array = parsed.isArray() ? (ArrayNode) parsed : JSON.createArrayNode();
        } catch (Exception e) {
            array = JSON.createArrayNode();
        }
        ObjectNode entry = array.addObject();
        entry.put("content", text.trim());
        entry.put("createdAt", formatDate(new Date()));
        return array.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addRecipients(User user, String taskCode, TaskAudienceRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "只有已发布任务可以追加接收学生");
        TaskAudienceService.Resolved resolved = audienceService.resolve(request);
        List<LearningTaskRecipient> existing = recipientMapper.selectList(
                new LambdaQueryWrapper<LearningTaskRecipient>()
                        .eq(LearningTaskRecipient::getTaskId, task.getId()));
        Set<Long> existingIds = new HashSet<>();
        for (LearningTaskRecipient row : existing) existingIds.add(row.getUserId());
        int added = 0;
        int already = 0;
        for (Map<String, Object> student : resolved.getStudents()) {
            Long userId = Long.valueOf(String.valueOf(student.get("userId")));
            if (!existingIds.add(userId)) {
                already++;
                continue;
            }
            LearningTaskRecipient recipient = new LearningTaskRecipient();
            recipient.setId(IdWorker.getId());
            recipient.setTaskId(task.getId());
            recipient.setUserId(userId);
            recipient.setStudentNumberSnapshot((String) student.get("studentNumber"));
            recipient.setClassNameSnapshot((String) student.get("className"));
            recipient.setStatus(TaskConstants.R_PENDING);
            recipient.setRowVersion(0);
            recipientMapper.insert(recipient);
            added++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("already", already);
        out.put("excluded", resolved.getExcluded());
        out.put("total", existing.size() + added);
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(User user, String taskCode) {
        LearningTask task = access.requireCreator(taskCode, user);
        if (TaskConstants.TASK_CLOSED.equals(task.getStatus())) return get(user, taskCode);
        ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "只有已发布任务可以关闭");
        int expected = task.getRowVersion();
        task.setStatus(TaskConstants.TASK_CLOSED);
        task.setClosedAt(new Date());
        task.setRowVersion(expected + 1);
        task.setUpdateTime(null);
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, task.getId())
                .eq(LearningTask::getRowVersion, expected));
        ThrowUtils.throwIf(updated == 0, ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新");
        return get(user, taskCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> duplicate(User user, String taskCode) {
        LearningTask source = access.requireCreator(taskCode, user);
        LearningTask copy = new LearningTask();
        copy.setId(IdWorker.getId());
        copy.setTaskCode(newTaskCode());
        copy.setCreatorId(user.getId());
        copy.setCourseCode(source.getCourseCode());
        copy.setUnitCode(source.getUnitCode());
        copy.setTitle(source.getTitle() + "（副本）");
        copy.setDescription(source.getDescription());
        copy.setStatus(TaskConstants.TASK_DRAFT);
        copy.setAudienceJson(source.getAudienceJson());
        copy.setDueAt(source.getDueAt());
        copy.setAllowLate(source.getAllowLate());
        copy.setRowVersion(0);
        copy.setIsArchived(0);
        copy.setIsDelete(0);
        copy.setSupplementsJson("[]");
        taskMapper.insert(copy);
        for (LearningTaskItem item : items(source.getId())) {
            ObjectNode content;
            try {
                content = (ObjectNode) JSON.readTree(TaskConstants.TASK_DRAFT.equals(source.getStatus())
                        ? item.getDraftJson() : item.getPublishedSnapshotJson());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "任务内容损坏，无法复制");
            }
            content.remove("runtimeResourceId");
            content.remove("runtimeResourceType");
            content.remove("evaluationIdentity");
            LearningTaskItem clone = new LearningTaskItem();
            clone.setId(IdWorker.getId());
            clone.setTaskId(copy.getId());
            clone.setItemCode(item.getItemCode());
            clone.setSortOrder(item.getSortOrder());
            clone.setItemType(item.getItemType());
            clone.setSourceMode(item.getSourceMode());
            clone.setRequired(item.getRequired());
            clone.setMaxScore(item.getMaxScore());
            clone.setDraftJson(content.toString());
            itemMapper.insert(clone);
        }
        return get(user, copy.getTaskCode());
    }

    public Map<String, Object> archive(User user, String taskCode) {
        LearningTask task = access.requireCreator(taskCode, user);
        task.setIsArchived(1);
        task.setUpdateTime(null);
        taskMapper.updateById(task);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskCode", task.getTaskCode());
        out.put("archived", true);
        return out;
    }

    public Map<String, Object> deleteDraft(User user, String taskCode) {
        LearningTask task = access.requireCreator(taskCode, user);
        ThrowUtils.throwIf(!TaskConstants.TASK_DRAFT.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "已发布任务只能关闭或归档");
        task.setIsDelete(1);
        task.setUpdateTime(null);
        taskMapper.updateById(task);
        itemMapper.delete(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, task.getId()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", true);
        return out;
    }

    // ---------------- 接收人 / 批阅 ----------------

    public Map<String, Object> recipients(User user, String taskCode, long current, long pageSize) {
        LearningTask task = access.requireCreator(taskCode, user);
        long size = Math.min(Math.max(pageSize, 1), 100);
        long offset = (Math.max(current, 1) - 1) * size;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_task_recipient WHERE taskId=?", Long.class, task.getId());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id,r.userId,r.studentNumberSnapshot,r.classNameSnapshot,r.status,r.acceptedAt,"
                        + "r.latestSubmissionId,u.userName FROM learning_task_recipient r "
                        + "LEFT JOIN user u ON u.id=r.userId WHERE r.taskId=? "
                        + "ORDER BY r.classNameSnapshot,r.studentNumberSnapshot LIMIT ? OFFSET ?",
                task.getId(), size, offset);
        List<Map<String, Object>> records = new ArrayList<>();
        Date now = new Date();
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recipientId", String.valueOf(row.get("id")));
            record.put("userId", String.valueOf(row.get("userId")));
            record.put("name", row.get("userName") == null ? row.get("studentNumberSnapshot") : row.get("userName"));
            record.put("studentNumber", row.get("studentNumberSnapshot"));
            record.put("className", row.get("classNameSnapshot"));
            record.put("status", row.get("status"));
            record.put("acceptedAt", formatDate(toDate(row.get("acceptedAt"))));
            record.put("latestSubmissionId", row.get("latestSubmissionId") == null
                    ? null : String.valueOf(row.get("latestSubmissionId")));
            if (row.get("latestSubmissionId") != null) {
                LearningTaskSubmission submission = submissionMapper.selectById(
                        ((Number) row.get("latestSubmissionId")).longValue());
                if (submission != null) {
                    record.put("roundNo", submission.getRoundNo());
                    record.put("submittedAt", formatDate(submission.getSubmittedAt()));
                    record.put("isLate", Integer.valueOf(1).equals(submission.getIsLate()));
                    record.put("autoScore", submission.getAutoScore());
                    record.put("totalScore", submission.getTotalScore());
                    record.put("reviewStatus", submission.getStatus());
                }
            }
            boolean overdue = task.getDueAt() != null && now.after(task.getDueAt())
                    && !TaskConstants.R_SUBMITTED.equals(row.get("status"))
                    && !TaskConstants.R_REVIEWED.equals(row.get("status"));
            record.put("overdue", overdue);
            records.add(record);
        }
        Map<String, Object> stats = recipientStats(task.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("current", current);
        out.put("pageSize", size);
        out.put("stats", stats);
        return out;
    }

    private Map<String, Object> recipientStats(Long taskId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COUNT(*) total, SUM(status='PENDING') pending, SUM(status='IN_PROGRESS') inProgress,"
                        + " SUM(status='RETURNED') returned, SUM(status='SUBMITTED') submitted,"
                        + " SUM(status='REVIEWED') reviewed FROM learning_task_recipient WHERE taskId=?", taskId);
        return numbers(row);
    }

    public Map<String, Object> submissionDetail(User user, String taskCode, long submissionId) {
        LearningTask task = access.requireCreator(taskCode, user);
        LearningTaskSubmission submission = submissionMapper.selectById(submissionId);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR, "提交记录不存在");
        LearningTaskRecipient recipient = recipientMapper.selectById(submission.getRecipientId());
        ThrowUtils.throwIf(recipient == null || !task.getId().equals(recipient.getTaskId()),
                ErrorCode.NOT_FOUND_ERROR, "提交记录不属于该任务");
        ObjectNode snapshot;
        try {
            snapshot = (ObjectNode) JSON.readTree(StringUtils.defaultIfBlank(
                    submission.getSubmittedSnapshotJson(), "{}"));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "提交快照损坏");
        }
        ArrayNode items = snapshot.path("items").isArray()
                ? (ArrayNode) snapshot.path("items") : JSON.createArrayNode();
        for (JsonNode item : items) {
            ObjectNode row = (ObjectNode) item;
            if (row.path("codeSubmissionId").isTextual()) {
                QuestionSubmit code = questionSubmitService.getById(
                        Long.parseLong(row.path("codeSubmissionId").asText()));
                if (code != null) {
                    ObjectNode codeView = row.putObject("code");
                    codeView.put("id", String.valueOf(code.getId()));
                    codeView.put("language", code.getLanguage());
                    codeView.put("code", code.getCode());
                    codeView.put("status", code.getStatus());
                    codeView.put("judgeInfo", code.getJudgeInfo());
                }
            }
            List<Long> fileIds = new ArrayList<>();
            for (JsonNode fileId : row.path("fileIds")) {
                try {
                    fileIds.add(Long.parseLong(fileId.asText()));
                } catch (Exception ignored) {
                }
            }
            row.set("files", JSON.valueToTree(fileService.metadataList(fileIds)));
        }
        User student = userMapper.selectById(recipient.getUserId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", String.valueOf(submission.getId()));
        out.put("roundNo", submission.getRoundNo());
        out.put("status", submission.getStatus());
        out.put("isLate", Integer.valueOf(1).equals(submission.getIsLate()));
        out.put("autoScore", submission.getAutoScore());
        out.put("manualScore", submission.getManualScore());
        out.put("totalScore", submission.getTotalScore());
        out.put("submittedAt", formatDate(submission.getSubmittedAt()));
        out.put("reviewedAt", formatDate(submission.getReviewedAt()));
        out.put("review", parseObject(submission.getReviewJson()));
        out.put("items", items);
        out.put("student", studentMap(recipient, student));
        out.put("taskTitle", task.getTitle());
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> review(User user, String taskCode, long submissionId,
                                      TaskReviewRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        LearningTaskSubmission submission = submissionMapper.selectById(submissionId);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR, "提交记录不存在");
        LearningTaskRecipient recipient = recipientMapper.selectById(submission.getRecipientId());
        ThrowUtils.throwIf(recipient == null || !task.getId().equals(recipient.getTaskId()),
                ErrorCode.NOT_FOUND_ERROR, "提交记录不属于该任务");
        ThrowUtils.throwIf(!TaskConstants.R_SUBMITTED.equals(submission.getStatus()),
                ErrorCode.OPERATION_ERROR, "只有待批阅提交可以评分");
        ObjectNode snapshot = parseObject(submission.getSubmittedSnapshotJson());
        Map<String, ReviewTarget> targets = new LinkedHashMap<>();
        for (JsonNode item : snapshot.path("items")) {
            if (item.path("manual").asBoolean(false)) {
                ReviewTarget target = new ReviewTarget();
                target.itemCode = item.path("itemCode").asText();
                target.title = item.path("title").asText("");
                target.maxScore = item.path("maxScore").isNumber()
                        ? item.path("maxScore").decimalValue() : BigDecimal.ZERO;
                targets.put(target.itemCode, target);
            }
        }
        ThrowUtils.throwIf(targets.isEmpty(), ErrorCode.OPERATION_ERROR, "该提交没有需要人工评分的项目");
        Map<String, TaskReviewRequest.ReviewItem> submitted = new HashMap<>();
        if (request != null && request.getItems() != null) {
            for (TaskReviewRequest.ReviewItem item : request.getItems()) {
                if (item != null && StringUtils.isNotBlank(item.getItemCode())) {
                    submitted.put(item.getItemCode(), item);
                }
            }
        }
        ArrayNode reviewItems = JSON.createArrayNode();
        BigDecimal manualScore = BigDecimal.ZERO;
        for (ReviewTarget target : targets.values()) {
            TaskReviewRequest.ReviewItem item = submitted.get(target.itemCode);
            ThrowUtils.throwIf(item == null || item.getScore() == null,
                    ErrorCode.PARAMS_ERROR, "请为「" + target.title + "」填写分数");
            if (item.getScore().compareTo(BigDecimal.ZERO) < 0
                    || item.getScore().compareTo(target.maxScore) > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "「" + target.title + "」分数需在 0-" + target.maxScore + " 之间");
            }
            manualScore = manualScore.add(item.getScore());
            ObjectNode row = reviewItems.addObject();
            row.put("itemCode", target.itemCode);
            row.put("title", target.title);
            row.put("score", item.getScore());
            row.put("maxScore", target.maxScore);
            row.put("comment", item.getComment());
        }
        ObjectNode review = JSON.createObjectNode();
        review.set("items", reviewItems);
        review.put("overallComment", request == null ? null : request.getOverallComment());
        review.put("reviewerId", String.valueOf(user.getId()));
        review.put("reviewedAt", formatDate(new Date()));
        BigDecimal autoScore = submission.getAutoScore() == null ? BigDecimal.ZERO : submission.getAutoScore();
        submission.setManualScore(manualScore);
        submission.setTotalScore(autoScore.add(manualScore));
        submission.setReviewJson(review.toString());
        submission.setStatus(TaskConstants.R_REVIEWED);
        submission.setReviewerId(user.getId());
        submission.setReviewedAt(new Date());
        submissionMapper.updateById(submission);
        recipient.setStatus(TaskConstants.R_REVIEWED);
        recipientMapper.updateById(recipient);
        return submissionDetail(user, taskCode, submissionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> returnSubmission(User user, String taskCode, long submissionId,
                                                TaskReturnRequest request) {
        LearningTask task = access.requireCreator(taskCode, user);
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getReason()),
                ErrorCode.PARAMS_ERROR, "退回必须填写原因");
        LearningTaskSubmission submission = submissionMapper.selectById(submissionId);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR, "提交记录不存在");
        LearningTaskRecipient recipient = recipientMapper.selectById(submission.getRecipientId());
        ThrowUtils.throwIf(recipient == null || !task.getId().equals(recipient.getTaskId()),
                ErrorCode.NOT_FOUND_ERROR, "提交记录不属于该任务");
        ThrowUtils.throwIf(!TaskConstants.R_SUBMITTED.equals(submission.getStatus())
                        && !TaskConstants.R_REVIEWED.equals(submission.getStatus()),
                ErrorCode.OPERATION_ERROR, "当前状态不可退回");
        Date now = new Date();
        Date newDueAt = null;
        if (StringUtils.isNotBlank(request.getDueAt())) {
            newDueAt = parseDate(request.getDueAt());
            ThrowUtils.throwIf(newDueAt == null || newDueAt.before(now),
                    ErrorCode.PARAMS_ERROR, "新的截止时间必须晚于当前时间");
        } else if (access.late(task, now) && !Integer.valueOf(1).equals(task.getAllowLate())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "任务已超期且禁止逾期提交，退回时必须提供新的截止时间");
        }
        if (newDueAt != null) {
            task.setDueAt(newDueAt);
            task.setUpdateTime(null);
            taskMapper.updateById(task);
        }
        ObjectNode review = parseObject(submission.getReviewJson());
        review.put("returnReason", request.getReason().trim());
        review.put("returnedAt", formatDate(now));
        submission.setReviewJson(review.toString());
        submission.setStatus(TaskConstants.R_RETURNED);
        submissionMapper.updateById(submission);
        recipient.setStatus(TaskConstants.R_RETURNED);
        recipientMapper.updateById(recipient);
        return submissionDetail(user, taskCode, submissionId);
    }

    // ---------------- 视图工具 ----------------

    private LearningTaskItem requireItem(Long taskId, String itemCode) {
        LearningTaskItem item = itemMapper.selectOne(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, taskId)
                .eq(LearningTaskItem::getItemCode, itemCode)
                .last("limit 1"));
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "任务内容块不存在");
        return item;
    }

    private List<LearningTaskItem> items(Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, taskId)
                .orderByAsc(LearningTaskItem::getSortOrder));
    }

    private Map<String, Object> summary(LearningTask task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskCode", task.getTaskCode());
        out.put("title", task.getTitle());
        out.put("status", task.getStatus());
        out.put("dueAt", formatDate(task.getDueAt()));
        out.put("allowLate", Integer.valueOf(1).equals(task.getAllowLate()));
        out.put("publishedAt", formatDate(task.getPublishedAt()));
        out.put("closedAt", formatDate(task.getClosedAt()));
        out.put("isArchived", Integer.valueOf(1).equals(task.getIsArchived()));
        out.put("updateTime", formatDate(task.getUpdateTime()));
        out.put("itemCount", itemMapper.selectCount(new LambdaQueryWrapper<LearningTaskItem>()
                .eq(LearningTaskItem::getTaskId, task.getId())));
        ObjectNode policy = parseObject(task.getPublishedPolicyJson());
        if (policy.has("scored")) {
            out.put("scored", policy.path("scored").asBoolean(false));
            out.put("totalMaxScore", policy.path("totalMaxScore").decimalValue());
        } else {
            BigDecimal draftTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(maxScore),0) FROM learning_task_item WHERE taskId=?",
                    BigDecimal.class, task.getId());
            Long draftScored = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM learning_task_item WHERE taskId=? AND maxScore IS NOT NULL",
                    Long.class, task.getId());
            out.put("scored", draftScored != null && draftScored > 0);
            out.put("totalMaxScore", draftTotal == null ? BigDecimal.ZERO : draftTotal);
        }
        out.put("manualRequired", policy.path("manualRequired").asBoolean(false));
        long total = recipientMapper.selectCount(new LambdaQueryWrapper<LearningTaskRecipient>()
                .eq(LearningTaskRecipient::getTaskId, task.getId()));
        long submitted = recipientMapper.selectCount(new LambdaQueryWrapper<LearningTaskRecipient>()
                .eq(LearningTaskRecipient::getTaskId, task.getId())
                .in(LearningTaskRecipient::getStatus, TaskConstants.R_SUBMITTED, TaskConstants.R_REVIEWED));
        long pending = recipientMapper.selectCount(new LambdaQueryWrapper<LearningTaskRecipient>()
                .eq(LearningTaskRecipient::getTaskId, task.getId())
                .eq(LearningTaskRecipient::getStatus, TaskConstants.R_SUBMITTED));
        out.put("recipientCount", total);
        out.put("submittedCount", submitted);
        out.put("pendingReviewCount", pending);
        return out;
    }

    private Map<String, Object> detail(LearningTask task) {
        Map<String, Object> out = summary(task);
        out.put("taskStatus", task.getStatus());
        out.put("description", task.getDescription());
        out.put("courseCode", task.getCourseCode());
        out.put("unitCode", task.getUnitCode());
        out.put("rowVersion", task.getRowVersion());
        out.put("audience", parseObject(task.getAudienceJson()));
        out.put("audienceSummary", audienceService.describe(audienceService.parse(task.getAudienceJson())));
        out.put("supplements", parseArray(task.getSupplementsJson()));
        out.put("publishedPolicy", parseObject(task.getPublishedPolicyJson()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (LearningTaskItem item : items(task.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemType", item.getItemType());
            row.put("sourceMode", item.getSourceMode());
            row.put("sortOrder", item.getSortOrder());
            row.put("required", item.getRequired() == 1);
            row.put("maxScore", item.getMaxScore());
            row.put("runtimeResourceId", item.getRuntimeResourceId() == null
                    ? null : String.valueOf(item.getRuntimeResourceId()));
            row.put("content", parseObject(TaskConstants.TASK_DRAFT.equals(task.getStatus())
                    ? item.getDraftJson() : item.getPublishedSnapshotJson()));
            items.add(row);
        }
        out.put("items", items);
        return out;
    }

    private Map<String, Object> allowed(LearningTask task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canEdit", TaskConstants.TASK_DRAFT.equals(task.getStatus()));
        out.put("canPublish", TaskConstants.TASK_DRAFT.equals(task.getStatus()));
        out.put("canClose", TaskConstants.TASK_PUBLISHED.equals(task.getStatus()));
        out.put("canAddRecipients", TaskConstants.TASK_PUBLISHED.equals(task.getStatus()));
        out.put("canDuplicate", true);
        out.put("canArchive", Integer.valueOf(0).equals(task.getIsArchived()));
        out.put("canDelete", TaskConstants.TASK_DRAFT.equals(task.getStatus()));
        out.put("canReview", TaskConstants.TASK_PUBLISHED.equals(task.getStatus())
                || TaskConstants.TASK_CLOSED.equals(task.getStatus()));
        return out;
    }

    private Map<String, Object> studentMap(LearningTaskRecipient recipient, User student) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", String.valueOf(recipient.getUserId()));
        out.put("name", student == null ? recipient.getStudentNumberSnapshot() : student.getUserName());
        out.put("studentNumber", recipient.getStudentNumberSnapshot());
        out.put("className", recipient.getClassNameSnapshot());
        return out;
    }

    private Map<String, Object> numbers(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            out.put(entry.getKey(), value instanceof Number ? ((Number) value).longValue() : 0L);
        }
        return out;
    }

    private void checkVersion(LearningTask task, Integer requested) {
        ThrowUtils.throwIf(requested != null && !requested.equals(task.getRowVersion()),
                ErrorCode.OPERATION_ERROR, "任务已被其他编辑保存，请刷新");
    }

    private String writeAudience(TaskAudienceRequest audience) {
        try {
            return JSON.writeValueAsString(audience == null ? new TaskAudienceRequest() : audience);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发布对象格式无效");
        }
    }

    private ObjectNode parseObject(String json) {
        try {
            JsonNode node = JSON.readTree(StringUtils.defaultIfBlank(json, "{}"));
            return node instanceof ObjectNode ? (ObjectNode) node : JSON.createObjectNode();
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private ArrayNode parseArray(String json) {
        try {
            JsonNode node = JSON.readTree(StringUtils.defaultIfBlank(json, "[]"));
            return node instanceof ArrayNode ? (ArrayNode) node : JSON.createArrayNode();
        } catch (Exception e) {
            return JSON.createArrayNode();
        }
    }

    private String newTaskCode() {
        return "task." + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "."
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    public static Date parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;
        String text = value.trim();
        try {
            return Date.from(OffsetDateTime.parse(text).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间格式无效");
        }
    }

    public static String formatDate(Date date) {
        if (date == null) return null;
        return OUT.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static Date toDate(Object value) {
        if (value instanceof java.sql.Timestamp) return new Date(((java.sql.Timestamp) value).getTime());
        if (value instanceof java.time.LocalDateTime) {
            return Date.from(((java.time.LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }
        return value instanceof Date ? (Date) value : null;
    }

    private static final class ReviewTarget {
        String itemCode;
        String title;
        BigDecimal maxScore;
    }
}
