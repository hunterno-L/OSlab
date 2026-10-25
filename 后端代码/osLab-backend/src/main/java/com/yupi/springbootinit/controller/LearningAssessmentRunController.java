package com.yupi.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 单题测评会话（Phase 4 §8）：队列服务端固定；顺序与首次结果由作答记录读取。
 */
@RestController
@RequestMapping("/learning/assessment")
public class LearningAssessmentRunController {

    @Resource
    private LearningAssessmentRunMapper assessmentRunMapper;

    @Resource
    private LearningAttemptMapper learningAttemptMapper;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private UserService userService;

    /**
     * 开始测评：按范围和数量建立固定队列，返回 runId 与题面列表（不含答案）。
     *
     * <p>阶段 5 §5.2：先取得完整 eligible ID 集合（章节/节点/题型/难度/本人学习状态
     * 过滤后的全部题目 id，无分页上限），再在该全集上做确定性等距抽样；不再存在旧实现
     * pageSize=50 的隐含上限，也不会固定取前 count 条（第 51 条之后的题目同样有机会进入本轮）。
     */
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/start")
    public BaseResponse<StartResult> start(@RequestBody StartRequest request,
                                           HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        ThrowUtils.throwIf(request == null || request.getCount() == null
                || request.getCount() < 1 || request.getCount() > 20, ErrorCode.PARAMS_ERROR, "数量需在 1-20");
        // 候选题：学生口径（已发布）+ 范围过滤 + 本人学习状态过滤，服务端一次取全量 eligible ID
        LearningQuestionQueryRequest query = new LearningQuestionQueryRequest();
        query.setChapter(request.getChapter());
        query.setNodeName(request.getNodeName());
        query.setCourseCode(request.getCourseCode());
        query.setSectionCode(request.getSectionCode());
        query.setUnitCode(request.getUnitCode());
        query.setNodeCode(request.getNodeCode());
        query.setType(request.getType());
        query.setDifficulty(request.getDifficulty());
        query.setStudyState(request.getStudyState());
        List<Long> candidates = learningQuestionService.listEligibleQuestionIds(query, user);
        ThrowUtils.throwIf(candidates.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "范围内没有可作答的题目");
        // 服务端稳定顺序：全集上的确定性等距抽样（不做随机，结果可复算）
        List<Long> queue = sampleStable(candidates, request.getCount());
        LearningAssessmentRun run = new LearningAssessmentRun();
        run.setUserId(user.getId());
        run.setChapter(request.getChapter());
        run.setNodeName(request.getNodeName());
        run.setQuestionIdsJson(JSONUtil.toJsonStr(queue));
        run.setStatus("ACTIVE");
        assessmentRunMapper.insert(run);
        StartResult result = new StartResult();
        result.setRunId(run.getId());
        result.setQuestionIds(queue);
        return ResultUtils.success(result);
    }

    /**
     * 读取本轮进度：题面列表（含本人作答状态）+ 会话状态。
     */
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @GetMapping("/run")
    public BaseResponse<RunResult> run(@RequestParam("runId") long runId,
                                       HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        LearningAssessmentRun run = requireOwnedRun(runId, user);
        RunResult result = new RunResult();
        result.setRunId(run.getId());
        result.setStatus(run.getStatus());
        List<Long> ids = questionIds(run);
        Map<Long, LearningQuestionVO> visibleQuestions = new LinkedHashMap<>();
        for (LearningQuestionVO question : learningQuestionService.getQuestionVOs(ids, user)) {
            visibleQuestions.put(question.getId(), question);
        }
        Map<Long, List<LearningAttempt>> attempts = runAttempts(run, new ArrayList<>(visibleQuestions.keySet()));
        List<LearningQuestionVO> questions = new ArrayList<>();
        for (Long id : ids) {
            RunQuestionVO question = new RunQuestionVO();
            LearningQuestionVO visible = visibleQuestions.get(id);
            if (visible == null) {
                question.setId(id);
                question.setTitle("该题目已下线");
                question.setReviewStatus(LearningReviewStatusEnum.OFFLINE.name());
                question.setOffline(true);
            } else {
                BeanUtils.copyProperties(visible, question);
                question.setOffline(false);
            }
            applyRunProgress(question, attempts.getOrDefault(id, Collections.emptyList()));
            questions.add(question);
        }
        result.setQuestions(questions);
        return ResultUtils.success(result);
    }

    /**
     * 完成本轮：置 FINISHED 并返回摘要（题数、本人作答数、本轮首次答对数）。
     */
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/finish")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<FinishResult> finish(@RequestBody FinishRequest request,
                                             HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        ThrowUtils.throwIf(request == null || request.getRunId() == null, ErrorCode.PARAMS_ERROR);
        LearningAssessmentRun run = requireOwnedRun(request.getRunId(), user);
        ThrowUtils.throwIf(!"ACTIVE".equals(run.getStatus()), ErrorCode.OPERATION_ERROR, "本轮已结束");
        List<Long> ids = questionIds(run);
        List<Long> validIds = new ArrayList<>();
        if (!ids.isEmpty()) {
            for (LearningQuestion question : learningQuestionService.list(new LambdaQueryWrapper<LearningQuestion>()
                    .in(LearningQuestion::getId, ids)
                    .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                    .eq(LearningQuestion::getIsDelete, 0))) {
                validIds.add(question.getId());
            }
        }
        Map<Long, List<LearningAttempt>> attempts = runAttempts(run, validIds);
        FinishResult result = new FinishResult();
        result.setRunId(run.getId());
        result.setTotalQuestions(validIds.size());
        result.setAnsweredQuestions(attempts.size());
        result.setFirstCorrect((int) attempts.values().stream()
                .filter(records -> Integer.valueOf(1).equals(records.get(0).getIsCorrect())).count());
        run.setStatus("FINISHED");
        run.setFinishTime(new Date());
        ThrowUtils.throwIf(assessmentRunMapper.updateById(run) != 1, ErrorCode.OPERATION_ERROR, "结束本轮失败");
        return ResultUtils.success(result);
    }

    /**
     * 确定性抽样（阶段 5 §5.2）：在完整 eligible ID 集合上按等距步长取样。
     * 不做随机、不取隐含前 N 条——同一候选集合与 count 的结果恒定可复算
     * （与服务端"稳定决定顺序"的既有原则一致），且第 51 条之后的题目同样有机会进候选。
     */
    static List<Long> sampleStable(List<Long> candidates, int count) {
        int total = candidates.size();
        int take = Math.min(count, total);
        List<Long> queue = new ArrayList<>(take);
        if (take == total) {
            queue.addAll(candidates);
            return queue;
        }
        for (int k = 0; k < take; k++) {
            queue.add(candidates.get((int) ((long) k * total / take)));
        }
        return queue;
    }

    private List<Long> questionIds(LearningAssessmentRun run) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(JSONUtil.toList(run.getQuestionIdsJson(), Long.class)));
        ids.removeIf(id -> id == null);
        return ids;
    }

    private Map<Long, List<LearningAttempt>> runAttempts(LearningAssessmentRun run, List<Long> ids) {
        Map<Long, List<LearningAttempt>> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (LearningAttempt attempt : learningAttemptMapper.selectList(new LambdaQueryWrapper<LearningAttempt>()
                .eq(LearningAttempt::getUserId, run.getUserId())
                .eq(LearningAttempt::getAssessmentRunId, run.getId())
                .in(LearningAttempt::getQuestionId, ids)
                .orderByAsc(LearningAttempt::getCreateTime)
                .orderByAsc(LearningAttempt::getId))) {
            result.computeIfAbsent(attempt.getQuestionId(), ignored -> new ArrayList<>()).add(attempt);
        }
        return result;
    }

    private void applyRunProgress(RunQuestionVO question, List<LearningAttempt> attempts) {
        question.setHasAttempted(!attempts.isEmpty());
        question.setAttemptCount(attempts.size());
        question.setFirstResult(null);
        question.setLatestResult(null);
        if (attempts.isEmpty()) {
            question.setAnalysis(null);
            question.setMisconception(null);
            return;
        }
        LearningAttempt latest = attempts.get(attempts.size() - 1);
        question.setFirstResult(Integer.valueOf(1).equals(attempts.get(0).getIsCorrect()) ? "CORRECT" : "WRONG");
        question.setLatestCorrect(Integer.valueOf(1).equals(latest.getIsCorrect()));
        question.setLatestResult(question.getLatestCorrect() ? "CORRECT" : "WRONG");
        question.setLatestAttemptTime(latest.getCreateTime());
    }

    private LearningAssessmentRun requireOwnedRun(long runId, User user) {
        LearningAssessmentRun run = assessmentRunMapper.selectById(runId);
        ThrowUtils.throwIf(run == null, ErrorCode.NOT_FOUND_ERROR, "测评会话不存在");
        ThrowUtils.throwIf(!run.getUserId().equals(user.getId()), ErrorCode.NO_AUTH_ERROR, "无权访问该测评会话");
        return run;
    }

    // ---- 请求/响应 DTO ----

    public static class RunQuestionVO extends LearningQuestionVO {
        private Boolean offline;
        private Boolean latestCorrect;
        private Date latestAttemptTime;
        public Boolean getOffline() { return offline; }
        public void setOffline(Boolean offline) { this.offline = offline; }
        public Boolean getLatestCorrect() { return latestCorrect; }
        public void setLatestCorrect(Boolean latestCorrect) { this.latestCorrect = latestCorrect; }
        public Date getLatestAttemptTime() { return latestAttemptTime; }
        public void setLatestAttemptTime(Date latestAttemptTime) { this.latestAttemptTime = latestAttemptTime; }
    }

    public static class StartRequest {
        private String chapter;
        private String nodeName;
        private String courseCode;
        private String sectionCode;
        private String unitCode;
        private String nodeCode;
        private String type;
        private Integer difficulty;
        private String studyState;
        private Integer count;

        public String getChapter() { return chapter; }
        public void setChapter(String chapter) { this.chapter = chapter; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getCourseCode() { return courseCode; }
        public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
        public String getSectionCode() { return sectionCode; }
        public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
        public String getUnitCode() { return unitCode; }
        public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
        public String getNodeCode() { return nodeCode; }
        public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public String getStudyState() { return studyState; }
        public void setStudyState(String studyState) { this.studyState = studyState; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    public static class StartResult {
        private Long runId;
        private List<Long> questionIds;
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public List<Long> getQuestionIds() { return questionIds; }
        public void setQuestionIds(List<Long> questionIds) { this.questionIds = questionIds; }
    }

    public static class RunResult {
        private Long runId;
        private String status;
        private List<LearningQuestionVO> questions;
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<LearningQuestionVO> getQuestions() { return questions; }
        public void setQuestions(List<LearningQuestionVO> questions) { this.questions = questions; }
    }

    public static class FinishRequest {
        private Long runId;
        private Integer answeredCount;
        private Integer firstCorrect;
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public Integer getAnsweredCount() { return answeredCount; }
        public void setAnsweredCount(Integer answeredCount) { this.answeredCount = answeredCount; }
        public Integer getFirstCorrect() { return firstCorrect; }
        public void setFirstCorrect(Integer firstCorrect) { this.firstCorrect = firstCorrect; }
    }

    public static class FinishResult {
        private Long runId;
        private Integer totalQuestions;
        private Integer answeredQuestions;
        private Integer firstCorrect;
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public Integer getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }
        public Integer getAnsweredQuestions() { return answeredQuestions; }
        public void setAnsweredQuestions(Integer answeredQuestions) { this.answeredQuestions = answeredQuestions; }
        public Integer getFirstCorrect() { return firstCorrect; }
        public void setFirstCorrect(Integer firstCorrect) { this.firstCorrect = firstCorrect; }
    }
}
