package com.yupi.springbootinit.ai.evidence;

import com.yupi.springbootinit.learning.SelfTestPolicy;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 导师守卫：自测中本组题必须阻断；普通题提示必须先落库；
 * 前端删掉 runId 也不能绕过服务端 ACTIVE 自测判定。
 */
class TutorAssessmentGuardTest {

    private final SelfTestPolicy selfTestPolicy = mock(SelfTestPolicy.class);
    private final LearningAssessmentRunMapper runMapper = mock(LearningAssessmentRunMapper.class);
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningEvidenceContextService evidenceContexts = mock(LearningEvidenceContextService.class);
    private final TutorAssessmentGuard guard = new TutorAssessmentGuard();

    private final User user = new User();
    private final LearningQuestion question = new LearningQuestion();

    @BeforeEach
    void setUp() {
        user.setId(7L);
        question.setId(101L);
        question.setRevision(2);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);
        when(questionMapper.selectById(101L)).thenReturn(question);
        ReflectionTestUtils.setField(guard, "selfTestPolicy", selfTestPolicy);
        ReflectionTestUtils.setField(guard, "assessmentRunMapper", runMapper);
        ReflectionTestUtils.setField(guard, "learningQuestionMapper", questionMapper);
        ReflectionTestUtils.setField(guard, "evidenceContexts", evidenceContexts);
    }

    @Test
    void activeSelfTestBlocksHintEvenWithoutClientRunId() {
        LearningAssessmentRun active = new LearningAssessmentRun();
        active.setId(41L);
        active.setMode("SELF_TEST");
        active.setStatus("ACTIVE");
        when(selfTestPolicy.activeSelfTestContaining(7L, 101L)).thenReturn(active);

        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(101L, null, null, "HINT"));

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("整组自测"));
        assertEquals(41L, decision.getAssessmentRunId());
        verifyNoInteractions(evidenceContexts);
        verifyNoInteractions(questionMapper);
    }

    @Test
    void activeSelfTestRunIdAlwaysBlocks() {
        LearningAssessmentRun active = new LearningAssessmentRun();
        active.setId(41L);
        active.setUserId(7L);
        active.setMode("SELF_TEST");
        active.setStatus("ACTIVE");
        when(runMapper.selectById(41L)).thenReturn(active);

        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(null, null, 41L, "ASK"));

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("整组自测"));
    }

    @Test
    void practiceHintRecordsHelpBeforeAllowing() {
        LearningEvidenceContext context = context(101L, 0);
        when(evidenceContexts.findOwn(7L, 555L)).thenReturn(context);
        when(evidenceContexts.recordHelp(eq(7L), eq(555L), eq(EvidenceQualification.HELP_HINT),
                isNull(), anyString())).thenReturn(
                EvidenceQualification.of(555L, EvidenceQualification.HELP_HINT, null));

        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(101L, 555L, null, "HINT"));

        assertTrue(decision.isAllowed());
        assertEquals("HINT", decision.getHelpLevel());
        assertEquals("101", decision.getQuestionId());
        assertEquals(2, decision.getQuestionRevision());
        verify(evidenceContexts).recordHelp(eq(7L), eq(555L), eq(EvidenceQualification.HELP_HINT),
                isNull(), anyString());
    }

    @Test
    void consumedAttemptIsExplainedWithoutChangingEvidence() {
        LearningEvidenceContext context = context(101L, 1);
        when(evidenceContexts.findOwn(7L, 555L)).thenReturn(context);

        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(101L, 555L, null, "HINT"));

        assertTrue(decision.isAllowed());
        assertEquals(context.getHelpLevel(), decision.getHelpLevel());
        verify(evidenceContexts, never()).recordHelp(anyLong(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    void foreignAttemptContextIsDenied() {
        when(evidenceContexts.findOwn(7L, 555L)).thenReturn(null);

        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(101L, 555L, null, "HINT"));

        assertFalse(decision.isAllowed());
        verifyNoInteractions(questionMapper);
    }

    @Test
    void globalSceneWithoutQuestionIsAllowedWithoutRecordingHelp() {
        TutorAssessmentGuard.Decision decision = guard.check(user,
                TutorAssessmentGuard.Request.of(null, null, null, "ASK"));

        assertTrue(decision.isAllowed());
        assertNull(decision.getQuestionId());
        verifyNoInteractions(evidenceContexts);
    }

    private LearningEvidenceContext context(Long questionId, int consumed) {
        LearningEvidenceContext context = new LearningEvidenceContext();
        context.setId(555L);
        context.setUserId(7L);
        context.setActivityKind(LearningEvidenceContext.KIND_QUESTION);
        context.setActivityCode(String.valueOf(questionId));
        context.setHelpLevel(EvidenceQualification.HELP_NONE);
        context.setSelfReportedHelp(EvidenceQualification.SELF_HELP_NONE);
        context.setConsumed(consumed);
        return context;
    }
}
