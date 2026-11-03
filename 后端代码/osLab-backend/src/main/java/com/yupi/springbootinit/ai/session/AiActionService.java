package com.yupi.springbootinit.ai.session;

import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.LearningEvidenceContextService;
import com.yupi.springbootinit.ai.plan.PredictCaseCatalog;
import com.yupi.springbootinit.ai.plan.ReviewPlanner;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiMaterialChunkMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.ActionSubmitResultVO;
import com.yupi.springbootinit.model.vo.ai.ActivityRefVO;
import com.yupi.springbootinit.model.vo.ai.EvidenceSnapshot;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.TargetRef;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.LearningQuestionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * AI 动作执行服务（P0-3 / 任务书 §3）。
 *
 * <p>start 与 submit 都先按 actionId 反解会话并校验归属，绝不接受前端传来的
 * 题目内容、正确答案或判分结论；判分一律委托原有服务：
 * 客观题走 {@link LearningQuestionService}，页面置换预测走 {@link PredictCaseCatalog}
 * + {@code PageReplacer}，预测事件写入沿用原有 {@code recordSimulationEvent}。
 *
 * <p>幂等：submit 必须携带 submissionKey，重复提交由原有作答幂等与
 * 稳定的模拟幂等键共同保证——刷新、换会话、换模型都不会新增首次观测。
 *
 * <p>帮助等级在答题事务之前落库（独立事务提交），答题事务再以行锁读取，
 * 因此"提示/看答案"一定先于掌握度更新生效。
 *
 * <p>提交前只下发允许看到的前缀：预测任务的后续轨迹与正确答案不出现在 start 响应里。
 */
@Slf4j
@Service
public class AiActionService {

    @Resource
    private AiSessionService sessionService;

    @Resource
    private LearningEvidenceContextService evidenceContexts;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private LearningEventService learningEventService;

    @Resource
    private PredictCaseCatalog predictCases;

    @Resource
    private ReviewPlanner reviewPlanner;

    @Resource
    private AiMaterialChunkMapper chunkMapper;

    @Data
    static class ResolvedAction implements Serializable {
        private AiLearningSession session;
        private NextAction action;
        private int index;
        private List<NextAction> plan;
    }

    // region start

    /**
     * 开始一个动作：创建（或复用）证据上下文并把任务位置写回会话。
     * 返回的 activityRef 只包含提交前允许看到的内容。
     */
    public ActivityRefVO start(User user, String actionId) {
        ResolvedAction resolved = resolve(user, actionId);
        NextAction action = resolved.getAction();
        ActivityRefVO ref = new ActivityRefVO();
        ref.setActionId(action.getActionId());
        ref.setType(action.getType());
        ref.setTargetRef(action.getTargetRef());

        if (action.isAssessed()) {
            // 帮助等级从 NONE 起，且必须在任何作答事务之前落库
            LearningEvidenceContext context = beginContext(user, resolved, null);
            ref.setHelpLevel(StringUtils.defaultIfBlank(context.getHelpLevel(), EvidenceQualification.HELP_NONE));
            ref.setSubmissionKey("sub-" + action.getActionId() + "-" + System.currentTimeMillis());
        } else {
            ref.setHelpLevel(EvidenceQualification.HELP_UNKNOWN);
        }

        if (NextAction.TYPE_PREDICT.equals(action.getType())) {
            fillPredict(ref, action);
        } else if (NextAction.TYPE_QUESTION.equals(action.getType())
                || NextAction.TYPE_RETEST.equals(action.getType())) {
            fillQuestion(user, ref, action);
        } else {
            fillReading(user, ref, resolved, action);
        }

        action.setStatus(NextAction.STATUS_STARTED);
        persistPlan(resolved, action);
        return ref;
    }

    private void fillPredict(ActivityRefVO ref, NextAction action) {
        PredictCaseCatalog.PredictCase predictCase = predictCases.find(action.getTargetRef().getCode());
        if (predictCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                    "预测任务不存在或未通过人工核验：" + action.getTargetRef().getCode());
        }
        PredictCaseCatalog.Truth truth = predictCases.truth(predictCase);
        ref.setTitle(predictCase.getTitle());
        ref.setPrompt(predictCase.getPrompt());
        ref.setFrames(predictCase.getFrames());
        // 只下发提交前允许看到的前缀：待预测步之后的访问序列与任何已解出步骤都不出现
        ref.setVisibleSequence(truth.getVisibleSequence());
        for (PredictCaseCatalog.StepView step : truth.getVisibleSteps()) {
            ref.getVisibleSteps().add(toPredictStep(step));
        }
        ref.setPredictStepIndex(predictCase.getPredictStepIndex());
        ref.setExpectsText(false);
    }

    /** 目录内部的步骤视图转成下发用的 VO，字段一一对应，不额外携带任何后续轨迹。 */
    private static ActivityRefVO.PredictStep toPredictStep(PredictCaseCatalog.StepView step) {
        ActivityRefVO.PredictStep view = new ActivityRefVO.PredictStep();
        view.setStep(step.getStep());
        view.setPage(step.getPage());
        view.setFault(step.getFault());
        view.setEvicted(step.getEvicted());
        view.setFrames(step.getFrames() == null ? new ArrayList<>() : new ArrayList<>(step.getFrames()));
        return view;
    }

    private void fillQuestion(User user, ActivityRefVO ref, NextAction action) {
        Long questionId = parseQuestionId(action);
        LearningQuestionVO vo = learningQuestionService.getQuestionVO(questionId, user);
        ref.setTitle(vo.getTitle());
        ref.setPrompt(vo.getContent());
        ref.setExpectsText(!"SINGLE".equals(vo.getType()) && !"MULTIPLE".equals(vo.getType())
                && !"JUDGE".equals(vo.getType()));
        // 未作答前绝不下发解析与标准答案
        for (Option option : parseOptions(vo.getOptions())) {
            ref.getOptions().add(new ActivityRefVO.Option(option.key, option.label));
        }
    }

    private void fillReading(User user, ActivityRefVO ref, ResolvedAction resolved, NextAction action) {
        TargetRef target = action.getTargetRef();
        ref.setExpectsText(NextAction.TYPE_EXPLAIN.equals(action.getType()));
        if (target != null && TargetRef.KIND_MATERIAL.equals(target.getKind())
                && StringUtils.isNotBlank(target.getCode())) {
            List<Map<String, Object>> rows = chunkMapper.selectScopedByBlockIds(
                    resolved.getSession().getCourseCode(), user.getId(),
                    java.util.Collections.singletonList(target.getCode()));
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资料段落不存在或无权访问");
            }
            Map<String, Object> row = rows.get(0);
            ref.setTitle(str(row.get("title")));
            ref.setPrompt(str(row.get("contentMd")));
            return;
        }
        ref.setTitle(StringUtils.defaultIfBlank(action.getReason(), "阅读任务"));
        ref.setPrompt(action.getReason());
    }

    // endregion

    // region help

    /**
     * 记录帮助（提示 / 揭示答案 / 自报外部帮助）。
     *
     * <p>独立事务提交，因此一定早于后续答题事务；帮助等级只允许单调升级。
     */
    public EvidenceQualification recordHelp(User user, String actionId, String helpLevel,
                                            String selfReportedHelp, String note) {
        ResolvedAction resolved = resolve(user, actionId);
        if (!resolved.getAction().isAssessed()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该任务不产生评估证据，无需记录帮助等级");
        }
        LearningEvidenceContext context = beginContext(user, resolved, null);
        return evidenceContexts.recordHelp(user.getId(), context.getId(), helpLevel, selfReportedHelp, note);
    }

    /**
     * 导师回答泄露答案时的兜底保护（确定性判断，不依赖模型自述）：
     * 会话当前有未提交的客观题任务，且模型回答里出现了服务端保存的正确选项文本或解析开头，
     * 就把该任务的帮助等级升到 ANSWER_REVEALED，避免"看过答案"被记成干净的独立观测。
     *
     * <p>判断刻意保守：宁可漏判（保持升级前行为），也不误伤正常讲解。
     */
    public void revealIfAnswerLeaked(User user, AiLearningSession session, String answer) {
        if (session == null || StringUtils.isBlank(answer)) {
            return;
        }
        NextAction current = AiJson.read(session.getCurrentActionJson(), NextAction.class);
        if (current == null) {
            return;
        }
        boolean scored = NextAction.TYPE_QUESTION.equals(current.getType())
                || NextAction.TYPE_RETEST.equals(current.getType());
        if (!current.isAssessed() || !scored) {
            return;
        }
        if (!NextAction.STATUS_PENDING.equals(current.getStatus())
                && !NextAction.STATUS_STARTED.equals(current.getStatus())) {
            return;
        }
        Long questionId;
        try {
            questionId = parseQuestionId(current);
        } catch (BusinessException e) {
            return;
        }
        LearningQuestion question = learningQuestionService.getById(questionId);
        if (question == null) {
            return;
        }
        boolean leaked = false;
        for (Option option : parseOptions(question.getOptions())) {
            String correct = correctAnswerText(question);
            // 只有"正确选项的完整文本"出现在回答里才算泄露，避免单字母误判
            if (option.key.equals(correct) && StringUtils.length(option.label) >= 4
                    && answer.contains(option.label)) {
                leaked = true;
                break;
            }
        }
        String analysis = StringUtils.trimToEmpty(question.getAnalysis());
        if (!leaked && analysis.length() >= 16 && answer.contains(analysis.substring(0, 16))) {
            leaked = true;
        }
        if (!leaked) {
            return;
        }
        LearningEvidenceContext context = evidenceContexts.findByKey(user.getId(),
                LearningEvidenceContextService.actionContextKey(current.getActionId()));
        if (context == null) {
            context = evidenceContexts.begin(user.getId(),
                    LearningEvidenceContextService.actionContextKey(current.getActionId()),
                    LearningEvidenceContext.KIND_QUESTION, String.valueOf(questionId),
                    session.getCourseCode(), question.getRevision(), null,
                    session.getId(), null);
        }
        evidenceContexts.recordHelp(user.getId(), context.getId(),
                EvidenceQualification.HELP_ANSWER_REVEALED, null, "导师回答中出现了该题答案内容");
        log.info("[ai-action] answer leak detected, help escalated context={} action={}",
                context.getId(), current.getActionId());
    }

    // endregion

    // region submit

    /**
     * 提交动作结果：委托原有判分服务，返回冻结契约
     * {@code {result, correct?, judgeMessage?, evidence}}。
     */
    public ActionSubmitResultVO submit(User user, String actionId, AiRequests.SubmitAction request) {
        ResolvedAction resolved = resolve(user, actionId);
        NextAction action = resolved.getAction();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        if (StringUtils.isBlank(request.getSubmissionKey())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "submissionKey 不能为空（用于提交幂等）");
        }
        if (NextAction.TYPE_READ.equals(action.getType()) || NextAction.TYPE_EXPLAIN.equals(action.getType())) {
            return submitUnassessed(user, resolved, action, request);
        }
        if (NextAction.TYPE_PREDICT.equals(action.getType())) {
            return submitPredict(user, resolved, action, request);
        }
        return submitQuestion(user, resolved, action, request);
    }

    private ActionSubmitResultVO submitQuestion(User user, ResolvedAction resolved, NextAction action,
                                                AiRequests.SubmitAction request) {
        Long questionId = parseQuestionId(action);
        LearningEvidenceContext context = beginContext(user, resolved, questionId);
        // 自报外部帮助在判分事务之前独立提交
        if (StringUtils.isNotBlank(request.getSelfReportedHelp())) {
            evidenceContexts.recordHelp(user.getId(), context.getId(), context.getHelpLevel(),
                    request.getSelfReportedHelp(), "学生自报外部帮助");
        }
        LearningAnswerRequest answerRequest = new LearningAnswerRequest();
        answerRequest.setQuestionId(questionId);
        answerRequest.setAnswer(toAnswer(request));
        answerRequest.setSubmissionKey(request.getSubmissionKey().trim());
        // 判分与首次判定全部由原有服务完成；证据上下文只影响"是否计独立观测"
        LearningAnswerResultVO graded = learningQuestionService.answerQuestion(answerRequest, user, context.getId());

        List<Long> eventIds = learningEventService.eventIdsOfAttempt(user.getId(), graded.getAttemptId());
        EvidenceQualification qualification = evidenceContexts.lockAndQualify(user.getId(), context.getId());
        String result = graded.getCorrect() == null ? "NOT_ASSESSED"
                : (graded.getCorrect() ? "PASS" : "FAIL");
        evidenceContexts.consume(user.getId(), context.getId(), result, graded.getAttemptId(), eventIds);

        ActionSubmitResultVO vo = new ActionSubmitResultVO();
        vo.setResult(result);
        vo.setCorrect(graded.getCorrect());
        vo.setJudgeMessage(graded.getCorrect() == null ? "综合应用题不自动评分，已记录你的作答"
                : (graded.getCorrect() ? "回答正确（服务端判分）" : "回答错误（服务端判分）"));
        vo.setEvidence(evidenceContexts.snapshot(qualification,
                nodeCodeOf(action, resolved), result, eventIds));
        // 解析只在本人已提交后下发
        vo.setAnalysis(graded.getAnalysis());
        vo.setMasteryChanges(graded.getMasteryChanges() == null
                ? new ArrayList<>() : graded.getMasteryChanges());
        markDone(resolved, action);
        scheduleReviewAfter(user, resolved, action, graded.getCorrect(), eventIds);
        return vo;
    }

    private ActionSubmitResultVO submitPredict(User user, ResolvedAction resolved, NextAction action,
                                              AiRequests.SubmitAction request) {
        PredictCaseCatalog.PredictCase predictCase = predictCases.find(action.getTargetRef().getCode());
        if (predictCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "预测任务不存在或未通过人工核验");
        }
        if (StringUtils.isBlank(request.getPredictedProcess())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先提交你的预测");
        }
        int predicted;
        try {
            predicted = Integer.parseInt(request.getPredictedProcess().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "预测值必须是整数");
        }
        PredictCaseCatalog.Truth truth = predictCases.truth(predictCase);
        boolean correct = predicted == truth.getValue();

        LearningEvidenceContext context = beginContext(user, resolved, null);
        if (StringUtils.isNotBlank(request.getSelfReportedHelp())) {
            evidenceContexts.recordHelp(user.getId(), context.getId(), context.getHelpLevel(),
                    request.getSelfReportedHelp(), "学生自报外部帮助");
        }
        EvidenceQualification qualification = evidenceContexts.lockAndQualify(user.getId(), context.getId());

        List<Long> eventIds = new ArrayList<>();
        // 预测判分由确定性计算器给出；只有资格通过时才写入原有模拟事件（掌握度仍由事件驱动）
        if (qualification.isEligibleForObservation()) {
            String evidence = AiJson.write(new PredictEvidence(predictCase.getCode(), predictCase.getAlgorithm(),
                    predictCase.getFrames(), truth.getVisibleSequence(), predicted, truth.getValue(), correct));
            Long eventId = learningEventService.recordSimulationEvent(user.getId(),
                    StringUtils.defaultIfBlank(predictCase.getNodeCode(), predictCase.getSkillCode()),
                    // 稳定幂等键：同一预测案例只产生一次独立观测，换会话/换模型/刷新都不会重复计入
                    "ai-predict:" + predictCase.getCode(), correct, evidence);
            if (eventId != null) {
                eventIds.add(eventId);
            }
        }
        String result = correct ? "PASS" : "FAIL";
        evidenceContexts.consume(user.getId(), context.getId(), result, null, eventIds);

        ActionSubmitResultVO vo = new ActionSubmitResultVO();
        vo.setResult(result);
        vo.setCorrect(correct);
        vo.setJudgeMessage(correct
                ? "预测正确（服务端页面置换计算器核对）"
                : "预测与计算器结果不一致：你预测 " + predicted + "，实际 " + truth.getValue());
        vo.setEvidence(evidenceContexts.snapshot(qualification, predictCase.getSkillCode(), result, eventIds));
        // 提交后才揭示完整解释
        vo.setAnalysis(truth.getExplanation());
        List<String> diff = new ArrayList<>();
        diff.add("待预测步：" + predictCase.getAlgorithm() + " 换出/计数结果 " + truth.getValue());
        diff.add("整条序列缺页 " + truth.getFaults() + " 次、换出 " + truth.getEvictions() + " 次");
        vo.setDiffSteps(diff);
        markDone(resolved, action);
        scheduleReviewAfter(user, resolved, action, correct, eventIds);
        return vo;
    }

    /**
     * 阅读与解释类任务：只记录完成，不产生评估证据。
     * 绝不伪装成"通过评估"，correct 保持 null。
     */
    private ActionSubmitResultVO submitUnassessed(User user, ResolvedAction resolved, NextAction action,
                                                  AiRequests.SubmitAction request) {
        if (NextAction.TYPE_EXPLAIN.equals(action.getType())
                && StringUtils.isBlank(request.getText())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先写下你的解释");
        }
        ActionSubmitResultVO vo = new ActionSubmitResultVO();
        vo.setResult("RECORDED");
        vo.setCorrect(null);
        vo.setJudgeMessage(NextAction.TYPE_EXPLAIN.equals(action.getType())
                ? "已记录你的解释；这不是一次判分，也不作为独立能力证据"
                : "已记录阅读完成；这不是一次判分，也不作为独立能力证据");
        vo.setEvidence(evidenceContexts.snapshot(EvidenceQualification.unknown(), null, "NOT_ASSESSED",
                new ArrayList<>()));
        markDone(resolved, action);
        return vo;
    }

    // endregion

    // region 复习阶梯

    /**
     * 失败 → 安排第一阶梯（1 天）；复习任务通过 → 推进阶梯（3 / 7 天）。
     *
     * @param correct null 表示未评估（综合题/阅读），既不安排复习也不推进阶梯
     */
    private void scheduleReviewAfter(User user, ResolvedAction resolved, NextAction action,
                                     Boolean correct, List<Long> eventIds) {
        if (correct == null) {
            return;
        }
        String activityCode = action.getTargetRef() == null ? null : action.getTargetRef().getCode();
        if (NextAction.TYPE_RETEST.equals(action.getType())) {
            if (correct) {
                for (com.yupi.springbootinit.model.entity.LearningReviewItem item : reviewPlanner.due(
                        user.getId(), resolved.getSession().getCourseCode(), new Date(), 20)) {
                    if (StringUtils.isNotBlank(item.getActivityCode())
                            && item.getActivityCode().equals(activityCode)) {
                        reviewPlanner.complete(item, true, new Date());
                        return;
                    }
                }
            }
            // 复习答错：留在当前阶段重新到期，由 complete(false) 处理
            if (!correct) {
                for (com.yupi.springbootinit.model.entity.LearningReviewItem item : reviewPlanner.due(
                        user.getId(), resolved.getSession().getCourseCode(), new Date(), 20)) {
                    if (StringUtils.isNotBlank(item.getActivityCode())
                            && item.getActivityCode().equals(activityCode)) {
                        reviewPlanner.complete(item, false, new Date());
                        return;
                    }
                }
            }
        }
        if (correct) {
            return;
        }
        String nodeCode = nodeCodeOf(action, resolved);
        ReviewPlanner.ReviewTarget target = new ReviewPlanner.ReviewTarget();
        target.setUserId(user.getId());
        target.setCourseCode(resolved.getSession().getCourseCode());
        target.setNodeCode(nodeCode);
        target.setSkillCode(nodeCode);
        target.setActivityKind(NextAction.TYPE_PREDICT.equals(action.getType())
                ? LearningEvidenceContext.KIND_PREDICT : LearningEvidenceContext.KIND_QUESTION);
        target.setActivityCode(activityCode);
        target.setSourceEventId(eventIds == null || eventIds.isEmpty() ? null : eventIds.get(0));
        reviewPlanner.scheduleFirstStage(target, new Date());
    }

    // endregion

    // region 内部工具

    private ResolvedAction resolve(User user, String actionId) {
        Long sessionId = AiSessionService.sessionIdOf(actionId);
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "actionId 非法：" + actionId);
        }
        // 归属校验：actionId 反解出的会话必须属于当前用户
        AiLearningSession session = sessionService.requireOwned(user, sessionId);
        List<NextAction> plan = sessionService.plan(session);
        int index = AiSessionService.actionIndexOf(actionId);
        if (index < 0 || index >= plan.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在或计划已更新，请刷新后重试");
        }
        NextAction action = plan.get(index);
        if (!actionId.equals(action.getActionId())) {
            // 计划被重新生成后 id 可能移位：以 id 精确匹配为准，避免执行到别的任务
            for (int i = 0; i < plan.size(); i++) {
                if (actionId.equals(plan.get(i).getActionId())) {
                    ResolvedAction resolved = new ResolvedAction();
                    resolved.setSession(session);
                    resolved.setAction(plan.get(i));
                    resolved.setIndex(i);
                    resolved.setPlan(plan);
                    return resolved;
                }
            }
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在或计划已更新，请刷新后重试");
        }
        ResolvedAction resolved = new ResolvedAction();
        resolved.setSession(session);
        resolved.setAction(action);
        resolved.setIndex(index);
        resolved.setPlan(plan);
        return resolved;
    }

    private LearningEvidenceContext beginContext(User user, ResolvedAction resolved, Long questionId) {
        NextAction action = resolved.getAction();
        String kind = NextAction.TYPE_PREDICT.equals(action.getType())
                ? LearningEvidenceContext.KIND_PREDICT
                : (NextAction.TYPE_RETEST.equals(action.getType())
                ? LearningEvidenceContext.KIND_RETEST : LearningEvidenceContext.KIND_QUESTION);
        LearningQuestion question = questionId == null ? null : learningQuestionService.getById(questionId);
        return evidenceContexts.begin(user.getId(),
                LearningEvidenceContextService.actionContextKey(action.getActionId()),
                kind,
                action.getTargetRef() == null ? action.getActionId() : action.getTargetRef().getCode(),
                resolved.getSession().getCourseCode(),
                question == null ? null : question.getRevision(),
                nodeCodeOf(action, resolved),
                resolved.getSession().getId(),
                null);
    }

    private void markDone(ResolvedAction resolved, NextAction action) {
        action.setStatus(NextAction.STATUS_DONE);
        // 完成后清空"当前任务"，让下一步卡回到未完成队列
        NextAction current = AiJson.read(resolved.getSession().getCurrentActionJson(), NextAction.class);
        if (current != null && action.getActionId().equals(current.getActionId())) {
            resolved.getSession().setCurrentActionJson(null);
        }
        persistPlan(resolved, null);
    }

    private void persistPlan(ResolvedAction resolved, NextAction currentAction) {
        AiLearningSession session = resolved.getSession();
        session.setPlanJson(AiJson.write(resolved.getPlan()));
        if (currentAction != null) {
            session.setCurrentActionJson(AiJson.write(currentAction));
        }
        sessionService.savePlan(session, resolved.getPlan());
    }

    private Long parseQuestionId(NextAction action) {
        TargetRef target = action.getTargetRef();
        if (target == null || !StringUtils.isNumeric(StringUtils.trimToEmpty(target.getCode()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题目引用非法，请刷新任务列表");
        }
        return Long.valueOf(target.getCode().trim());
    }

    private String nodeCodeOf(NextAction action, ResolvedAction resolved) {
        if (NextAction.TYPE_PREDICT.equals(action.getType())) {
            PredictCaseCatalog.PredictCase predictCase = predictCases.find(
                    action.getTargetRef() == null ? null : action.getTargetRef().getCode());
            return predictCase == null ? null : predictCase.getNodeCode();
        }
        return resolved.getSession().getUnitCode();
    }

    /** 选择题答案 -> 原有判分服务接受的格式（SINGLE=字符串，MULTIPLE=列表，其余=文本）。 */
    private static Object toAnswer(AiRequests.SubmitAction request) {
        String choice = StringUtils.trimToNull(request.getChoiceKey());
        if (choice != null) {
            if (choice.contains(",") || choice.contains("，")) {
                List<String> keys = new ArrayList<>();
                for (String part : choice.split("[,，]")) {
                    if (StringUtils.isNotBlank(part)) {
                        keys.add(part.trim().toUpperCase());
                    }
                }
                return keys;
            }
            return choice.toUpperCase();
        }
        if (StringUtils.isNotBlank(request.getPredictedProcess())) {
            return request.getPredictedProcess().trim();
        }
        return StringUtils.trimToEmpty(request.getText());
    }

    private static String correctAnswerText(LearningQuestion question) {
        String answer = StringUtils.trimToEmpty(question.getAnswer());
        // 答案以 JSON 字符串保存，例如 "B" 或 ["A","C"]
        String parsed = AiJson.read(answer, String.class);
        if (parsed != null) {
            return parsed.trim().toUpperCase();
        }
        return answer.replace("\"", "").replace("[", "").replace("]", "").trim().toUpperCase();
    }

    @Data
    static class Option implements Serializable {
        private String key;
        private String label;
    }

    /** 题目选项 JSON：兼容 {@code {"A":"文本"}} 与 {@code [{"key":"A","label":"文本"}]} 两种写法。 */
    static List<Option> parseOptions(String optionsJson) {
        List<Option> out = new ArrayList<>();
        if (StringUtils.isBlank(optionsJson)) {
            return out;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = AiJson.mapper().readTree(optionsJson);
            if (node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : node) {
                    Option option = new Option();
                    option.setKey(item.path("key").asText(item.path("label").asText("")));
                    option.setLabel(item.path("label").asText(item.path("content").asText("")));
                    if (StringUtils.isNotBlank(option.getKey())) {
                        out.add(option);
                    }
                }
            } else if (node.isObject()) {
                node.fieldNames().forEachRemaining(key -> {
                    Option option = new Option();
                    option.setKey(key);
                    option.setLabel(node.path(key).asText(""));
                    out.add(option);
                });
            }
        } catch (Exception e) {
            // 选项格式异常时按纯文本处理，不因展示问题阻塞作答
            for (String part : optionsJson.split("[;；\\n]")) {
                String value = part.trim();
                if (value.length() >= 2) {
                    Option option = new Option();
                    option.setKey(value.substring(0, 1));
                    option.setLabel(value);
                    out.add(option);
                }
            }
        }
        out.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return out;
    }

    @Data
    static class PredictEvidence implements Serializable {
        private final String caseCode;
        private final String algorithm;
        private final int frames;
        private final List<Integer> visibleSequence;
        private final int predicted;
        private final int actual;
        private final boolean correct;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    // endregion
}
