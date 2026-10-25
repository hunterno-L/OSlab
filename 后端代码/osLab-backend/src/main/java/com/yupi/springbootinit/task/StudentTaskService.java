package com.yupi.springbootinit.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningTaskItemMapper;
import com.yupi.springbootinit.mapper.LearningTaskItemWorkMapper;
import com.yupi.springbootinit.mapper.LearningTaskMapper;
import com.yupi.springbootinit.mapper.LearningTaskRecipientMapper;
import com.yupi.springbootinit.mapper.LearningTaskSubmissionMapper;
import com.yupi.springbootinit.mapper.UserMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.yupi.springbootinit.model.dto.task.TaskAnswerRequest;
import com.yupi.springbootinit.model.dto.task.TaskCodeSubmitRequest;
import com.yupi.springbootinit.model.dto.task.TaskSubmitRequest;
import com.yupi.springbootinit.model.dto.task.TaskWorkSaveRequest;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningTask;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import com.yupi.springbootinit.model.entity.LearningTaskItemWork;
import com.yupi.springbootinit.model.entity.LearningTaskRecipient;
import com.yupi.springbootinit.model.entity.LearningTaskSubmission;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.enums.QuestionSubmitStatusEnum;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 学生任务：列表、接收、保存、真实作答/判题与正式提交。 */
@Service
public class StudentTaskService {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private LearningTaskMapper taskMapper;
    @Resource
    private LearningTaskItemMapper itemMapper;
    @Resource
    private LearningTaskItemWorkMapper workMapper;
    @Resource
    private LearningTaskRecipientMapper recipientMapper;
    @Resource
    private LearningTaskSubmissionMapper submissionMapper;
    @Resource
    private LearningAttemptMapper attemptMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TaskAccessService access;
    @Resource
    private TaskContentService contentService;
    @Resource
    private TaskFileService fileService;
    @Resource
    private LearningQuestionService learningQuestionService;
    @Resource
    private QuestionSubmitService questionSubmitService;
    @Resource
    private JdbcTemplate jdbc;

    // ---------------- 列表 / 详情 ----------------

    public Map<String, Object> list(User user, String status, String keyword, long current, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 50);
        long offset = (Math.max(current, 1) - 1) * size;
        String filter = "";
        List<Object> args = new ArrayList<>();
        args.add(user.getId());
        if (StringUtils.isNotBlank(status)) {
            switch (status.trim().toUpperCase()) {
                case TaskConstants.R_PENDING:
                    filter += " AND r.status='PENDING'";
                    break;
                case TaskConstants.R_IN_PROGRESS:
                    filter += " AND r.status IN ('IN_PROGRESS','RETURNED')";
                    break;
                case TaskConstants.R_SUBMITTED:
                    filter += " AND r.status='SUBMITTED'";
                    break;
                case TaskConstants.R_REVIEWED:
                    filter += " AND r.status='REVIEWED'";
                    break;
                case TaskConstants.R_RETURNED:
                    filter += " AND r.status='RETURNED'";
                    break;
                default:
                    break;
            }
        }
        if (StringUtils.isNotBlank(keyword)) {
            filter += " AND t.title LIKE ?";
            args.add("%" + keyword.trim() + "%");
        }
        String from = " FROM learning_task_recipient r JOIN learning_task t ON t.id=r.taskId "
                + "WHERE r.userId=? AND t.isDelete=0" + filter;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        args.add(size);
        args.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id taskId,t.taskCode,t.title,t.description,t.dueAt,t.allowLate,t.status taskStatus,t.publishedAt,"
                        + "t.publishedPolicyJson,r.id recipientId,r.status,r.acceptedAt,r.latestSubmissionId"
                        + from + " ORDER BY t.publishedAt DESC,t.updateTime DESC LIMIT ? OFFSET ?", args.toArray());
        Map<Long, Map<String, Long>> composition = composition(rows);
        Date now = new Date();
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("taskCode", row.get("taskCode"));
            record.put("title", row.get("title"));
            record.put("description", row.get("description"));
            record.put("dueAt", TeacherTaskService.formatDate(toDate(row.get("dueAt"))));
            record.put("allowLate", Integer.valueOf(1).equals(row.get("allowLate")));
            record.put("taskStatus", row.get("taskStatus"));
            record.put("status", row.get("status"));
            record.put("acceptedAt", TeacherTaskService.formatDate(toDate(row.get("acceptedAt"))));
            record.put("publishedAt", TeacherTaskService.formatDate(toDate(row.get("publishedAt"))));
            boolean submitted = TaskConstants.R_SUBMITTED.equals(row.get("status"))
                    || TaskConstants.R_REVIEWED.equals(row.get("status"));
            boolean overdue = toDate(row.get("dueAt")) != null && now.after(toDate(row.get("dueAt"))) && !submitted;
            record.put("overdue", overdue);
            Object policy = row.get("publishedPolicyJson");
            ObjectNode policyNode = parseObject(policy == null ? null : policy.toString());
            record.put("scored", policyNode.path("scored").asBoolean(false));
            record.put("totalMaxScore", policyNode.path("totalMaxScore").decimalValue());
            if (row.get("latestSubmissionId") != null) {
                LearningTaskSubmission submission = submissionMapper.selectById(
                        ((Number) row.get("latestSubmissionId")).longValue());
                if (submission != null) {
                    record.put("latestSubmissionId", String.valueOf(submission.getId()));
                    record.put("roundNo", submission.getRoundNo());
                    record.put("submittedAt", TeacherTaskService.formatDate(submission.getSubmittedAt()));
                    record.put("isLate", Integer.valueOf(1).equals(submission.getIsLate()));
                    record.put("autoScore", submission.getAutoScore());
                    record.put("totalScore", submission.getTotalScore());
                    record.put("submissionStatus", submission.getStatus());
                    record.put("review", parseObject(submission.getReviewJson()));
                }
            }
            long taskId = ((Number) row.get("taskId")).longValue();
            record.put("composition", composition.getOrDefault(taskId, Collections.emptyMap()));
            records.add(record);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("current", current);
        out.put("pageSize", size);
        return out;
    }

    private Map<Long, Map<String, Long>> composition(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Long>> out = new HashMap<>();
        if (rows.isEmpty()) return out;
        List<Long> taskIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object taskId = row.get("taskId");
            if (taskId != null) taskIds.add(((Number) taskId).longValue());
        }
        if (taskIds.isEmpty()) return out;
        String placeholders = String.join(",", Collections.nCopies(taskIds.size(), "?"));
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT taskId,itemType,COUNT(*) amount FROM learning_task_item WHERE taskId IN (" + placeholders
                        + ") GROUP BY taskId,itemType", taskIds.toArray())) {
            Long taskId = ((Number) row.get("taskId")).longValue();
            Map<String, Long> counts = out.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>());
            counts.put(String.valueOf(row.get("itemType")), ((Number) row.get("amount")).longValue());
        }
        return out;
    }

    public Map<String, Object> summary(User user) {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT r.status,COUNT(*) amount FROM learning_task_recipient r "
                        + "JOIN learning_task t ON t.id=r.taskId WHERE r.userId=? AND t.isDelete=0 "
                        + "GROUP BY r.status", user.getId())) {
            counts.put(String.valueOf(row.get("status")), ((Number) row.get("amount")).longValue());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", counts.getOrDefault(TaskConstants.R_PENDING, 0L));
        out.put("inProgress", counts.getOrDefault(TaskConstants.R_IN_PROGRESS, 0L)
                + counts.getOrDefault(TaskConstants.R_RETURNED, 0L));
        out.put("returned", counts.getOrDefault(TaskConstants.R_RETURNED, 0L));
        out.put("submitted", counts.getOrDefault(TaskConstants.R_SUBMITTED, 0L));
        out.put("reviewed", counts.getOrDefault(TaskConstants.R_REVIEWED, 0L));
        out.put("total", counts.values().stream().mapToLong(Long::longValue).sum());
        return out;
    }

    public Map<String, Object> detail(User user, String taskCode) {
        LearningTask task = access.task(taskCode);
        LearningTaskRecipient recipient = access.requireRecipient(task, user);
        LearningTaskSubmission latest = access.latestSubmission(recipient.getId());
        int editableRound = access.editableRound(recipient.getId());
        int displayRound = access.editable(recipient) ? editableRound
                : (latest == null ? 1 : latest.getRoundNo());
        List<LearningTaskItem> items = items(task.getId());
        Map<Long, LearningTaskItemWork> works = new HashMap<>();
        for (LearningTaskItemWork work : workMapper.selectList(new LambdaQueryWrapper<LearningTaskItemWork>()
                .eq(LearningTaskItemWork::getRecipientId, recipient.getId())
                .eq(LearningTaskItemWork::getRoundNo, displayRound))) {
            works.put(work.getItemId(), work);
        }
        List<Map<String, Object>> itemViews = new ArrayList<>();
        for (LearningTaskItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemType", item.getItemType());
            row.put("sortOrder", item.getSortOrder());
            row.put("required", item.getRequired() == 1);
            row.put("maxScore", item.getMaxScore());
            row.put("content", contentService.studentContent(item));
            LearningTaskItemWork work = works.get(item.getId());
            row.put("work", workView(work));
            itemViews.add(row);
        }
        Date now = new Date();
        boolean accepted = access.editable(recipient);
        boolean overdue = task.getDueAt() != null && now.after(task.getDueAt());
        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("canAccept", TaskConstants.R_PENDING.equals(recipient.getStatus())
                && TaskConstants.TASK_PUBLISHED.equals(task.getStatus()));
        boolean canWork = accepted && TaskConstants.TASK_PUBLISHED.equals(task.getStatus());
        allowed.put("canWork", canWork);
        allowed.put("canSubmit", canWork && (!overdue || Integer.valueOf(1).equals(task.getAllowLate())));
        allowed.put("canViewHistory", true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskCode", task.getTaskCode());
        out.put("title", task.getTitle());
        out.put("description", task.getDescription());
        out.put("courseCode", task.getCourseCode());
        out.put("unitCode", task.getUnitCode());
        out.put("dueAt", TeacherTaskService.formatDate(task.getDueAt()));
        out.put("allowLate", Integer.valueOf(1).equals(task.getAllowLate()));
        out.put("taskStatus", task.getStatus());
        out.put("status", recipient.getStatus());
        out.put("acceptedAt", TeacherTaskService.formatDate(recipient.getAcceptedAt()));
        out.put("publishedAt", TeacherTaskService.formatDate(task.getPublishedAt()));
        out.put("supplements", parseArray(task.getSupplementsJson()));
        out.put("overdue", overdue);
        out.put("currentRound", displayRound);
        out.put("editableRound", editableRound);
        out.put("allowed", allowed);
        out.put("items", itemViews);
        out.put("history", history(recipient.getId()));
        User teacher = userMapper.selectById(task.getCreatorId());
        out.put("teacherName", teacher == null ? null : teacher.getUserName());
        return out;
    }

    // ---------------- 接收 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> accept(User user, String taskCode) {
        LearningTask task = access.task(taskCode);
        LearningTaskRecipient recipient = access.requireRecipient(task, user);
        ThrowUtils.throwIf(TaskConstants.TASK_CLOSED.equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "任务已关闭");
        ThrowUtils.throwIf(!TaskConstants.TASK_PUBLISHED.equals(task.getStatus())
                        && !TaskConstants.R_PENDING.equals(recipient.getStatus()),
                ErrorCode.OPERATION_ERROR, "任务当前不可接收");
        if (TaskConstants.R_PENDING.equals(recipient.getStatus())) {
            recipient.setStatus(TaskConstants.R_IN_PROGRESS);
            recipient.setAcceptedAt(new Date());
            recipientMapper.updateById(recipient);
        }
        return detail(user, taskCode);
    }

    // ---------------- 草稿保存 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveWork(User user, String taskCode, String itemCode,
                                        TaskWorkSaveRequest request) {
        ThrowUtils.throwIf(request == null || request.getRoundNo() == null,
                ErrorCode.PARAMS_ERROR, "缺少提交轮次");
        LearningTask task = access.task(taskCode);
        access.requirePublished(task);
        LearningTaskRecipient recipient = access.requireAccepted(task, user);
        access.requireEditable(recipient, request.getRoundNo());
        LearningTaskItem item = requireItem(task.getId(), itemCode);
        List<Long> fileIds = request.getFileIds() == null ? new ArrayList<>() : request.getFileIds();
        fileService.validateStudentFiles(task.getId(), itemCode, recipient.getId(), fileIds);
        LearningTaskItemWork work = work(recipient.getId(), item.getId(), request.getRoundNo());
        boolean created = false;
        if (work == null) {
            work = new LearningTaskItemWork();
            work.setId(IdWorker.getId());
            work.setRecipientId(recipient.getId());
            work.setItemId(item.getId());
            work.setRoundNo(request.getRoundNo());
            work.setRowVersion(0);
            created = true;
        }
        if (request.getAnswer() != null) work.setAnswerJson(request.getAnswer().toString());
        if (request.getTextResponse() != null) work.setTextResponse(request.getTextResponse());
        work.setFileIdsJson(fileIds.isEmpty() ? "[]" : JSON.valueToTree(fileIds).toString());
        if (created) workMapper.insert(work);
        else workMapper.updateById(work);
        if (TaskConstants.R_RETURNED.equals(recipient.getStatus())) {
            recipient.setStatus(TaskConstants.R_IN_PROGRESS);
            recipientMapper.updateById(recipient);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        out.put("roundNo", request.getRoundNo());
        out.put("work", workView(work));
        return out;
    }

    // ---------------- 真实作答 / 判题 ----------------

    public Map<String, Object> answer(User user, String taskCode, String itemCode, TaskAnswerRequest request) {
        ThrowUtils.throwIf(request == null || request.getAnswer() == null,
                ErrorCode.PARAMS_ERROR, "答案不能为空");
        ThrowUtils.throwIf(request.getRoundNo() == null,
                ErrorCode.PARAMS_ERROR, "缺少提交轮次");
        LearningTask task = access.task(taskCode);
        access.requirePublished(task);
        LearningTaskRecipient recipient = access.requireAccepted(task, user);
        access.requireEditable(recipient, request.getRoundNo());
        LearningTaskItem item = requireItem(task.getId(), itemCode);
        ThrowUtils.throwIf(!TaskConstants.ITEM_QUESTION.equals(item.getItemType()),
                ErrorCode.OPERATION_ERROR, "该内容块不是客观题");
        LearningQuestion question = learningQuestionService.getById(item.getRuntimeResourceId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "任务题目执行副本不存在");
        LearningAnswerRequest answer = new LearningAnswerRequest();
        answer.setQuestionId(item.getRuntimeResourceId());
        answer.setAnswer(JSON.convertValue(request.getAnswer(), Object.class));
        answer.setSourceTaskCode(taskCode);
        answer.setSourceTaskItemCode(itemCode);
        answer.setSourceTaskRound(request.getRoundNo());
        answer.setAssessmentRunId(null);
        answer.setSourceUnitCode(null);
        answer.setSourceActivityCode(null);
        String key = StringUtils.defaultIfBlank(request.getSubmissionKey(), UUID.randomUUID().toString());
        answer.setSubmissionKey("task:" + taskCode + ":" + itemCode + ":r" + request.getRoundNo()
                + ":" + key);
        LearningAnswerResultVO result = learningQuestionService.answerQuestion(answer, user);
        LearningTaskItemWork work = workOrCreate(recipient.getId(), item.getId(), request.getRoundNo());
        work.setAnswerJson(request.getAnswer() == null ? null : request.getAnswer().toString());
        work.setLastAttemptId(result.getAttemptId());
        workMapper.updateById(work);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attemptId", String.valueOf(result.getAttemptId()));
        out.put("correct", result.getCorrect());
        out.put("analysis", result.getAnalysis());
        out.put("misconception", result.getMisconception());
        out.put("isFirst", result.getIsFirst());
        out.put("masteryChanges", result.getMasteryChanges());
        out.put("roundNo", request.getRoundNo());
        return out;
    }

    public Map<String, Object> codeSubmit(User user, String taskCode, String itemCode,
                                          TaskCodeSubmitRequest request) {
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getCode()),
                ErrorCode.PARAMS_ERROR, "代码不能为空");
        ThrowUtils.throwIf(request.getRoundNo() == null, ErrorCode.PARAMS_ERROR, "缺少提交轮次");
        LearningTask task = access.task(taskCode);
        access.requirePublished(task);
        LearningTaskRecipient recipient = access.requireAccepted(task, user);
        access.requireEditable(recipient, request.getRoundNo());
        LearningTaskItem item = requireItem(task.getId(), itemCode);
        ThrowUtils.throwIf(!TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType()),
                ErrorCode.OPERATION_ERROR, "该内容块不是编程实验");
        ObjectNode snapshot = parseObject(item.getPublishedSnapshotJson());
        ThrowUtils.throwIf(snapshot.path("manualReview").asBoolean(false),
                ErrorCode.OPERATION_ERROR, "该实验为人工评阅，不执行代码判题");
        QuestionSubmitAddRequest submit = new QuestionSubmitAddRequest();
        submit.setQuestionId(item.getRuntimeResourceId());
        submit.setLanguage(request.getLanguage());
        submit.setCode(request.getCode());
        submit.setSourceTaskCode(taskCode);
        submit.setSourceTaskItemCode(itemCode);
        submit.setSourceTaskRound(request.getRoundNo());
        long id = questionSubmitService.doQuestionSubmit(submit, user);
        LearningTaskItemWork work = workOrCreate(recipient.getId(), item.getId(), request.getRoundNo());
        work.setLastCodeSubmissionId(id);
        ObjectNode draft = JSON.createObjectNode();
        draft.put("language", request.getLanguage());
        draft.put("code", request.getCode());
        draft.put("submissionKey", request.getSubmissionKey());
        work.setAnswerJson(draft.toString());
        workMapper.updateById(work);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("codeSubmissionId", String.valueOf(id));
        out.put("status", QuestionSubmitStatusEnum.WAITING.getValue());
        return out;
    }

    public Map<String, Object> codeStatus(User user, String taskCode, String itemCode, long codeSubmissionId) {
        LearningTask task = access.task(taskCode);
        access.requireRecipient(task, user);
        LearningTaskItem item = requireItem(task.getId(), itemCode);
        QuestionSubmit submit = questionSubmitService.getById(codeSubmissionId);
        ThrowUtils.throwIf(submit == null || !submit.getUserId().equals(user.getId()),
                ErrorCode.NOT_FOUND_ERROR, "判题记录不存在");
        ThrowUtils.throwIf(!item.getRuntimeResourceId().equals(submit.getQuestionId())
                        || !taskCode.equals(submit.getSourceTaskCode())
                        || !itemCode.equals(submit.getSourceTaskItemCode()),
                ErrorCode.NO_AUTH_ERROR, "判题记录不属于当前任务内容块");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("codeSubmissionId", String.valueOf(submit.getId()));
        out.put("status", submit.getStatus());
        out.put("terminal", Integer.valueOf(QuestionSubmitStatusEnum.SUCCEED.getValue()).equals(submit.getStatus())
                || Integer.valueOf(QuestionSubmitStatusEnum.FAILED.getValue()).equals(submit.getStatus()));
        out.put("judgeInfo", parseObject(submit.getJudgeInfo()));
        out.put("language", submit.getLanguage());
        out.put("code", submit.getCode());
        out.put("createTime", TeacherTaskService.formatDate(submit.getCreateTime()));
        return out;
    }

    // ---------------- 正式提交 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(User user, String taskCode, TaskSubmitRequest request) {
        ThrowUtils.throwIf(request == null || request.getRoundNo() == null,
                ErrorCode.PARAMS_ERROR, "缺少提交轮次");
        LearningTask task = access.task(taskCode);
        LearningTaskRecipient recipient = access.requireRecipient(task, user);
        String submissionKey = StringUtils.defaultIfBlank(request.getSubmissionKey(),
                UUID.randomUUID().toString());
        ThrowUtils.throwIf(submissionKey.length() > 64, ErrorCode.PARAMS_ERROR, "提交标识过长");
        // 网络重试：先按 submissionKey 返回既有提交，即使任务已关闭/轮次已变化。
        LearningTaskSubmission existing = submissionMapper.selectOne(new LambdaQueryWrapper<LearningTaskSubmission>()
                .eq(LearningTaskSubmission::getRecipientId, recipient.getId())
                .eq(LearningTaskSubmission::getSubmissionKey, submissionKey)
                .last("limit 1"));
        if (existing != null) return studentSubmissionDetail(user, taskCode, existing.getId());
        access.requirePublished(task);
        recipient = access.requireAccepted(task, user);
        int editableRound = access.editableRound(recipient.getId());
        ThrowUtils.throwIf(request.getRoundNo() != editableRound,
                ErrorCode.OPERATION_ERROR, "提交轮次已变化，请刷新页面");
        Date now = new Date();
        if (access.late(task, now) && !Integer.valueOf(1).equals(task.getAllowLate())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已超过截止时间且不允许逾期提交");
        }
        LearningTaskSubmission roundExisting = submissionMapper.selectOne(
                new LambdaQueryWrapper<LearningTaskSubmission>()
                        .eq(LearningTaskSubmission::getRecipientId, recipient.getId())
                        .eq(LearningTaskSubmission::getRoundNo, request.getRoundNo())
                        .last("limit 1"));
        if (roundExisting != null) {
            if (submissionKey.equals(roundExisting.getSubmissionKey())) {
                return studentSubmissionDetail(user, taskCode, roundExisting.getId());
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "本轮已提交，请勿重复提交");
        }

        List<LearningTaskItem> items = items(task.getId());
        Map<Long, LearningTaskItemWork> works = new HashMap<>();
        for (LearningTaskItemWork work : workMapper.selectList(new LambdaQueryWrapper<LearningTaskItemWork>()
                .eq(LearningTaskItemWork::getRecipientId, recipient.getId())
                .eq(LearningTaskItemWork::getRoundNo, request.getRoundNo()))) {
            works.put(work.getItemId(), work);
        }
        List<String> missing = new ArrayList<>();
        ArrayNode itemResults = JSON.createArrayNode();
        BigDecimal autoScore = BigDecimal.ZERO;
        boolean manualRequired = false;
        boolean scored = false;
        for (LearningTaskItem item : items) {
            ObjectNode snapshot = parseObject(item.getPublishedSnapshotJson());
            LearningTaskItemWork work = works.get(item.getId());
            boolean required = Integer.valueOf(1).equals(item.getRequired());
            BigDecimal maxScore = item.getMaxScore();
            ObjectNode result = itemResults.addObject();
            result.put("itemCode", item.getItemCode());
            result.put("title", snapshot.path("title").asText(""));
            result.put("itemType", item.getItemType());
            result.put("required", required);
            result.put("manual", snapshot.path("manual").asBoolean(false));
            if (maxScore == null) result.putNull("maxScore");
            else result.put("maxScore", maxScore);
            boolean answered = true;
            BigDecimal itemScore = BigDecimal.ZERO;
            boolean scoredItem = maxScore != null;
            if (scoredItem) scored = true;
            if (TaskConstants.ITEM_QUESTION.equals(item.getItemType())) {
                LearningAttempt attempt = resolveAttempt(user, taskCode, item, request.getRoundNo(), work);
                if (attempt == null) {
                    answered = false;
                    result.put("answered", false);
                    if (required) missing.add(title(snapshot, item));
                } else {
                    boolean correct = Integer.valueOf(1).equals(attempt.getIsCorrect());
                    result.put("answered", true);
                    result.put("attemptId", String.valueOf(attempt.getId()));
                    result.set("answer", parseJson(attempt.getSubmittedAnswer()));
                    result.put("correct", correct);
                    String analysis = attemptAnalysis(attempt);
                    if (analysis != null) result.put("analysis", analysis);
                    itemScore = correct && scoredItem ? maxScore : BigDecimal.ZERO;
                }
            } else if (TaskConstants.ITEM_EXPERIMENT.equals(item.getItemType())) {
                boolean manual = snapshot.path("manual").asBoolean(false);
                if (manual) {
                    answered = manualComplete(snapshot, work, required, missing, title(snapshot, item));
                    manualRequired = true;
                    result.put("answered", answered);
                    attachWork(result, work);
                } else {
                    QuestionSubmit code = resolveCodeSubmit(user, taskCode, item, request.getRoundNo(), work);
                    if (code == null) {
                        answered = false;
                        result.put("answered", false);
                        if (required) missing.add(title(snapshot, item) + "（未提交代码）");
                    } else if (isPending(code)) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                                "「" + title(snapshot, item) + "」判题尚未结束，请稍后提交");
                    } else if (Integer.valueOf(QuestionSubmitStatusEnum.FAILED.getValue()).equals(code.getStatus())) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                                "「" + title(snapshot, item) + "」判题系统故障，请重新提交代码");
                    } else {
                        boolean pass = judgeAccepted(code);
                        result.put("answered", true);
                        result.put("codeSubmissionId", String.valueOf(code.getId()));
                        result.put("codeStatus", code.getStatus());
                        result.set("judgeInfo", parseObject(code.getJudgeInfo()));
                        result.put("passed", pass);
                        if (!pass && snapshot.path("requirePass").asBoolean(true) && required) {
                            missing.add(title(snapshot, item) + "（必须通过测试用例）");
                        }
                        itemScore = pass && scoredItem ? maxScore : BigDecimal.ZERO;
                    }
                }
            } else if (TaskConstants.ITEM_TEXT.equals(item.getItemType())) {
                boolean manual = snapshot.path("manual").asBoolean(false);
                if (manual) manualRequired = true;
                boolean hasText = work != null && StringUtils.isNotBlank(work.getTextResponse());
                boolean hasFile = work != null && parseIds(work.getFileIdsJson()).size() > 0;
                boolean confirm = confirm(work);
                boolean requireText = snapshot.path("requireText").asBoolean(false);
                boolean requireFile = snapshot.path("requireFile").asBoolean(false);
                boolean requireConfirm = !manual && snapshot.path("requireConfirm").asBoolean(true);
                if (requireText && !hasText) {
                    answered = false;
                    if (required) missing.add(title(snapshot, item) + "（需要文字答复）");
                }
                if (requireFile && !hasFile) {
                    answered = false;
                    if (required) missing.add(title(snapshot, item) + "（需要上传文件）");
                }
                if (requireConfirm && !confirm) {
                    answered = false;
                    if (required) missing.add(title(snapshot, item) + "（请确认已阅读）");
                }
                result.put("answered", answered);
                attachWork(result, work);
            } else if (TaskConstants.ITEM_MATERIAL.equals(item.getItemType())) {
                boolean manual = snapshot.path("manual").asBoolean(false);
                if (manual) manualRequired = true;
                boolean hasFile = work != null && parseIds(work.getFileIdsJson()).size() > 0;
                boolean confirm = confirm(work);
                if (snapshot.path("requireConfirm").asBoolean(false) && !confirm) {
                    answered = false;
                    if (required) missing.add(title(snapshot, item) + "（请确认已阅读资料）");
                }
                if (snapshot.path("requireFile").asBoolean(false) && !hasFile) {
                    answered = false;
                    if (required) missing.add(title(snapshot, item) + "（需要上传作业文件）");
                }
                result.put("answered", answered);
                attachWork(result, work);
            }
            result.put("scored", scoredItem);
            if (!result.path("manual").asBoolean(false) && scoredItem) {
                autoScore = autoScore.add(itemScore);
                result.put("autoScore", itemScore);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "还有未完成的必做项：" + String.join("；", new LinkedHashSet<>(missing)));
        }
        ObjectNode snapshotOut = JSON.createObjectNode();
        snapshotOut.set("items", itemResults);
        snapshotOut.put("roundNo", request.getRoundNo());
        snapshotOut.put("submittedAt", TeacherTaskService.formatDate(now));
        snapshotOut.put("isLate", access.late(task, now));
        snapshotOut.put("scored", scored);
        snapshotOut.put("manualRequired", manualRequired);
        LearningTaskSubmission submission = new LearningTaskSubmission();
        submission.setId(IdWorker.getId());
        submission.setRecipientId(recipient.getId());
        submission.setRoundNo(request.getRoundNo());
        submission.setSubmissionKey(submissionKey);
        submission.setSubmittedSnapshotJson(snapshotOut.toString());
        submission.setAutoScore(autoScore);
        submission.setIsLate(access.late(task, now) ? 1 : 0);
        submission.setSubmittedAt(now);
        if (manualRequired) {
            submission.setStatus(TaskConstants.R_SUBMITTED);
        } else {
            submission.setStatus(TaskConstants.R_REVIEWED);
            submission.setManualScore(BigDecimal.ZERO);
            submission.setTotalScore(autoScore);
            submission.setReviewedAt(now);
        }
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException e) {
            LearningTaskSubmission duplicate = submissionMapper.selectOne(
                    new LambdaQueryWrapper<LearningTaskSubmission>()
                            .eq(LearningTaskSubmission::getRecipientId, recipient.getId())
                            .eq(LearningTaskSubmission::getSubmissionKey, submissionKey)
                            .last("limit 1"));
            if (duplicate != null) return studentSubmissionDetail(user, taskCode, duplicate.getId());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "本轮已提交，请刷新");
        }
        recipient.setStatus(submission.getStatus());
        recipient.setLatestSubmissionId(submission.getId());
        recipientMapper.updateById(recipient);
        return studentSubmissionDetail(user, taskCode, submission.getId());
    }

    // ---------------- 历史 ----------------

    public List<Map<String, Object>> history(Long recipientId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearningTaskSubmission submission : submissionMapper.selectList(
                new LambdaQueryWrapper<LearningTaskSubmission>()
                        .eq(LearningTaskSubmission::getRecipientId, recipientId)
                        .orderByDesc(LearningTaskSubmission::getRoundNo))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("submissionId", String.valueOf(submission.getId()));
            row.put("roundNo", submission.getRoundNo());
            row.put("status", submission.getStatus());
            row.put("isLate", Integer.valueOf(1).equals(submission.getIsLate()));
            row.put("autoScore", submission.getAutoScore());
            row.put("manualScore", submission.getManualScore());
            row.put("totalScore", submission.getTotalScore());
            row.put("submittedAt", TeacherTaskService.formatDate(submission.getSubmittedAt()));
            row.put("reviewedAt", TeacherTaskService.formatDate(submission.getReviewedAt()));
            ObjectNode review = parseObject(submission.getReviewJson());
            if (review.hasNonNull("returnReason")) row.put("returnReason", review.path("returnReason").asText());
            if (review.hasNonNull("overallComment")) row.put("comment", review.path("overallComment").asText());
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> historyFor(User user, String taskCode) {
        LearningTask task = access.task(taskCode);
        LearningTaskRecipient recipient = access.requireRecipient(task, user);
        return history(recipient.getId());
    }

    public Map<String, Object> studentSubmissionDetail(User user, String taskCode, long submissionId) {
        LearningTask task = access.task(taskCode);
        LearningTaskSubmission submission = submissionMapper.selectById(submissionId);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR, "提交记录不存在");
        LearningTaskRecipient recipient = recipientMapper.selectById(submission.getRecipientId());
        ThrowUtils.throwIf(recipient == null || !recipient.getUserId().equals(user.getId())
                        || !task.getId().equals(recipient.getTaskId()),
                ErrorCode.NO_AUTH_ERROR, "只能查看本人的提交记录");
        ObjectNode snapshot = parseObject(submission.getSubmittedSnapshotJson());
        ArrayNode items = snapshot.path("items").isArray()
                ? (ArrayNode) snapshot.path("items") : JSON.createArrayNode();
        for (JsonNode item : items) {
            ObjectNode row = (ObjectNode) item;
            List<Long> fileIds = new ArrayList<>();
            for (JsonNode fileId : row.path("fileIds")) {
                try {
                    fileIds.add(Long.parseLong(fileId.asText()));
                } catch (Exception ignored) {
                }
            }
            row.set("files", JSON.valueToTree(fileService.metadataList(fileIds)));
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
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", String.valueOf(submission.getId()));
        out.put("roundNo", submission.getRoundNo());
        out.put("status", submission.getStatus());
        out.put("isLate", Integer.valueOf(1).equals(submission.getIsLate()));
        out.put("autoScore", submission.getAutoScore());
        out.put("manualScore", submission.getManualScore());
        out.put("totalScore", submission.getTotalScore());
        out.put("submittedAt", TeacherTaskService.formatDate(submission.getSubmittedAt()));
        out.put("reviewedAt", TeacherTaskService.formatDate(submission.getReviewedAt()));
        out.put("review", parseObject(submission.getReviewJson()));
        out.put("items", items);
        out.put("taskTitle", task.getTitle());
        return out;
    }

    // ---------------- 内部解析 ----------------

    private boolean manualComplete(ObjectNode snapshot, LearningTaskItemWork work, boolean required,
                                   List<String> missing, String title) {
        boolean hasText = work != null && StringUtils.isNotBlank(work.getTextResponse());
        boolean hasFile = work != null && parseIds(work.getFileIdsJson()).size() > 0;
        boolean ok = true;
        if (snapshot.path("requireText").asBoolean(true) && !hasText) {
            ok = false;
            if (required) missing.add(title + "（需要填写说明）");
        }
        if (snapshot.path("requireFile").asBoolean(false) && !hasFile) {
            ok = false;
            if (required) missing.add(title + "（需要上传报告）");
        }
        return ok;
    }

    private void attachWork(ObjectNode result, LearningTaskItemWork work) {
        if (work == null) return;
        if (StringUtils.isNotBlank(work.getTextResponse())) result.put("textResponse", work.getTextResponse());
        List<Long> fileIds = parseIds(work.getFileIdsJson());
        result.set("fileIds", JSON.valueToTree(fileIds));
        result.set("files", JSON.valueToTree(fileService.metadataList(fileIds)));
        if (work.getAnswerJson() != null) result.set("draft", parseObject(work.getAnswerJson()));
    }

    private boolean confirm(LearningTaskItemWork work) {
        if (work == null || work.getAnswerJson() == null) return false;
        return parseObject(work.getAnswerJson()).path("confirm").asBoolean(false);
    }

    private LearningAttempt resolveAttempt(User user, String taskCode, LearningTaskItem item,
                                           int roundNo, LearningTaskItemWork work) {
        if (work != null && work.getLastAttemptId() != null) {
            LearningAttempt direct = attemptMapper.selectById(work.getLastAttemptId());
            if (matches(direct, user, taskCode, item, roundNo)) return direct;
        }
        List<LearningAttempt> rows = attemptMapper.selectList(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getSourceTaskCode, taskCode)
                .eq(LearningAttempt::getSourceTaskItemCode, item.getItemCode())
                .eq(LearningAttempt::getSourceTaskRound, roundNo)
                .eq(LearningAttempt::getQuestionId, item.getRuntimeResourceId())
                .orderByDesc(LearningAttempt::getCreateTime)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean matches(LearningAttempt attempt, User user, String taskCode, LearningTaskItem item, int roundNo) {
        return attempt != null && attempt.getUserId().equals(user.getId())
                && taskCode.equals(attempt.getSourceTaskCode())
                && item.getItemCode().equals(attempt.getSourceTaskItemCode())
                && Integer.valueOf(roundNo).equals(attempt.getSourceTaskRound())
                && item.getRuntimeResourceId().equals(attempt.getQuestionId());
    }

    private QuestionSubmit resolveCodeSubmit(User user, String taskCode, LearningTaskItem item,
                                             int roundNo, LearningTaskItemWork work) {
        if (work != null && work.getLastCodeSubmissionId() != null) {
            QuestionSubmit direct = questionSubmitService.getById(work.getLastCodeSubmissionId());
            if (matches(direct, user, taskCode, item, roundNo)) return direct;
        }
        List<QuestionSubmit> rows = questionSubmitService.list(new LambdaQueryWrapper<QuestionSubmit>()
                .eq(QuestionSubmit::getUserId, user.getId())
                .eq(QuestionSubmit::getSourceTaskCode, taskCode)
                .eq(QuestionSubmit::getSourceTaskItemCode, item.getItemCode())
                .eq(QuestionSubmit::getSourceTaskRound, roundNo)
                .eq(QuestionSubmit::getQuestionId, item.getRuntimeResourceId())
                .orderByDesc(QuestionSubmit::getCreateTime)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean matches(QuestionSubmit submit, User user, String taskCode, LearningTaskItem item, int roundNo) {
        return submit != null && submit.getUserId().equals(user.getId())
                && taskCode.equals(submit.getSourceTaskCode())
                && item.getItemCode().equals(submit.getSourceTaskItemCode())
                && Integer.valueOf(roundNo).equals(submit.getSourceTaskRound())
                && item.getRuntimeResourceId().equals(submit.getQuestionId());
    }

    private boolean isPending(QuestionSubmit submit) {
        return Integer.valueOf(QuestionSubmitStatusEnum.WAITING.getValue()).equals(submit.getStatus())
                || Integer.valueOf(QuestionSubmitStatusEnum.RUNNING.getValue()).equals(submit.getStatus());
    }

    private boolean judgeAccepted(QuestionSubmit submit) {
        ObjectNode info = parseObject(submit.getJudgeInfo());
        return "Accepted".equals(info.path("message").asText());
    }

    private String attemptAnalysis(LearningAttempt attempt) {
        try {
            ObjectNode snapshot = (ObjectNode) JSON.readTree(
                    StringUtils.defaultIfBlank(attempt.getQuestionSnapshot(), "{}"));
            return snapshot.path("analysis").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> workView(LearningTaskItemWork work) {
        if (work == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roundNo", work.getRoundNo());
        out.put("answer", parseJson(work.getAnswerJson()));
        out.put("textResponse", work.getTextResponse());
        List<Long> fileIds = parseIds(work.getFileIdsJson());
        out.put("fileIds", fileIds.stream().map(String::valueOf).collect(Collectors.toList()));
        out.put("files", fileService.metadataList(fileIds));
        out.put("lastAttemptId", work.getLastAttemptId() == null ? null : String.valueOf(work.getLastAttemptId()));
        out.put("lastCodeSubmissionId", work.getLastCodeSubmissionId() == null
                ? null : String.valueOf(work.getLastCodeSubmissionId()));
        if (work.getLastAttemptId() != null) {
            LearningAttempt attempt = attemptMapper.selectById(work.getLastAttemptId());
            if (attempt != null) {
                Map<String, Object> attemptView = new LinkedHashMap<>();
                attemptView.put("attemptId", String.valueOf(attempt.getId()));
                attemptView.put("correct", Integer.valueOf(1).equals(attempt.getIsCorrect()));
                attemptView.put("answer", parseObject(attempt.getSubmittedAnswer()));
                attemptView.put("analysis", attemptAnalysis(attempt));
                out.put("lastAttempt", attemptView);
            }
        }
        if (work.getLastCodeSubmissionId() != null) {
            QuestionSubmit submit = questionSubmitService.getById(work.getLastCodeSubmissionId());
            if (submit != null) {
                Map<String, Object> codeView = new LinkedHashMap<>();
                codeView.put("codeSubmissionId", String.valueOf(submit.getId()));
                codeView.put("status", submit.getStatus());
                codeView.put("judgeInfo", parseObject(submit.getJudgeInfo()));
                codeView.put("language", submit.getLanguage());
                codeView.put("code", submit.getCode());
                codeView.put("terminal", Integer.valueOf(QuestionSubmitStatusEnum.SUCCEED.getValue()).equals(submit.getStatus())
                        || Integer.valueOf(QuestionSubmitStatusEnum.FAILED.getValue()).equals(submit.getStatus()));
                out.put("lastCodeSubmission", codeView);
            }
        }
        return out;
    }

    private LearningTaskItemWork workOrCreate(Long recipientId, Long itemId, Integer roundNo) {
        LearningTaskItemWork work = work(recipientId, itemId, roundNo);
        if (work == null) {
            work = new LearningTaskItemWork();
            work.setId(IdWorker.getId());
            work.setRecipientId(recipientId);
            work.setItemId(itemId);
            work.setRoundNo(roundNo);
            work.setRowVersion(0);
            workMapper.insert(work);
        }
        return work;
    }

    private LearningTaskItemWork work(Long recipientId, Long itemId, Integer roundNo) {
        return workMapper.selectOne(new LambdaQueryWrapper<LearningTaskItemWork>()
                .eq(LearningTaskItemWork::getRecipientId, recipientId)
                .eq(LearningTaskItemWork::getItemId, itemId)
                .eq(LearningTaskItemWork::getRoundNo, roundNo)
                .last("limit 1"));
    }

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

    private static String title(ObjectNode snapshot, LearningTaskItem item) {
        String title = snapshot.path("title").asText("");
        return StringUtils.isBlank(title) ? item.getItemCode() : title;
    }

    private static List<Long> parseIds(String json) {
        List<Long> out = new ArrayList<>();
        if (StringUtils.isBlank(json)) return out;
        try {
            JsonNode node = JSON.readTree(json);
            if (node.isArray()) {
                for (JsonNode value : node) {
                    try {
                        out.add(Long.parseLong(value.asText()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private ObjectNode parseObject(String json) {
        try {
            JsonNode node = JSON.readTree(StringUtils.defaultIfBlank(json, "{}"));
            return node instanceof ObjectNode ? (ObjectNode) node : JSON.createObjectNode();
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private JsonNode parseJson(String json) {
        if (StringUtils.isBlank(json)) return JSON.nullNode();
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return JSON.getNodeFactory().textNode(json);
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

    private static Date toDate(Object value) {
        if (value instanceof java.sql.Timestamp) return new Date(((java.sql.Timestamp) value).getTime());
        if (value instanceof java.time.LocalDateTime) {
            return Date.from(((java.time.LocalDateTime) value).atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return value instanceof Date ? (Date) value : null;
    }
}
