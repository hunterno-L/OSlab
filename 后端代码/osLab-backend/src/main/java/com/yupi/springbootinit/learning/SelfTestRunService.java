package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningAssessmentDraftMapper;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.learning.SelfTestDraftSaveRequest;
import com.yupi.springbootinit.model.entity.LearningAssessmentDraft;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.learning.MasteryChangeVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestDraftSaveVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestResultVO;
import com.yupi.springbootinit.model.vo.learning.SelfTestRunVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.task.EvaluationIdentity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 整组自测（SELF_TEST）服务。
 *
 * <p>边界：
 * <ul>
 *   <li>草稿保存只落草稿：不判分、不生成学习事件、不更新掌握度/推荐；</li>
 *   <li>ACTIVE 视图不含答案、解析、误区、正确性、本轮或历史正确率；</li>
 *   <li>交卷时由服务端用行锁串行化，按服务端草稿原子判分并冻结结果；</li>
 *   <li>重复/并发交卷返回同一结果，不产生重复 attempt/event；</li>
 *   <li>未答题不产生错误观测；综合题待自评；下线/版本失效题不计入有效分母。</li>
 * </ul>
 */
@Service
public class SelfTestRunService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ANSWER_LENGTH = 20000;
    private static final int MAX_OPTION_COUNT = 26;

    @Resource
    private LearningAssessmentRunMapper runMapper;

    @Resource
    private LearningAssessmentDraftMapper draftMapper;

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningQuestionNodeMapper questionNodeMapper;

    @Resource
    private LearningAttemptMapper attemptMapper;

    @Resource
    private LearningEventService learningEventService;

    @Resource
    private SelfTestPolicy selfTestPolicy;

    /** 开始一次整组自测：固定队列与题目版本；模式由服务端保存。 */
    @Transactional(rollbackFor = Exception.class)
    public LearningAssessmentRun createRun(User user, String courseCode, String chapter, String nodeName,
                                           List<Long> queue) {
        ThrowUtils.throwIf(user == null || queue == null || queue.isEmpty(), ErrorCode.PARAMS_ERROR);
        LearningAssessmentRun run = new LearningAssessmentRun();
        run.setUserId(user.getId());
        run.setChapter(chapter);
        run.setNodeName(nodeName);
        run.setCourseCode(courseCode);
        run.setMode("SELF_TEST");
        run.setRowVersion(0);
        run.setStatus("ACTIVE");
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(queue));
        run.setQuestionIdsJson(JSONUtil.toJsonStr(ordered));
        JSONObject revisions = new JSONObject();
        for (LearningQuestion question : learningQuestionMapper.selectBatchIds(ordered)) {
            if (question != null && question.getRevision() != null) {
                revisions.set(String.valueOf(question.getId()), question.getRevision());
            }
        }
        run.setQuestionRevisionsJson(revisions.toString());
        ThrowUtils.throwIf(runMapper.insert(run) != 1, ErrorCode.OPERATION_ERROR, "创建自测失败");
        return run;
    }

    /** ACTIVE 自测的安全视图：题面 + 本人草稿 + 标记 + 保存状态。 */
    public SelfTestRunVO view(User user, Long runId) {
        LearningAssessmentRun run = requireOwned(runId, user);
        List<Long> ids = selfTestPolicy.queueIds(run);
        Map<Long, LearningQuestion> questions = loadQuestions(ids);
        Map<Long, LearningAssessmentDraft> drafts = loadDrafts(run.getId(), user.getId(), ids);
        Map<Long, Integer> revisions = parseRevisions(run.getQuestionRevisionsJson());
        SelfTestRunVO vo = new SelfTestRunVO();
        vo.setRunId(String.valueOf(run.getId()));
        vo.setMode("SELF_TEST");
        vo.setStatus(run.getStatus());
        vo.setRowVersion(run.getRowVersion() == null ? 0 : run.getRowVersion());
        vo.setSavedAt(run.getLastSaveAt() == null ? null : run.getLastSaveAt().getTime());
        List<SelfTestRunVO.Question> items = new ArrayList<>();
        for (Long id : ids) {
            LearningQuestion question = questions.get(id);
            SelfTestRunVO.Question item = new SelfTestRunVO.Question();
            item.setId(String.valueOf(id));
            item.setQuestionRevision(revisions.get(id));
            LearningAssessmentDraft draft = drafts.get(id);
            item.setDraftAnswer(draft == null ? null : parseAnswer(draft.getAnswerJson()));
            item.setMarkedUnsure(draft != null && Integer.valueOf(1).equals(draft.getMarkedUnsure()));
            if (question == null || Integer.valueOf(1).equals(question.getIsDelete())
                    || !LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus())) {
                item.setTitle("该题目已下线");
                item.setOffline(true);
                item.setRevisionChanged(false);
                item.setOfflineReason("题目已下线，不参与本次计分");
                items.add(item);
                continue;
            }
            item.setTitle(question.getTitle());
            item.setContent(question.getContent());
            item.setType(question.getType());
            item.setOptions(question.getOptions());
            item.setChapter(question.getChapter());
            item.setDifficulty(question.getDifficulty());
            item.setOffline(false);
            Integer startRevision = revisions.get(id);
            boolean changed = startRevision != null && question.getRevision() != null
                    && !startRevision.equals(question.getRevision());
            item.setRevisionChanged(changed);
            if (changed) {
                item.setOfflineReason("题目内容在自测开始后已更新，交卷时本题不计分");
            }
            items.add(item);
        }
        vo.setQuestions(items);
        return vo;
    }

    /**
     * 保存草稿：只写草稿与 run 版本。
     *
     * @return 新版本；版本冲突时不写库并返回服务端草稿（conflict=true）
     */
    @Transactional(rollbackFor = Exception.class)
    public SelfTestDraftSaveVO saveDrafts(User user, Long runId, SelfTestDraftSaveRequest request) {
        LearningAssessmentRun run = requireOwnedLocked(runId, user);
        ThrowUtils.throwIf(request == null || StringUtils.isBlank(request.getClientSaveId()),
                ErrorCode.PARAMS_ERROR, "缺少保存标识");
        ThrowUtils.throwIf(request.getClientSaveId().length() > 64, ErrorCode.PARAMS_ERROR, "保存标识过长");
        ThrowUtils.throwIf(!"ACTIVE".equals(run.getStatus()), ErrorCode.OPERATION_ERROR, "本轮已结束");
        int currentVersion = versionOf(run);
        if (request.getClientSaveId().equals(run.getLastSaveId())) {
            // 网络重发的同一次保存：返回同一版本，不重复写
            return saveResult(currentVersion, run.getLastSaveAt(), false, Collections.emptyList());
        }
        if (request.getRowVersion() == null || request.getRowVersion() != currentVersion) {
            return saveResult(currentVersion, run.getLastSaveAt(), true, serverDrafts(run, user));
        }
        List<Long> queue = selfTestPolicy.queueIds(run);
        Map<Long, LearningAssessmentDraft> existing = loadDrafts(run.getId(), user.getId(), queue);
        Map<Long, Integer> revisions = parseRevisions(run.getQuestionRevisionsJson());
        Date now = new Date();
        for (SelfTestDraftSaveRequest.Draft incoming : safeDrafts(request)) {
            ThrowUtils.throwIf(incoming == null || incoming.getQuestionId() == null,
                    ErrorCode.PARAMS_ERROR, "草稿缺少题目");
            Long questionId = incoming.getQuestionId();
            ThrowUtils.throwIf(!queue.contains(questionId), ErrorCode.PARAMS_ERROR, "题目不在本轮自测中");
            String answerJson = normalizeAnswer(incoming.getAnswer());
            int marked = Boolean.TRUE.equals(incoming.getMarkedUnsure()) ? 1 : 0;
            LearningAssessmentDraft draft = existing.get(questionId);
            if (draft == null) {
                draft = new LearningAssessmentDraft();
                draft.setRunId(run.getId());
                draft.setUserId(user.getId());
                draft.setQuestionId(questionId);
                draft.setAnswerJson(answerJson);
                draft.setMarkedUnsure(marked);
                draft.setQuestionRevision(revisions.get(questionId));
                draft.setIsDelete(0);
                draft.setCreateTime(now);
                draft.setUpdateTime(now);
                draftMapper.insert(draft);
            } else {
                draft.setAnswerJson(answerJson);
                draft.setMarkedUnsure(marked);
                draft.setUpdateTime(now);
                draftMapper.updateById(draft);
            }
        }
        int updated = runMapper.bumpDraftVersion(run.getId(), request.getClientSaveId(), currentVersion);
        if (updated != 1) {
            // 持有行锁时仍失败只可能是数据被外部并行修改；回滚草稿，让前端重新载入
            throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.OPERATION_ERROR,
                    "草稿版本冲突，请重新载入后再保存");
        }
        return saveResult(currentVersion + 1, now, false, Collections.emptyList());
    }

    /**
     * 交卷：服务端核实草稿版本后原子判分并冻结结果。
     * 同 submissionKey 重发返回同一结果；并发交卷由 run 行锁串行化。
     */
    @Transactional(rollbackFor = Exception.class)
    public SelfTestResultVO finish(User user, Long runId, String submissionKey, Integer rowVersion) {
        LearningAssessmentRun run = requireOwnedLocked(runId, user);
        if ("FINISHED".equals(run.getStatus())) {
            ThrowUtils.throwIf(StringUtils.isBlank(submissionKey)
                            || !submissionKey.equals(run.getSubmissionKey())
                            || StringUtils.isBlank(run.getFinishResultJson()),
                    ErrorCode.OPERATION_ERROR, "本轮已结束");
            return readResult(run);
        }
        ThrowUtils.throwIf(StringUtils.isBlank(submissionKey), ErrorCode.PARAMS_ERROR, "缺少交卷标识");
        ThrowUtils.throwIf(submissionKey.length() > 64, ErrorCode.PARAMS_ERROR, "交卷标识过长");
        ThrowUtils.throwIf(rowVersion == null, ErrorCode.PARAMS_ERROR, "缺少草稿版本");
        int currentVersion = versionOf(run);
        ThrowUtils.throwIf(rowVersion != currentVersion, ErrorCode.OPERATION_ERROR,
                "草稿尚未保存到最新版本，请先保存后再交卷");

        SelfTestResultVO result = gradeDrafts(user, run);
        Date now = new Date();
        result.setFinishedAt(formatTime(now));
        run.setStatus("FINISHED");
        run.setFinishTime(now);
        run.setSubmissionKey(submissionKey);
        run.setSubmissionRowVersion(rowVersion);
        try {
            run.setFinishResultJson(JSON.writeValueAsString(result));
        } catch (Exception e) {
            throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.OPERATION_ERROR,
                    "保存自测结果失败");
        }
        ThrowUtils.throwIf(runMapper.updateById(run) != 1, ErrorCode.OPERATION_ERROR, "结束本轮失败");
        return result;
    }

    /** 只读结果：仅所属用户且 FINISHED 可读；不产生新事件。 */
    public SelfTestResultVO result(User user, Long runId) {
        LearningAssessmentRun run = requireOwned(runId, user);
        ThrowUtils.throwIf(!"FINISHED".equals(run.getStatus()), ErrorCode.OPERATION_ERROR, "本轮尚未交卷");
        ThrowUtils.throwIf(StringUtils.isBlank(run.getFinishResultJson()), ErrorCode.NOT_FOUND_ERROR, "本轮暂无结果");
        return readResult(run);
    }

    private LearningAssessmentRun requireOwned(Long runId, User user) {
        ThrowUtils.throwIf(runId == null, ErrorCode.PARAMS_ERROR);
        LearningAssessmentRun run = runMapper.selectById(runId);
        ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "测评会话不存在");
        ThrowUtils.throwIf(!run.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR, "无权访问该测评会话");
        return run;
    }

    private LearningAssessmentRun requireOwnedLocked(Long runId, User user) {
        ThrowUtils.throwIf(runId == null, ErrorCode.PARAMS_ERROR);
        LearningAssessmentRun run = runMapper.selectByIdForUpdate(runId);
        ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "测评会话不存在");
        ThrowUtils.throwIf(!run.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR, "无权访问该测评会话");
        return run;
    }

    // region 判分

    private SelfTestResultVO gradeDrafts(User user, LearningAssessmentRun run) {
        List<Long> ids = selfTestPolicy.queueIds(run);
        Map<Long, LearningQuestion> questions = loadQuestions(ids);
        Map<Long, LearningAssessmentDraft> drafts = loadDrafts(run.getId(), user.getId(), ids);
        Map<Long, Integer> revisions = parseRevisions(run.getQuestionRevisionsJson());
        Map<Long, List<String>> nodeCodes = nodeCodes(ids);

        SelfTestResultVO result = new SelfTestResultVO();
        result.setRunId(String.valueOf(run.getId()));
        result.setStatus("FINISHED");
        int total = ids.size();
        int invalidCount = 0;
        int validCount = 0;
        int answered = 0;
        int objective = 0;
        int correctCount = 0;
        int wrongCount = 0;
        int unanswered = 0;
        int pending = 0;
        int markedUnsure = 0;
        List<String> wrongIds = new ArrayList<>();
        List<String> unsureIds = new ArrayList<>();
        List<SelfTestResultVO.QuestionResult> items = new ArrayList<>();

        for (Long id : ids) {
            LearningQuestion question = questions.get(id);
            LearningAssessmentDraft draft = drafts.get(id);
            Object answer = draft == null ? null : parseAnswer(draft.getAnswerJson());
            boolean marked = draft != null && Integer.valueOf(1).equals(draft.getMarkedUnsure());
            SelfTestResultVO.QuestionResult item = new SelfTestResultVO.QuestionResult();
            item.setQuestionId(String.valueOf(id));
            item.setMarkedUnsure(marked);
            item.setSubmittedAnswer(answer);
            if (question == null || Integer.valueOf(1).equals(question.getIsDelete())
                    || !LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus())) {
                invalidCount++;
                item.setTitle("该题目已下线");
                item.setStatus(SelfTestResultVO.STATUS_INVALID_OFFLINE);
                item.setInvalidReason("题目已下线，本题不参与本次计分");
                items.add(item);
                continue;
            }
            Integer startRevision = revisions.get(id);
            boolean revisionChanged = startRevision != null && question.getRevision() != null
                    && !startRevision.equals(question.getRevision());
            if (revisionChanged) {
                invalidCount++;
                item.setTitle(question.getTitle());
                item.setType(question.getType());
                item.setStatus(SelfTestResultVO.STATUS_INVALID_VERSION);
                item.setInvalidReason("题目内容在自测开始后已更新，本题不参与本次计分");
                items.add(item);
                continue;
            }
            validCount++;
            item.setTitle(question.getTitle());
            item.setType(question.getType());
            item.setAnalysis(question.getAnalysis());
            item.setMisconception(question.getMisconception());
            if (marked) {
                markedUnsure++;
                unsureIds.add(String.valueOf(id));
            }
            if (draft == null || isBlankAnswer(answer)) {
                unanswered++;
                item.setStatus(SelfTestResultVO.STATUS_UNANSWERED);
                items.add(item);
                continue;
            }
            answered++;
            if ("COMPREHENSIVE".equals(question.getType())) {
                pending++;
                item.setStatus(SelfTestResultVO.STATUS_PENDING_SELF_ASSESSMENT);
                item.setCorrect(null);
                items.add(item);
                continue;
            }
            objective++;
            boolean correct = LearningQuestionGrader.grade(question.getType(), question.getAnswer(), answer);
            item.setCorrect(correct);
            item.setStatus(correct ? SelfTestResultVO.STATUS_CORRECT : SelfTestResultVO.STATUS_WRONG);
            if (correct) {
                correctCount++;
            } else {
                wrongCount++;
                wrongIds.add(String.valueOf(id));
            }
            recordAttempt(user, run, question, answer, correct, nodeCodes.get(id), item);
            items.add(item);
        }

        result.setTotalQuestions(total);
        result.setValidQuestions(validCount);
        result.setAnsweredQuestions(answered);
        result.setObjectiveQuestions(objective);
        result.setCorrectCount(correctCount);
        result.setWrongCount(wrongCount);
        result.setAccuracy(objective == 0 ? null : correctCount / (double) objective);
        result.setUnansweredCount(unanswered);
        result.setPendingSelfAssessmentCount(pending);
        result.setInvalidCount(invalidCount);
        result.setMarkedUnsureCount(markedUnsure);
        result.setQuestions(items);
        result.setWrongQuestionIds(wrongIds);
        result.setUnsureQuestionIds(unsureIds);
        result.setSummaryText(summaryText(validCount, correctCount, wrongCount, objective, unanswered, pending, invalidCount));
        return result;
    }

    /** 交卷判分产生一次正式 attempt 与学习事件；帮助资格在自测中恒为 NONE。 */
    private void recordAttempt(User user, LearningAssessmentRun run, LearningQuestion question, Object answer,
                               boolean correct, List<String> nodeCodes,
                               SelfTestResultVO.QuestionResult item) {
        String identity = EvaluationIdentity.forLearningQuestion(question, nodeCodes);
        Long identityAttempts = attemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getEvaluationIdentity, identity));
        Long legacyAttempts = attemptMapper.selectCount(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, user.getId())
                .eq(LearningAttempt::getQuestionId, question.getId())
                .isNull(LearningAttempt::getEvaluationIdentity));
        boolean isFirst = (identityAttempts == null || identityAttempts == 0)
                && (legacyAttempts == null || legacyAttempts == 0);

        LearningAttempt attempt = new LearningAttempt();
        attempt.setQuestionId(question.getId());
        attempt.setUserId(user.getId());
        attempt.setSubmittedAnswer(JSONUtil.toJsonStr(answer));
        attempt.setIsCorrect(correct ? 1 : 0);
        attempt.setIsFirst(isFirst ? 1 : 0);
        attempt.setHasViewedAnalysis(1);
        attempt.setAssessmentRunId(run.getId());
        attempt.setSubmissionKey("selftest:" + run.getId() + ":" + question.getId());
        attempt.setQuestionRevision(question.getRevision());
        attempt.setQuestionSnapshot(JSONUtil.toJsonStr(question));
        attempt.setEvaluationIdentity(identity);
        attemptMapper.insert(attempt);
        item.setAttemptId(String.valueOf(attempt.getId()));

        List<MasteryChangeVO> changes = learningEventService.recordAttemptEvents(
                user.getId(), question, attempt.getId(), correct, isFirst,
                EvidenceQualification.of(null, EvidenceQualification.HELP_NONE, null));
        item.setMasteryChanges(changes == null ? new ArrayList<>() : changes);
        List<String> eventIds = new ArrayList<>();
        for (Long eventId : learningEventService.eventIdsOfAttempt(user.getId(), attempt.getId())) {
            eventIds.add(String.valueOf(eventId));
        }
        item.setEventIds(eventIds);
    }

    // endregion

    // region 读取辅助

    private Map<Long, LearningQuestion> loadQuestions(List<Long> ids) {
        Map<Long, LearningQuestion> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (LearningQuestion question : learningQuestionMapper.selectBatchIds(ids)) {
            if (question != null) {
                map.put(question.getId(), question);
            }
        }
        return map;
    }

    private Map<Long, LearningAssessmentDraft> loadDrafts(Long runId, Long userId, List<Long> ids) {
        Map<Long, LearningAssessmentDraft> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (LearningAssessmentDraft draft : draftMapper.selectList(new LambdaQueryWrapper<LearningAssessmentDraft>()
                .eq(LearningAssessmentDraft::getRunId, runId)
                .eq(LearningAssessmentDraft::getUserId, userId)
                .in(LearningAssessmentDraft::getQuestionId, ids)
                .eq(LearningAssessmentDraft::getIsDelete, 0))) {
            map.putIfAbsent(draft.getQuestionId(), draft);
        }
        return map;
    }

    private Map<Long, List<String>> nodeCodes(List<Long> ids) {
        Map<Long, List<String>> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (LearningQuestionNode node : questionNodeMapper.selectList(new LambdaQueryWrapper<LearningQuestionNode>()
                .in(LearningQuestionNode::getQuestionId, ids))) {
            if (node.getNodeCode() != null) {
                map.computeIfAbsent(node.getQuestionId(), ignored -> new ArrayList<>()).add(node.getNodeCode());
            }
        }
        return map;
    }

    private Map<Long, Integer> parseRevisions(String json) {
        Map<Long, Integer> map = new HashMap<>();
        if (StringUtils.isBlank(json)) {
            return map;
        }
        try {
            JSONObject object = JSONUtil.parseObj(json);
            for (String key : object.keySet()) {
                try {
                    map.put(Long.valueOf(key), object.getInt(key));
                } catch (NumberFormatException ignored) {
                    // 忽略损坏的键，不扩大影响
                }
            }
        } catch (RuntimeException ignored) {
            // 快照损坏按“无版本信息”处理：交卷时仍按当前版本判分
        }
        return map;
    }

    private List<SelfTestDraftSaveVO.ServerDraft> serverDrafts(LearningAssessmentRun run, User user) {
        List<SelfTestDraftSaveVO.ServerDraft> list = new ArrayList<>();
        for (LearningAssessmentDraft draft : loadDrafts(run.getId(), user.getId(),
                selfTestPolicy.queueIds(run)).values()) {
            SelfTestDraftSaveVO.ServerDraft item = new SelfTestDraftSaveVO.ServerDraft();
            item.setQuestionId(String.valueOf(draft.getQuestionId()));
            item.setAnswer(parseAnswer(draft.getAnswerJson()));
            item.setMarkedUnsure(Integer.valueOf(1).equals(draft.getMarkedUnsure()));
            list.add(item);
        }
        return list;
    }

    private SelfTestDraftSaveVO saveResult(Integer rowVersion, Date savedAt, boolean conflict,
                                           List<SelfTestDraftSaveVO.ServerDraft> drafts) {
        SelfTestDraftSaveVO vo = new SelfTestDraftSaveVO();
        vo.setRowVersion(rowVersion);
        vo.setSavedAt(savedAt == null ? null : savedAt.getTime());
        vo.setConflict(conflict);
        vo.setServerDrafts(drafts);
        return vo;
    }

    private List<SelfTestDraftSaveRequest.Draft> safeDrafts(SelfTestDraftSaveRequest request) {
        return request.getDrafts() == null ? Collections.emptyList() : request.getDrafts();
    }

    private int versionOf(LearningAssessmentRun run) {
        return run.getRowVersion() == null ? 0 : run.getRowVersion();
    }

    private SelfTestResultVO readResult(LearningAssessmentRun run) {
        try {
            return JSON.readValue(run.getFinishResultJson(), SelfTestResultVO.class);
        } catch (Exception e) {
            throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.OPERATION_ERROR,
                    "自测结果读取失败");
        }
    }

    // endregion

    // region 答案规范化

    private String normalizeAnswer(Object answer) {
        if (answer == null) {
            return null;
        }
        if (answer instanceof String) {
            String value = (String) answer;
            ThrowUtils.throwIf(value.length() > MAX_ANSWER_LENGTH, ErrorCode.PARAMS_ERROR, "答案过长");
            return writeJson(value);
        }
        if (answer instanceof Collection) {
            Collection<?> values = (Collection<?>) answer;
            ThrowUtils.throwIf(values.size() > MAX_OPTION_COUNT, ErrorCode.PARAMS_ERROR, "选择过多");
            List<String> normalized = new ArrayList<>();
            for (Object value : values) {
                ThrowUtils.throwIf(!(value instanceof String), ErrorCode.PARAMS_ERROR, "答案格式不正确");
                String text = (String) value;
                ThrowUtils.throwIf(text.length() > MAX_ANSWER_LENGTH, ErrorCode.PARAMS_ERROR, "答案过长");
                normalized.add(text);
            }
            return writeJson(normalized);
        }
        throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.PARAMS_ERROR, "草稿答案格式不正确");
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new com.yupi.springbootinit.exception.BusinessException(ErrorCode.SYSTEM_ERROR,
                    "草稿答案保存失败");
        }
    }

    private Object parseAnswer(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            Object value = JSON.readValue(json, Object.class);
            if (value == null) {
                return null;
            }
            if (value instanceof Collection) {
                List<String> values = new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    values.add(item == null ? "" : String.valueOf(item));
                }
                return values;
            }
            return String.valueOf(value);
        } catch (Exception e) {
            // 兼容旧草稿：历史数据可能直接保存了原始文本而非 JSON 字符串
            return json;
        }
    }

    private boolean isBlankAnswer(Object answer) {
        if (answer == null) {
            return true;
        }
        if (answer instanceof CharSequence) {
            return StringUtils.isBlank(answer.toString());
        }
        if (answer instanceof Collection) {
            for (Object item : (Collection<?>) answer) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private String summaryText(int valid, int correct, int wrong, int objective, int unanswered,
                               int pending, int invalid) {
        StringBuilder text = new StringBuilder();
        text.append("有效 ").append(valid).append(" 题：答对 ").append(correct)
                .append("，答错 ").append(wrong);
        if (objective > 0) {
            text.append("，正确率 ").append(Math.round(correct * 100.0 / objective)).append("%");
        }
        text.append("；未作答 ").append(unanswered).append(" 题");
        if (pending > 0) {
            text.append("；待自评 ").append(pending).append(" 题");
        }
        if (invalid > 0) {
            text.append("；失效 ").append(invalid).append(" 题（不计入分母）");
        }
        return text.toString();
    }

    private String formatTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    // endregion

    /** 供控制器判断是否绕过加锁读取。 */
    public static boolean isSelfTest(LearningAssessmentRun run) {
        return Objects.equals("SELF_TEST", run == null ? null : run.getMode());
    }
}
