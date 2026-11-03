package com.yupi.springbootinit.ai.session;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiLearningSessionMapper;
import com.yupi.springbootinit.mapper.AiTutorTurnMapper;
import com.yupi.springbootinit.model.dto.ai.AiRequests;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.AiTutorTurn;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import com.yupi.springbootinit.model.vo.ai.AiTurnVO;
import com.yupi.springbootinit.ai.tutor.TutorOrchestrator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 会话与轮次的契约回归（任务书 §4/§5）：归属校验、rowVersion、clientRequestId 幂等、
 * 课程作用域不可被前端改写、重启恢复、队列拒绝时的可重试终态。
 *
 * <p>纯单元测试，不连数据库；执行器被替换成同步/拒绝两种替身，
 * 因此"轮次一定会走到终态"这一条可以在没有线程竞态的前提下被验证。
 */
class AiSessionTurnContractTest {

    private final AiLearningSessionMapper sessionMapper = mock(AiLearningSessionMapper.class);
    private final AiTutorTurnMapper turnMapper = mock(AiTutorTurnMapper.class);
    private final ModelProfileService profileService = mock(ModelProfileService.class);
    private final TutorOrchestrator orchestrator = mock(TutorOrchestrator.class);
    private final AiActionService actionService = mock(AiActionService.class);
    private final ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);

    private final AiSessionService sessions = new AiSessionService();
    private final AiTurnService turns = new AiTurnService();

    private final User owner = new User();
    private final User intruder = new User();
    private AiLearningSession session;

    @BeforeEach
    void setUp() {
        // 生产配置 map-underscore-to-camel-case=false（列名驼峰），测试初始化必须一致
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "ai-session-turn-test");
        TableInfoHelper.initTableInfo(assistant, AiLearningSession.class);
        TableInfoHelper.initTableInfo(assistant, AiTutorTurn.class);

        owner.setId(7L);
        intruder.setId(8L);
        session = new AiLearningSession();
        session.setId(31L);
        session.setUserId(7L);
        session.setCourseCode("course.operating-systems");
        session.setGoal("搞懂页面置换");
        session.setFocus(AiSessionService.FOCUS_LEARN);
        session.setTimeBudgetMinutes(20);
        session.setStatus(AiSessionService.STATUS_ACTIVE);
        session.setRowVersion(0);
        session.setIsDelete(0);

        ReflectionTestUtils.setField(sessions, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(sessions, "modelProfileService", profileService);

        ReflectionTestUtils.setField(turns, "turnMapper", turnMapper);
        ReflectionTestUtils.setField(turns, "sessionService", sessions);
        ReflectionTestUtils.setField(turns, "modelProfileService", profileService);
        ReflectionTestUtils.setField(turns, "orchestrator", orchestrator);
        ReflectionTestUtils.setField(turns, "properties", new AiProperties());
        ReflectionTestUtils.setField(turns, "actionService", actionService);
        ReflectionTestUtils.setField(turns, "executor", executor);

        when(sessionMapper.selectById(31L)).thenReturn(session);
        when(sessionMapper.updateById(any())).thenReturn(1);
        when(turnMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(turnMapper.insert(any())).thenAnswer(invocation -> {
            AiTutorTurn turn = invocation.getArgument(0);
            turn.setId(900L);
            capturedTurn = turn;
            return 1;
        });
        // 轮询读回刚写入的轮次
        when(turnMapper.selectById(900L)).thenAnswer(invocation -> capturedTurn);
    }

    /** 最近一次插入的轮次；执行器替身不真的跑线程，因此这里就是"当前轮次"。 */
    private AiTutorTurn capturedTurn;

    // region 会话归属与版本

    @Test
    void createValidatesModelProfileBeforeInsertingAnything() {
        when(profileService.validateProfileForSession(7L, "55"))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "模型配置尚未测试通过"));
        AiRequests.CreateSession request = new AiRequests.CreateSession();
        request.setCourseCode("course.operating-systems");
        request.setModelProfileId("55");
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.create(owner, request)).getCode());
        verify(sessionMapper, never()).insert(any());
    }

    @Test
    void createRequiresCourseCode() {
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.create(owner, new AiRequests.CreateSession())).getCode());
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void otherUsersSessionIsRejectedAsNoAuthWithoutLeakingExistence() {
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.get(intruder, "31")).getCode());
    }

    @Test
    void logicallyDeletedSessionIsNotFound() {
        session.setIsDelete(1);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.get(owner, "31")).getCode());
    }

    @Test
    void updateRejectsMissingOrStaleRowVersion() {
        AiRequests.UpdateSession withoutVersion = new AiRequests.UpdateSession();
        withoutVersion.setGoal("换个目标");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.update(owner, "31", withoutVersion)).getCode());

        AiRequests.UpdateSession stale = new AiRequests.UpdateSession();
        stale.setGoal("换个目标");
        stale.setRowVersion(5);
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.update(owner, "31", stale)).getCode());
        verify(sessionMapper, never()).updateById(any());
    }

    @Test
    void createdSessionCarriesCreatedAtAndUpdatedAtForTheFrozenContract() {
        AiRequests.CreateSession request = new AiRequests.CreateSession();
        request.setCourseCode("course.operating-systems");
        request.setFocus(AiSessionService.FOCUS_LEARN);

        com.yupi.springbootinit.model.vo.ai.AiSessionVO vo = sessions.create(owner, request);

        assertNotNull(vo.getCreatedAt(), "冻结契约样例要求 createdAt 非空（DB 默认值不会回填实体）");
        assertNotNull(vo.getUpdatedAt());
        assertEquals(0, vo.getRowVersion());
        // sessionId 由 MyBatis-Plus ASSIGN_ID 在 insert 时分配；单测里 mapper 是 mock，
        // 因此这里只验证写入实体本身带了契约字段（真实分配由冒烟 S3 覆盖）。
        assertEquals("course.operating-systems", vo.getCourseCode());
    }

    @Test
    void updateBumpsRowVersionAndKeepsEverythingElseWhenOnlyModelChanges() {
        when(profileService.validateProfileForSession(7L, "55")).thenReturn(55L);
        AiRequests.UpdateSession request = new AiRequests.UpdateSession();
        request.setRowVersion(0);
        request.setModelProfileId("55");
        sessions.update(owner, "31", request);
        ArgumentCaptor<AiLearningSession> captor = ArgumentCaptor.forClass(AiLearningSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getRowVersion());
        assertEquals(55L, captor.getValue().getModelProfileId());
        // 切换模型不清空课程、目标与已有计划
        assertEquals("course.operating-systems", captor.getValue().getCourseCode());
        assertEquals("搞懂页面置换", captor.getValue().getGoal());
    }

    @Test
    void updateRejectsIllegalStatus() {
        AiRequests.UpdateSession request = new AiRequests.UpdateSession();
        request.setRowVersion(0);
        request.setStatus("PAUSED");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> sessions.update(owner, "31", request)).getCode());
    }

    @Test
    void actionIdEncodesSessionSoOwnershipCanBeResolvedWithoutExtraTables() {
        String actionId = AiSessionService.actionId(31L, 2);
        assertEquals(Long.valueOf(31L), AiSessionService.sessionIdOf(actionId));
        assertEquals(2, AiSessionService.actionIndexOf(actionId));
        assertNull(AiSessionService.sessionIdOf("na-not-a-number"));
        assertNull(AiSessionService.sessionIdOf(null));
        assertEquals(-1, AiSessionService.actionIndexOf("garbage"));
    }

    // endregion

    // region 轮次幂等与作用域

    @Test
    void turnRequiresClientRequestIdForIdempotency() {
        AiRequests.CreateTurn request = new AiRequests.CreateTurn();
        request.setMessage("为什么 LRU 换出 2？");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> turns.submit(owner, "31", request)).getCode());
        verify(turnMapper, never()).insert(any());
    }

    @Test
    void turnRejectsBlankMessage() {
        AiRequests.CreateTurn request = new AiRequests.CreateTurn();
        request.setClientRequestId("req-1");
        request.setMessage("   ");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> turns.submit(owner, "31", request)).getCode());
    }

    @Test
    void turnIsRejectedForAnotherUsersSessionBeforeAnyInsert() {
        AiRequests.CreateTurn request = turnRequest("req-1");
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> turns.submit(intruder, "31", request)).getCode());
        verify(turnMapper, never()).insert(any());
        verifyNoInteractions(executor);
    }

    @Test
    void sameClientRequestIdReturnsSameTurnWithoutSecondExecution() {
        AiTutorTurn existing = new AiTutorTurn();
        existing.setId(900L);
        existing.setUserId(7L);
        existing.setSessionId(31L);
        existing.setClientRequestId("req-1");
        existing.setStatus(AiTutorTurn.STATUS_RUNNING);
        existing.setMode(AiTutorTurn.MODE_FALLBACK);
        when(turnMapper.selectList(any())).thenReturn(List.of(existing));

        AiTurnVO vo = turns.submit(owner, "31", turnRequest("req-1"));
        assertEquals("900", vo.getTurnId());
        assertEquals(AiTutorTurn.STATUS_RUNNING, vo.getStatus());
        verify(turnMapper, never()).insert(any());
        verifyNoInteractions(executor);
    }

    @Test
    void concurrentDuplicateKeyFallsBackToExistingTurn() {
        AiTutorTurn concurrent = new AiTutorTurn();
        concurrent.setId(900L);
        concurrent.setUserId(7L);
        concurrent.setSessionId(31L);
        concurrent.setClientRequestId("req-1");
        concurrent.setStatus(AiTutorTurn.STATUS_RUNNING);
        concurrent.setMode(AiTutorTurn.MODE_FALLBACK);
        // doThrow 而不是 when(...).thenThrow：后者会真的调用一次已注册的 thenAnswer
        doThrow(new DuplicateKeyException("uk_ai_turn_idempotency")).when(turnMapper).insert(any());
        when(turnMapper.selectList(any())).thenReturn(Collections.emptyList(), List.of(concurrent));

        AiTurnVO vo = turns.submit(owner, "31", turnRequest("req-1"));
        assertEquals("900", vo.getTurnId());
        verifyNoInteractions(executor);
    }

    @Test
    void clientSuppliedCourseCodeIsOverriddenBySessionScope() {
        AiRequests.CreateTurn request = turnRequest("req-1");
        AiCourseContext context = new AiCourseContext();
        context.setCourseCode("course.some-other-course");
        request.setContext(context);

        turns.submit(owner, "31", request);

        ArgumentCaptor<AiTutorTurn> captor = ArgumentCaptor.forClass(AiTutorTurn.class);
        verify(turnMapper).insert(captor.capture());
        assertTrue(captor.getValue().getContextJson().contains("course.operating-systems"),
                "轮次上下文必须以会话课程为准");
        assertFalse(captor.getValue().getContextJson().contains("course.some-other-course"),
                "前端不得改写课程作用域");
        assertEquals(AiTutorTurn.STATUS_RUNNING, captor.getValue().getStatus());
        verify(executor).execute(any());
    }

    @Test
    void saturatedQueueStillReachesATerminalRetryableState() {
        doThrow(new RejectedExecutionException("queue full")).when(executor).execute(any());
        AiTutorTurn stored = new AiTutorTurn();
        stored.setId(900L);
        stored.setUserId(7L);
        stored.setSessionId(31L);
        stored.setStatus(AiTutorTurn.STATUS_RUNNING);
        when(turnMapper.selectById(900L)).thenReturn(stored);

        turns.submit(owner, "31", turnRequest("req-1"));

        assertEquals(AiTutorTurn.STATUS_FAILED_RETRYABLE, stored.getStatus());
        assertEquals(AiTutorTurn.MODE_FALLBACK, stored.getMode());
        assertTrue(stored.getErrorMessage().contains("队列已满"));
        verify(turnMapper).updateById(stored);
    }

    @Test
    void turnPollingIsOwnerScoped() {
        AiTutorTurn mine = new AiTutorTurn();
        mine.setId(900L);
        mine.setUserId(7L);
        mine.setSessionId(31L);
        mine.setStatus(AiTutorTurn.STATUS_SUCCEEDED);
        mine.setMode(AiTutorTurn.MODE_AI);
        when(turnMapper.selectById(900L)).thenReturn(mine);

        assertEquals("900", turns.get(owner, "900").getTurnId());
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> turns.get(intruder, "900")).getCode());
    }

    // endregion

    // region 重启恢复

    @Test
    void restartMarksInterruptedRunningTurnsRetryable() {
        when(turnMapper.markRunningTurnsRetryable()).thenReturn(3);
        turns.recoverInterruptedTurns();
        verify(turnMapper).markRunningTurnsRetryable();
    }

    @Test
    void recoveryFailureDoesNotBlockStartupWhenMigrationIsMissing() {
        when(turnMapper.markRunningTurnsRetryable())
                .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("recover", "UPDATE", null));
        assertDoesNotThrow(() -> turns.recoverInterruptedTurns());
    }

    // endregion

    // region 执行终态

    @Test
    void modelFailureLandsInTerminalStateAndNeverLeavesTurnRunning() {
        runInline();
        AiTutorTurn stored = runningTurn();
        when(turnMapper.selectById(900L)).thenReturn(stored);
        when(profileService.resolveEndpoint(7L, null)).thenReturn(new ModelEndpoint());
        TutorOrchestrator.TurnResult result = new TutorOrchestrator.TurnResult();
        result.setAnswer("规则降级回答");
        result.setMode(AiTutorTurn.MODE_FALLBACK);
        result.setErrorMessage("模型调用失败：鉴权失败");
        result.setRetryable(true);
        when(orchestrator.run(any(), any(), any(), any(), any())).thenReturn(result);

        turns.submit(owner, "31", turnRequest("req-1"));

        assertEquals(AiTutorTurn.MODE_FALLBACK, stored.getMode());
        assertEquals("规则降级回答", stored.getAnswer());
        assertTrue(stored.getErrorMessage().contains("鉴权"));
        assertFalse(stored.getErrorMessage().contains("sk-"), "错误信息不得包含密钥片段");
        // 回答里若泄露答案必须在提交前抬升帮助等级
        verify(actionService).revealIfAnswerLeaked(eq(owner), any(), eq("规则降级回答"));
    }

    @Test
    void unexpectedRuntimeExceptionBecomesRetryableFailure() {
        runInline();
        AiTutorTurn stored = runningTurn();
        when(turnMapper.selectById(900L)).thenReturn(stored);
        when(profileService.resolveEndpoint(7L, null)).thenReturn(new ModelEndpoint());
        when(orchestrator.run(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        turns.submit(owner, "31", turnRequest("req-1"));

        assertEquals(AiTutorTurn.STATUS_FAILED_RETRYABLE, stored.getStatus());
        assertEquals(AiTutorTurn.MODE_FALLBACK, stored.getMode());
    }

    @Test
    void unresolvableEndpointIsReportedInsteadOfSilentlySwitchingModels() {
        runInline();
        AiTutorTurn stored = runningTurn();
        when(turnMapper.selectById(900L)).thenReturn(stored);
        when(profileService.resolveEndpoint(7L, null))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "模型配置尚未测试通过"));
        TutorOrchestrator.TurnResult result = new TutorOrchestrator.TurnResult();
        result.setAnswer("规则回答");
        when(orchestrator.run(any(), any(), any(), any(), isNull())).thenReturn(result);

        turns.submit(owner, "31", turnRequest("req-1"));

        // 端点解析失败后仍以 null 端点执行（降级），并把原因写进轮次
        verify(orchestrator).run(any(), any(), any(), any(), isNull());
        assertTrue(stored.getErrorMessage().contains("尚未测试通过"));
    }

    @Test
    void turnBudgetDefaultsMatchTheFrozenContract() {
        AiProperties properties = new AiProperties();
        assertEquals(3, properties.getTurn().getMaxToolCalls(), "每轮最多 3 次工具执行");
        assertEquals(2, properties.getTurn().getMaxModelCalls(), "每轮最多 2 次模型调用");
    }

    // endregion

    // region fixtures

    private void runInline() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());
    }

    private AiTutorTurn runningTurn() {
        AiTutorTurn turn = new AiTutorTurn();
        turn.setId(900L);
        turn.setUserId(7L);
        turn.setSessionId(31L);
        turn.setClientRequestId("req-1");
        turn.setStatus(AiTutorTurn.STATUS_RUNNING);
        turn.setMode(AiTutorTurn.MODE_FALLBACK);
        turn.setModelCalls(0);
        turn.setToolCalls(0);
        capturedTurn = turn;
        return turn;
    }

    private AiRequests.CreateTurn turnRequest(String clientRequestId) {
        AiRequests.CreateTurn request = new AiRequests.CreateTurn();
        request.setClientRequestId(clientRequestId);
        request.setMessage("为什么 LRU 换出的是 2 而不是 1？");
        return request;
    }

    // endregion
}
