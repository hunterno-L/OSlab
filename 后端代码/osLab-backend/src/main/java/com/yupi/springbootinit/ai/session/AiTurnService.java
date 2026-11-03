package com.yupi.springbootinit.ai.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.ai.plan.NextActionPlanner;
import com.yupi.springbootinit.ai.tutor.TutorOrchestrator;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiTutorTurnMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import com.yupi.springbootinit.model.vo.ai.AiTurnVO;
import com.yupi.springbootinit.model.vo.ai.NextAction;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.ai.ToolTraceEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 导师轮次服务（P0-1 / 任务书 §3）。
 *
 * <p>幂等：{@code (userId, sessionId, clientRequestId)} 唯一，相同请求返回同一轮次；
 * 重试不会重复提交活动，因为活动提交另有 submissionKey 幂等。
 *
 * <p>执行模型：有界线程池 + 状态查询。POST 立即返回 RUNNING，前端按 &ge;1.5s 轮询
 * {@code GET /learning/ai/turns/{turnId}} 直到终态。队列满时直接标 FAILED_RETRYABLE，
 * 不无限堆积（2 核 2GB 部署目标）。
 *
 * <p>恢复：进程重启后仍为 RUNNING 的轮次，其执行线程已不存在，
 * 启动时统一标成 FAILED_RETRYABLE 并写明原因，不会永远挂着。
 */
@Slf4j
@Service
public class AiTurnService {

    @Resource
    private AiTutorTurnMapper turnMapper;

    @Resource
    private AiSessionService sessionService;

    @Resource
    private ModelProfileService modelProfileService;

    @Resource
    private TutorOrchestrator orchestrator;

    @Resource
    private AiProperties properties;

    @Resource
    private AiActionService actionService;

    private ThreadPoolExecutor executor;

    @PostConstruct
    void initExecutor() {
        int poolSize = Math.max(1, properties.getTurn().getPoolSize());
        int queueCapacity = Math.max(1, properties.getTurn().getQueueCapacity());
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "ai-tutor-turn-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * 进程重启恢复：未终态轮次的执行线程已消失，标成可重试并说明原因。
     * 迁移未执行（表不存在）时只记录告警，不阻塞应用启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTurns() {
        try {
            int marked = turnMapper.markRunningTurnsRetryable();
            if (marked > 0) {
                log.warn("[ai-turn] marked {} interrupted RUNNING turn(s) as retryable after restart", marked);
            }
        } catch (RuntimeException e) {
            log.warn("[ai-turn] turn recovery skipped (migration not applied?): {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 提交一轮提问。相同 clientRequestId 返回同一轮次，不重复执行。
     */
    public AiTurnVO submit(User user, String sessionId, AiRequests.CreateTurn request) {
        if (request == null || StringUtils.isBlank(request.getClientRequestId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "clientRequestId 不能为空（用于轮次幂等）");
        }
        if (StringUtils.isBlank(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message 不能为空");
        }
        String clientRequestId = request.getClientRequestId().trim();
        if (clientRequestId.length() > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "clientRequestId 过长");
        }
        AiLearningSession session = sessionService.requireOwned(user, parseId(sessionId));
        AiTutorTurn existing = findByIdempotencyKey(user.getId(), session.getId(), clientRequestId);
        if (existing != null) {
            // 幂等重放：直接返回既有轮次（可能仍是 RUNNING，由前端继续轮询）
            return toVo(existing);
        }
        AiTutorTurn turn = new AiTutorTurn();
        turn.setSessionId(session.getId());
        turn.setUserId(user.getId());
        turn.setClientRequestId(clientRequestId);
        turn.setStatus(AiTutorTurn.STATUS_RUNNING);
        turn.setMode(AiTutorTurn.MODE_FALLBACK);
        turn.setMessage(StringUtils.abbreviate(request.getMessage(), 4000));
        AiCourseContext context = request.getContext() == null ? new AiCourseContext() : request.getContext();
        // 课程作用域以会话为准，不接受前端改写，避免跨课程混用记录
        context.setCourseCode(session.getCourseCode());
        turn.setContextJson(AiJson.write(context));
        turn.setModelProfileId(session.getModelProfileId());
        turn.setModelCalls(0);
        turn.setToolCalls(0);
        turn.setLatencyMs(0L);
        turn.setIsDelete(0);
        try {
            turnMapper.insert(turn);
        } catch (DuplicateKeyException e) {
            AiTutorTurn concurrent = findByIdempotencyKey(user.getId(), session.getId(), clientRequestId);
            if (concurrent != null) {
                return toVo(concurrent);
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "轮次创建失败，请重试");
        }
        final Long turnId = turn.getId();
        final Long ownedSessionId = session.getId();
        final String message = request.getMessage();
        final AiCourseContext capturedContext = context;
        try {
            executor.execute(() -> executeSafely(user, turnId, ownedSessionId, message, capturedContext));
        } catch (RejectedExecutionException e) {
            markFailed(turnId, AiTutorTurn.STATUS_FAILED_RETRYABLE,
                    "导师执行队列已满，请稍后重试", true);
        }
        return toVo(turnMapper.selectById(turnId));
    }

    /** 轮询轮次状态（本人可见）。 */
    public AiTurnVO get(User user, String turnId) {
        return toVo(requireOwnedTurn(user, parseId(turnId)));
    }

    public AiTutorTurn requireOwnedTurn(User user, Long turnId) {
        if (turnId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "turnId 不能为空");
        }
        AiTutorTurn turn = turnMapper.selectById(turnId);
        if (turn == null || (turn.getIsDelete() != null && turn.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "轮次不存在");
        }
        if (!turn.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该轮次");
        }
        return turn;
    }

    // region 执行

    /** 异步执行入口：任何异常都必须落到终态，不允许轮次永远停在 RUNNING。 */
    void executeSafely(User user, Long turnId, Long sessionId, String message, AiCourseContext context) {
        try {
            AiTutorTurn turn = turnMapper.selectById(turnId);
            if (turn == null || !AiTutorTurn.STATUS_RUNNING.equals(turn.getStatus())) {
                return;
            }
            AiLearningSession session = sessionService.requireOwned(user, sessionId);
            ModelEndpoint endpoint = resolveEndpoint(turn, session);
            TutorOrchestrator.TurnResult result = orchestrator.run(user, session, message, context, endpoint);
            persist(turn, result);
            // 回答若已泄露当前评估任务的正确答案，必须在学生提交之前把帮助等级抬到 ANSWER_REVEALED，
            // 这样后续答题事务读到的是"已揭示答案"，该次作答不再计入独立观测。
            actionService.revealIfAnswerLeaked(user, session, result.getAnswer());
            persistPlan(session, result.getNextActions());
        } catch (BusinessException e) {
            markFailed(turnId, AiTutorTurn.STATUS_FAILED, e.getMessage(), false);
        } catch (RuntimeException e) {
            log.warn("[ai-turn] turn {} failed: {}", turnId, e.getClass().getSimpleName());
            markFailed(turnId, AiTutorTurn.STATUS_FAILED_RETRYABLE,
                    "轮次执行失败：" + e.getClass().getSimpleName(), true);
        }
    }

    /**
     * 解析本轮使用的模型端点。
     *
     * <p>配置不可用时不静默替换模型：把原因写进轮次错误信息与工具轨迹，
     * 并以 FALLBACK 模式返回真实检索与规则结论。
     */
    private ModelEndpoint resolveEndpoint(AiTutorTurn turn, AiLearningSession session) {
        try {
            return modelProfileService.resolveEndpoint(session.getUserId(), session.getModelProfileId());
        } catch (BusinessException e) {
            turn.setErrorMessage(e.getMessage());
            turnMapper.updateById(turn);
            return null;
        }
    }

    private void persist(AiTutorTurn turn, TutorOrchestrator.TurnResult result) {
        turn.setStatus(StringUtils.isNotBlank(result.getErrorMessage()) && StringUtils.isBlank(result.getAnswer())
                ? (result.isRetryable() ? AiTutorTurn.STATUS_FAILED_RETRYABLE : AiTutorTurn.STATUS_FAILED)
                : AiTutorTurn.STATUS_SUCCEEDED);
        turn.setAnswer(result.getAnswer());
        turn.setMode(result.getMode());
        turn.setSourcesJson(AiJson.write(result.getSources()));
        turn.setToolTraceJson(AiJson.write(result.getToolTrace()));
        turn.setDiagnosisJson(AiJson.write(result.getDiagnosis()));
        turn.setNextActionsJson(AiJson.write(result.getNextActions()));
        turn.setModelCalls(result.getModelCalls());
        turn.setToolCalls(result.getToolCalls());
        turn.setLatencyMs(result.getLatencyMs());
        if (StringUtils.isNotBlank(result.getErrorMessage())) {
            turn.setErrorMessage(StringUtils.abbreviate(result.getErrorMessage(), 1000));
        }
        turn.setModelLabel(result.getModelLabel());
        AiTurnVO.UsageVO usage = new AiTurnVO.UsageVO();
        usage.setModel(result.getModelLabel());
        usage.setLatencyMs(result.getLatencyMs());
        usage.setModelCalls(result.getModelCalls());
        usage.setToolCalls(result.getToolCalls());
        if (result.getTotalTokens() != null || result.getPromptTokens() != null) {
            AiTurnVO.TokenUsageVO tokens = new AiTurnVO.TokenUsageVO();
            tokens.setPrompt(result.getPromptTokens());
            tokens.setCompletion(result.getCompletionTokens());
            tokens.setTotal(result.getTotalTokens());
            usage.setTokens(tokens);
        }
        turn.setUsageJson(AiJson.write(usage));
        turnMapper.updateById(turn);
    }

    /**
     * 把本轮下一步写回会话计划，并在没有当前任务时把它设为任务位置。
     * 动作 id 由 sessionId 派生，因此 {@code /actions/{id}} 能反解归属并校验权限。
     */
    private void persistPlan(AiLearningSession session, List<NextAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        NextActionPlanner.assignIds(actions, session.getId());
        session.setPlanJson(AiJson.write(actions));
        if (StringUtils.isBlank(session.getCurrentActionJson())) {
            session.setCurrentActionJson(AiJson.write(actions.get(0)));
        }
        sessionService.savePlan(session, actions);
    }

    private void markFailed(Long turnId, String status, String message, boolean retryable) {
        try {
            AiTutorTurn turn = turnMapper.selectById(turnId);
            if (turn == null) {
                return;
            }
            turn.setStatus(status);
            turn.setMode(AiTutorTurn.MODE_FALLBACK);
            turn.setErrorMessage(StringUtils.abbreviate(message, 1000));
            turn.setToolTraceJson(AiJson.write(java.util.Collections.singletonList(
                    new ToolTraceEntry("turn", ToolTraceEntry.STATUS_FAILED, 0,
                            message + (retryable ? "（可重试）" : "")))));
            turnMapper.updateById(turn);
        } catch (RuntimeException e) {
            log.error("[ai-turn] cannot mark turn {} as {}: {}", turnId, status, e.getClass().getSimpleName());
        }
    }

    // endregion

    // region 序列化

    public AiTurnVO toVo(AiTutorTurn turn) {
        AiTurnVO vo = new AiTurnVO();
        if (turn == null) {
            return vo;
        }
        vo.setTurnId(AiJson.id(turn.getId()));
        vo.setStatus(turn.getStatus());
        vo.setAnswer(turn.getAnswer());
        vo.setMode(StringUtils.defaultIfBlank(turn.getMode(), AiTutorTurn.MODE_FALLBACK));
        vo.setErrorMessage(turn.getErrorMessage());
        vo.setGeneratedAt(AiJson.iso(turn.getUpdateTime() == null ? turn.getCreateTime() : turn.getUpdateTime()));
        List<SourceRef> sources = AiJson.readList(turn.getSourcesJson(), SourceRef.class);
        vo.setSources(sources);
        vo.setToolTrace(AiJson.readList(turn.getToolTraceJson(), ToolTraceEntry.class));
        vo.setDiagnosis(AiJson.read(turn.getDiagnosisJson(),
                com.yupi.springbootinit.model.vo.ai.Diagnosis.class));
        vo.setNextActions(AiJson.readList(turn.getNextActionsJson(), NextAction.class));
        vo.setUsage(AiJson.read(turn.getUsageJson(), AiTurnVO.UsageVO.class));
        return vo;
    }

    private AiTutorTurn findByIdempotencyKey(Long userId, Long sessionId, String clientRequestId) {
        List<AiTutorTurn> found = turnMapper.selectList(new LambdaQueryWrapper<AiTutorTurn>()
                .eq(AiTutorTurn::getUserId, userId)
                .eq(AiTutorTurn::getSessionId, sessionId)
                .eq(AiTutorTurn::getClientRequestId, clientRequestId)
                .last("limit 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    private static Long parseId(String value) {
        try {
            return AiJson.parseId(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "id 非法");
        }
    }

    // endregion
}
