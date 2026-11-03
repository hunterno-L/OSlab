package com.yupi.springbootinit.ai.evidence;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningEvidenceContextMapper;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 证据上下文的回归（计划 §5.5 / 任务书 §4）：
 * 帮助等级必须在答题事务之前落库、只能单调升级、并发冲突必须显式失败，
 * 且"已消费"的上下文不能再刷出第二次首次观测。
 *
 * <p>纯单元测试：事务边界由 {@code REQUIRES_NEW} 注解声明，
 * 这里验证的是"落库内容与时序契约"，不重复验证 Spring 事务本身。
 */
class LearningEvidenceContextServiceTest {

    private final LearningEvidenceContextMapper contextMapper = mock(LearningEvidenceContextMapper.class);
    private final LearningEvidenceContextService service = new LearningEvidenceContextService();

    private static final Long USER = 7L;
    private static final Long OTHER = 8L;
    private static final Long CONTEXT_ID = 61L;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "evidence-context-test");
        TableInfoHelper.initTableInfo(assistant, LearningEvidenceContext.class);
        ReflectionTestUtils.setField(service, "contextMapper", contextMapper);
        when(contextMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(contextMapper.insert(any())).thenAnswer(invocation -> {
            LearningEvidenceContext context = invocation.getArgument(0);
            context.setId(CONTEXT_ID);
            return 1;
        });
    }

    // region begin：幂等与初始资格

    @Test
    void beginStartsAtHelpNoneAndIsEligibleForObservation() {
        LearningEvidenceContext context = service.begin(USER, "action:na-31-0", "PREDICT",
                "page.lru-vs-fifo.victim", "course.operating-systems", 1, "os.memory.lru", 31L, 900L);

        assertEquals(CONTEXT_ID, context.getId());
        assertEquals(EvidenceQualification.HELP_NONE, context.getHelpLevel());
        assertEquals(EvidenceQualification.SELF_HELP_NONE, context.getSelfReportedHelp());
        assertEquals(1, context.getEligibleForObservation());
        assertEquals(0, context.getConsumed());
        assertEquals(0, context.getRowVersion());
        assertEquals(31L, context.getSessionId());
        assertEquals(900L, context.getTurnId());
    }

    @Test
    void beginReusesExistingContextForSameIdempotencyKey() {
        LearningEvidenceContext existing = context(EvidenceQualification.HELP_HINT, 0);
        when(contextMapper.selectList(any())).thenReturn(List.of(existing));

        LearningEvidenceContext result = service.begin(USER, "action:na-31-0", "QUESTION",
                "101", "course.operating-systems", 1, "os.memory.lru", 31L, 900L);

        assertSame(existing, result, "重复 start 必须复用同一上下文，不能新建一份干净记录");
        verify(contextMapper, never()).insert(any());
    }

    @Test
    void concurrentInsertReusesTheRowCreatedByTheOtherThread() {
        LearningEvidenceContext concurrent = context(EvidenceQualification.HELP_NONE, 0);
        doThrow(new DuplicateKeyException("uk_evidence_context_key")).when(contextMapper).insert(any());
        when(contextMapper.selectList(any())).thenReturn(Collections.emptyList(), List.of(concurrent));

        assertSame(concurrent, service.begin(USER, "action:na-31-0", "QUESTION", "101",
                "course.operating-systems", 1, null, null, null));
    }

    @Test
    void beginRequiresUserAndIdempotencyKey() {
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.begin(null, "action:x", "QUESTION", "101", null, null, null, null, null)).getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.begin(USER, "  ", "QUESTION", "101", null, null, null, null, null)).getCode());
        verifyNoInteractions(contextMapper);
    }

    @Test
    void contextKeysAreStableAndDistinguishFlows() {
        assertEquals("action:na-31-0", LearningEvidenceContextService.actionContextKey("na-31-0"));
        assertEquals("question:101:sub-1", LearningEvidenceContextService.submissionContextKey(101L, "sub-1"));
        assertEquals("question:101:live", LearningEvidenceContextService.submissionContextKey(101L, null));
    }

    // endregion

    // region recordHelp：单调升级 + 显式冲突

    @Test
    void hintRecordedBeforeSubmissionMakesTheAttemptIneligible() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_NONE, 0));
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(1);

        EvidenceQualification qualification = service.recordHelp(USER, CONTEXT_ID, "HINT", null, "学生请求提示");

        assertFalse(qualification.isEligibleForObservation());
        assertEquals(EvidenceQualification.HELP_HINT, qualification.getHelpLevel());
        assertTrue(qualification.getEligibilityReason().contains("提示"));
        ArgumentCaptor<String> level = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> eligible = ArgumentCaptor.forClass(Integer.class);
        verify(contextMapper).updateHelpWithVersion(eq(CONTEXT_ID), level.capture(), anyString(), anyString(),
                eligible.capture(), anyString(), eq(0));
        assertEquals(EvidenceQualification.HELP_HINT, level.getValue());
        assertEquals(0, eligible.getValue(), "带提示作答不得计入独立观测");
    }

    @Test
    void helpLevelOnlyEscalatesNeverDowngrades() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID))
                .thenReturn(context(EvidenceQualification.HELP_ANSWER_REVEALED, 1));
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(1);

        service.recordHelp(USER, CONTEXT_ID, "NONE", null, null);

        ArgumentCaptor<String> level = ArgumentCaptor.forClass(String.class);
        verify(contextMapper).updateHelpWithVersion(eq(CONTEXT_ID), level.capture(), anyString(), anyString(),
                anyInt(), anyString(), anyInt());
        assertEquals(EvidenceQualification.HELP_ANSWER_REVEALED, level.getValue(),
                "已揭示答案后不能因为一次 NONE 请求被洗回可观测");
    }

    @Test
    void hintEscalatesToAnswerRevealed() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_HINT, 0));
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(1);

        EvidenceQualification qualification = service.recordHelp(USER, CONTEXT_ID,
                EvidenceQualification.HELP_ANSWER_REVEALED, null, "模型回答泄露了正确选项");

        assertEquals(EvidenceQualification.HELP_ANSWER_REVEALED, qualification.getHelpLevel());
        assertFalse(qualification.isEligibleForObservation());
    }

    @Test
    void helpHistoryIsAppendedNotOverwritten() {
        LearningEvidenceContext existing = context(EvidenceQualification.HELP_NONE, 0);
        existing.setHelpSourceJson("{\"entries\":[{\"helpLevel\":\"NONE\",\"note\":\"start\"}]}");
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(existing);
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(1);

        service.recordHelp(USER, CONTEXT_ID, "HINT", null, "第一次提示");

        ArgumentCaptor<String> sourceJson = ArgumentCaptor.forClass(String.class);
        verify(contextMapper).updateHelpWithVersion(eq(CONTEXT_ID), anyString(), anyString(),
                sourceJson.capture(), anyInt(), anyString(), anyInt());
        List<Object> entries = JSONUtil.parseObj(sourceJson.getValue()).getBeanList("entries", Object.class);
        assertEquals(2, entries.size(), "帮助轨迹必须保留完整历史");
    }

    @Test
    void selfReportedExternalHelpIsRecordedAndKeepsTheObservationHonest() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_NONE, 0));
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(1);

        EvidenceQualification qualification = service.recordHelp(USER, CONTEXT_ID,
                EvidenceQualification.HELP_NONE, "EXTERNAL_AI", "学生自述用了外部 AI");

        assertEquals("EXTERNAL_AI", qualification.getSelfReportedHelp());
        assertEquals(EvidenceQualification.HELP_NONE, qualification.getHelpLevel());
    }

    @Test
    void illegalHelpLevelIsRejectedBeforeWriting() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_NONE, 0));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.recordHelp(USER, CONTEXT_ID, "SOME_HELP", null, null)).getCode());
        verify(contextMapper, never()).updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt());
    }

    @Test
    void concurrentHelpRecordingFailsLoudlyInsteadOfSilentlyDropping() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_NONE, 3));
        when(contextMapper.updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt())).thenReturn(0);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.recordHelp(USER, CONTEXT_ID, "HINT", null, null));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), failure.getCode());
        assertTrue(failure.getMessage().contains("并发冲突"));
    }

    @Test
    void helpOnAnotherUsersContextIsRejected() {
        LearningEvidenceContext foreign = context(EvidenceQualification.HELP_NONE, 0);
        foreign.setUserId(OTHER);
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(foreign);

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.recordHelp(USER, CONTEXT_ID, "HINT", null, null)).getCode());
        verify(contextMapper, never()).updateHelpWithVersion(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyInt());
    }

    @Test
    void missingContextIsNotFoundRatherThanCreated() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.recordHelp(USER, CONTEXT_ID, "HINT", null, null)).getCode());
    }

    // endregion

    // region lockAndQualify：答题事务内的读锁口径

    @Test
    void lockAndQualifyWithoutContextStaysLegacyEligible() {
        EvidenceQualification qualification = service.lockAndQualify(USER, null);
        assertEquals(EvidenceQualification.HELP_UNKNOWN, qualification.getHelpLevel());
        assertTrue(qualification.isEligibleForObservation(),
                "非 AI 入口（旧链路）必须保持既有观测行为，不能因为升级而全站失效");
    }

    @Test
    void lockAndQualifyReadsUnderRowLockSoHelpWrittenJustBeforeSubmitIsVisible() {
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(context(EvidenceQualification.HELP_HINT, 0));

        EvidenceQualification qualification = service.lockAndQualify(USER, CONTEXT_ID);

        verify(contextMapper).selectByIdForUpdate(CONTEXT_ID);
        assertEquals(EvidenceQualification.HELP_HINT, qualification.getHelpLevel());
        assertFalse(qualification.isEligibleForObservation());
        assertEquals(CONTEXT_ID, qualification.getContextId());
    }

    @Test
    void lockAndQualifyRejectsForeignContext() {
        LearningEvidenceContext foreign = context(EvidenceQualification.HELP_NONE, 0);
        foreign.setUserId(OTHER);
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(foreign);

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), assertThrows(BusinessException.class,
                () -> service.lockAndQualify(USER, CONTEXT_ID)).getCode());
    }

    @Test
    void consumedContextNeverYieldsASecondFirstObservation() {
        LearningEvidenceContext consumed = context(EvidenceQualification.HELP_NONE, 1);
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(consumed);

        EvidenceQualification qualification = service.lockAndQualify(USER, CONTEXT_ID);

        assertFalse(qualification.isEligibleForObservation());
        assertTrue(qualification.getEligibilityReason().contains("重复提交"));
    }

    @Test
    void consumeMarksContextUsedAndKeepsEventReferencesForReplay() {
        LearningEvidenceContext existing = context(EvidenceQualification.HELP_NONE, 0);
        when(contextMapper.selectByIdForUpdate(CONTEXT_ID)).thenReturn(existing);
        when(contextMapper.updateById(any())).thenReturn(1);

        service.consume(USER, CONTEXT_ID, "FAIL", 88L, List.of(1001L, 1002L));

        assertEquals(1, existing.getConsumed());
        assertEquals("FAIL", existing.getResult());
        assertEquals(88L, existing.getAttemptId());
        assertTrue(existing.getEventIdsJson().contains("1001"));
        assertTrue(existing.getEventIdsJson().contains("1002"));
    }

    @Test
    void consumeWithoutContextIsANoOp() {
        service.consume(USER, null, "PASS", 88L, null);
        verifyNoInteractions(contextMapper);
    }

    // endregion

    // region 资格判定口径本身

    @Test
    void helpRankIsMonotonicSoEscalationCannotBeReversed() {
        assertTrue(EvidenceQualification.rank(EvidenceQualification.HELP_NONE)
                < EvidenceQualification.rank(EvidenceQualification.HELP_HINT));
        assertTrue(EvidenceQualification.rank(EvidenceQualification.HELP_HINT)
                < EvidenceQualification.rank(EvidenceQualification.HELP_ANSWER_REVEALED));
    }

    @Test
    void onlyUnhelpedAttemptsCountAsIndependentObservations() {
        assertTrue(EvidenceQualification.of(1L, EvidenceQualification.HELP_NONE, null).isEligibleForObservation());
        assertTrue(EvidenceQualification.of(1L, EvidenceQualification.HELP_UNKNOWN, null).isEligibleForObservation());
        assertFalse(EvidenceQualification.of(1L, EvidenceQualification.HELP_HINT, null).isEligibleForObservation());
        assertFalse(EvidenceQualification.of(1L, EvidenceQualification.HELP_ANSWER_REVEALED, null)
                .isEligibleForObservation());
    }

    // endregion

    // region fixtures

    private LearningEvidenceContext context(String helpLevel, int consumed) {
        LearningEvidenceContext context = new LearningEvidenceContext();
        context.setId(CONTEXT_ID);
        context.setUserId(USER);
        context.setContextKey("action:na-31-0");
        context.setActivityKind(LearningEvidenceContext.KIND_QUESTION);
        context.setCourseCode("course.operating-systems");
        context.setHelpLevel(helpLevel);
        context.setSelfReportedHelp(EvidenceQualification.SELF_HELP_NONE);
        EvidenceQualification qualification = EvidenceQualification.of(CONTEXT_ID, helpLevel, null);
        context.setEligibleForObservation(qualification.isEligibleForObservation() ? 1 : 0);
        context.setEligibilityReason(qualification.getEligibilityReason());
        context.setConsumed(consumed);
        context.setRowVersion(0);
        context.setIsDelete(0);
        return context;
    }

    // endregion
}
