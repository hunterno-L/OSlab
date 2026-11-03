package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.bkt.BktModel;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.enums.QuestionSubmitStatusEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.QuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 5 §5.3 观测分离单元回归：
 * 编译错误 = 诊断事件（保留语言/实验/错误类型/时间，前后快照相同），
 * 不更新 BKT/独立证据/置信度；系统错误保持冻结；WA/AC 真实观测正常更新；
 * 重复回调幂等不破坏。分类一律走 LearningResultEnum 观测性质，不靠结果字符串逐处判断。
 */
class LearningEventObservationTest {

    private final LearningEventMapper eventMapper = mock(LearningEventMapper.class);
    private final LearningMasteryMapper masteryMapper = mock(LearningMasteryMapper.class);
    private final QuestionService questionService = mock(QuestionService.class);
    private final QuestionSubmitService submitService = mock(QuestionSubmitService.class);
    private final KnowledgeGraphService knowledgeGraphService = mock(KnowledgeGraphService.class);
    private final LearningEventServiceImpl service = new LearningEventServiceImpl();
    private final com.yupi.springbootinit.learning.ExperimentMappingService experimentMappings =
            mock(com.yupi.springbootinit.learning.ExperimentMappingService.class);
    /** 普通题作答路径依赖：节点映射与作答记录（AI 升级 §5.5 证据资格回归用）。 */
    private final com.yupi.springbootinit.mapper.LearningQuestionNodeMapper questionNodeMapper =
            mock(com.yupi.springbootinit.mapper.LearningQuestionNodeMapper.class);
    private final com.yupi.springbootinit.mapper.LearningAttemptMapper attemptMapper =
            mock(com.yupi.springbootinit.mapper.LearningAttemptMapper.class);

    private LearningMastery masteryRow;

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "event-observation-test");
        TableInfoHelper.initTableInfo(assistant, LearningEvent.class);
        TableInfoHelper.initTableInfo(assistant, LearningMastery.class);

        ReflectionTestUtils.setField(service, "learningEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "baseMapper", eventMapper);
        ReflectionTestUtils.setField(service, "learningMasteryMapper", masteryMapper);
        ReflectionTestUtils.setField(service, "questionService", questionService);
        ReflectionTestUtils.setField(service, "questionSubmitService", submitService);
        ReflectionTestUtils.setField(service, "knowledgeGraphService", knowledgeGraphService);
        ReflectionTestUtils.setField(service, "experimentMappings", experimentMappings);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", questionNodeMapper);
        ReflectionTestUtils.setField(service, "learningAttemptMapper", attemptMapper);
        when(experimentMappings.fromSnapshot(any(), any(Long.class))).thenReturn(List.of(
                new com.yupi.springbootinit.learning.ExperimentMappingService.Mapping("os.cpu.scheduling", "进程调度", 1.0)));
        // 判题事务模板：mock 事务管理器同步执行回调（recordJudgeEvents 用编程式事务）
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(service, "judgeTxTemplate", new TransactionTemplate(txManager));

        // 提交与实验题：tags 命中真实节点"进程调度"
        QuestionSubmit submit = new QuestionSubmit();
        submit.setUserId(7L);
        submit.setQuestionId(501L);
        submit.setLanguage("java");
        when(submitService.getById(any(Long.class))).thenReturn(submit);
        Question question = new Question();
        question.setId(501L);
        question.setTitle("进程调度模拟实验");
        question.setTags("[\"进程调度\"]");
        when(questionService.getById(501L)).thenReturn(question);
        when(knowledgeGraphService.findByName("进程调度"))
                .thenReturn(List.of(new com.yupi.springbootinit.model.entity.EntityNode()));

        masteryRow = masteryRow();
        when(masteryMapper.selectOne(any())).thenReturn(masteryRow);
    }

    @Test
    void authoritativeMappingOverridesOldTagsAndAppliesWeightOnce() {
        when(experimentMappings.fromSnapshot(any(), any(Long.class))).thenReturn(List.of(
                new com.yupi.springbootinit.learning.ExperimentMappingService.Mapping("os.memory.lru", "正式实验节点", 0.3)));
        service.recordJudgeEvents(9999L, QuestionSubmitStatusEnum.SUCCEED.getValue(), "Accepted");
        LearningEvent event=capturedEvent();
        assertEquals("正式实验节点",event.getNodeName());
        assertEquals("os.memory.lru",event.getNodeCode());
        double target=BktModel.update(0.1,true,BktModel.DEFAULT_P_LEARN,BktModel.DEFAULT_P_GUESS,BktModel.DEFAULT_P_SLIP);
        assertEquals(0.1+0.3*(target-0.1),event.getMasteryAfter(),1e-10);
        verify(eventMapper).insert(any(LearningEvent.class));
    }

    @Test
    void compileErrorThenWrongAnswerThenAccepted_onlyRealObservationsUpdateMastery() {
        // 1) CE：诊断事件，掌握度前后快照不变
        service.recordJudgeEvents(1001L, QuestionSubmitStatusEnum.SUCCEED.getValue(), "Compile Error");

        assertEquals(0, masteryRow.getPracticeEvidenceCount(), "CE 不得增加实践证据数");
        assertEquals(BktModel.DEFAULT_P_INIT, masteryRow.getPKnown(), 1e-9, "CE 不得更新 BKT");
        verify(masteryMapper, never()).updateById(any());
        verify(masteryMapper, never()).insert(any(LearningMastery.class));

        LearningEvent ceEvent = capturedEvent();
        assertEquals(LearningResultEnum.COMPILE_ERROR.name(), ceEvent.getResult());
        assertEquals(Integer.valueOf(0), ceEvent.getIsFirst(), "诊断事件不是独立观测");
        assertEquals(ceEvent.getMasteryBefore(), ceEvent.getMasteryAfter(), "CE 前后掌握度相同");
        assertEquals(ceEvent.getSnapshotBefore(), ceEvent.getSnapshotAfter(), "CE 前后快照相同");
        assertEvidenceDetail(ceEvent, "Compile Error");
        clearInvocations(eventMapper, masteryMapper);

        // 2) WA：真实失败观测，实践证据 0 -> 1，节点状态应为薄弱（RED）。
        // fix round 3（裁决 L3-R12）：按阶段一冻结的 5 参 BKT
        // （pInit=0.1 / pLearn=0.1 / pGuess=0.2 / pSlip=0.1），答错同样叠加学习转移：
        // 后验 = 0.1*0.1/(0.1*0.1+0.9*0.8)=0.0137，pKnown = 0.0137+(1-0.0137)*0.1≈0.1123。
        // 教科书 BKT 在低先验时答错可轻微上升，这是公式性质而非 bug——历史验收
        // （验收记录-2026-09-29 §3："学生 A 答错 → 后验 0.10→0.112，节点变 RED"）同源。
        service.recordJudgeEvents(1002L, QuestionSubmitStatusEnum.SUCCEED.getValue(), "Wrong Answer");

        assertEquals(1, masteryRow.getPracticeEvidenceCount(), "WA 计入失败证据");
        assertEquals("RED", masteryRow.getStatus(), "WA 后节点应为薄弱（RED）");
        assertEquals(0.1123, masteryRow.getPKnown(), 0.001,
                "教科书 BKT 含学习转移：后验 0.0137+(1-0.0137)*0.1≈0.1123（历史验收 0.10→0.112 同源）");
        verify(masteryMapper).updateById(any());
        LearningEvent waEvent = capturedEvent();
        assertEquals(LearningResultEnum.FAIL.name(), waEvent.getResult());
        assertEquals(Integer.valueOf(1), waEvent.getIsFirst());
        assertNotEquals(waEvent.getMasteryBefore(), waEvent.getMasteryAfter());
        assertNotEquals(waEvent.getSnapshotBefore(), waEvent.getSnapshotAfter());
        double afterWrong = masteryRow.getPKnown();
        clearInvocations(eventMapper, masteryMapper);

        // 3) AC：真实通过观测，实践证据 1 -> 2，掌握度回升
        service.recordJudgeEvents(1003L, QuestionSubmitStatusEnum.SUCCEED.getValue(), "Accepted");

        assertEquals(2, masteryRow.getPracticeEvidenceCount());
        assertTrue(masteryRow.getPKnown() > afterWrong, "AC 应提高掌握度");
        LearningEvent acEvent = capturedEvent();
        assertEquals(LearningResultEnum.PASS.name(), acEvent.getResult());
    }

    @Test
    void systemErrorAloneStaysFrozen() {
        service.recordJudgeEvents(1004L, QuestionSubmitStatusEnum.FAILED.getValue(), "System Error");

        LearningEvent event = capturedEvent();
        assertEquals(LearningResultEnum.SYSTEM_ERROR.name(), event.getResult());
        assertEquals(Integer.valueOf(0), event.getIsFirst());
        assertEquals(event.getMasteryBefore(), event.getMasteryAfter());
        assertEquals(event.getSnapshotBefore(), event.getSnapshotAfter());
        assertEquals(BktModel.DEFAULT_P_INIT, masteryRow.getPKnown(), 1e-9);
        assertEquals(0, masteryRow.getPracticeEvidenceCount());
        verify(masteryMapper, never()).updateById(any());
        verify(masteryMapper, never()).insert(any(LearningMastery.class));
    }

    @Test
    void duplicateCallbackIsIdempotentAndDoesNotUpdateMastery() {
        when(eventMapper.insert(any(LearningEvent.class)))
                .thenThrow(new DuplicateKeyException("duplicate idempotency key"));

        assertDoesNotThrow(() -> service.recordJudgeEvents(
                1005L, QuestionSubmitStatusEnum.SUCCEED.getValue(), "Accepted"));

        verify(masteryMapper, never()).updateById(any());
        verify(masteryMapper, never()).insert(any(LearningMastery.class));
        assertEquals(BktModel.DEFAULT_P_INIT, masteryRow.getPKnown(), 1e-9);
        assertEquals(0, masteryRow.getPracticeEvidenceCount());
    }

    @Test
    void classifyJudgeResultUsesObservationNatureNotRawString() {
        assertEquals(LearningResultEnum.SYSTEM_ERROR,
                LearningEventServiceImpl.classifyJudgeResult(QuestionSubmitStatusEnum.SUCCEED.getValue(), "System Error"));
        assertEquals(LearningResultEnum.SYSTEM_ERROR,
                LearningEventServiceImpl.classifyJudgeResult(QuestionSubmitStatusEnum.SUCCEED.getValue(), "系统错误"));
        assertEquals(LearningResultEnum.PASS,
                LearningEventServiceImpl.classifyJudgeResult(
                        QuestionSubmitStatusEnum.SUCCEED.getValue(), "Accepted"));
        assertEquals(LearningResultEnum.FAIL,
                LearningEventServiceImpl.classifyJudgeResult(
                        QuestionSubmitStatusEnum.SUCCEED.getValue(), "Wrong Answer"));
        assertEquals(LearningResultEnum.COMPILE_ERROR,
                LearningEventServiceImpl.classifyJudgeResult(
                        QuestionSubmitStatusEnum.SUCCEED.getValue(), "Compile Error"));
        assertEquals(LearningResultEnum.SYSTEM_ERROR,
                LearningEventServiceImpl.classifyJudgeResult(
                        QuestionSubmitStatusEnum.FAILED.getValue(), "Compile Error"));
        assertEquals(LearningResultEnum.SYSTEM_ERROR,
                LearningEventServiceImpl.classifyJudgeResult(
                        QuestionSubmitStatusEnum.FAILED.getValue(), "Accepted"));
        assertTrue(LearningResultEnum.COMPILE_ERROR.isDiagnostic());
        assertTrue(LearningResultEnum.SYSTEM_ERROR.isDiagnostic());
        assertTrue(LearningResultEnum.FAIL.isKnowledgeObservation(), "FAIL 是独立知识观测");
        assertTrue(LearningResultEnum.PASS.isKnowledgeObservation());
        assertFalse(LearningResultEnum.PASS.isDiagnostic());
        assertFalse(LearningResultEnum.BASELINE.isKnowledgeObservation());
    }

    // region AI 升级 §5.5：帮助等级参与证据资格（普通题作答路径）

    @Test
    void unhelpedFirstAttemptCountsAsIndependentObservation() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true,
                EvidenceQualification.of(555L, EvidenceQualification.HELP_NONE, null));

        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(1), event.getIsFirst(), "无帮助的首次作答是独立观测");
        assertEquals(1, masteryRow.getConceptEvidenceCount(), "概念证据数 +1");
        assertNotEquals(BktModel.DEFAULT_P_INIT, masteryRow.getPKnown(), 1e-9, "BKT 应更新");
        verify(masteryMapper).updateById(any());
        JSONObject evidence = JSONUtil.parseObj(event.getEvidence());
        assertEquals("NONE", evidence.getStr("helpLevel"));
        assertTrue(evidence.getBool("firstAttempt"));
        assertTrue(evidence.getBool("eligibleForObservation"));
    }

    @Test
    void hintedFirstAttemptIsRecordedButDoesNotTouchMastery() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true,
                EvidenceQualification.of(555L, EvidenceQualification.HELP_HINT, null));

        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(0), event.getIsFirst(), "带提示的作答不是独立观测");
        assertEquals(event.getMasteryBefore(), event.getMasteryAfter(), "掌握度前后相同");
        assertEquals(event.getSnapshotBefore(), event.getSnapshotAfter(), "快照前后相同");
        assertEquals(0, masteryRow.getConceptEvidenceCount(), "不得增加概念证据数");
        assertEquals(BktModel.DEFAULT_P_INIT, masteryRow.getPKnown(), 1e-9, "不得更新 BKT");
        verify(masteryMapper, never()).updateById(any());
        JSONObject evidence = JSONUtil.parseObj(event.getEvidence());
        assertEquals("HINT", evidence.getStr("helpLevel"));
        assertTrue(evidence.getBool("firstAttempt"), "账本仍保留'确实是第一次做这道题'的事实");
        assertFalse(evidence.getBool("eligibleForObservation"));
        assertTrue(evidence.getStr("note").contains("已获得提示"), "事件文案要说明为何不计观测");
        assertTrue(evidence.getStr("eligibilityReason").contains("提示"));
    }

    @Test
    void revealedAnswerFirstAttemptIsRecordedButDoesNotTouchMastery() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true,
                EvidenceQualification.of(555L, EvidenceQualification.HELP_ANSWER_REVEALED, "EXTERNAL_AI"));

        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(0), event.getIsFirst(), "看过答案后的正确表现不等于能力");
        assertEquals(0, masteryRow.getConceptEvidenceCount());
        verify(masteryMapper, never()).updateById(any());
        JSONObject evidence = JSONUtil.parseObj(event.getEvidence());
        assertEquals("ANSWER_REVEALED", evidence.getStr("helpLevel"));
        assertEquals("EXTERNAL_AI", evidence.getStr("selfReportedHelp"));
        assertTrue(evidence.getStr("note").contains("已揭示答案"));
        assertEquals("555", evidence.getStr("evidenceContextId"));
    }

    @Test
    void unknownHelpLevelKeepsLegacyObservationBehaviour() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, false, true,
                EvidenceQualification.unknown());

        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(1), event.getIsFirst(),
                "缺少帮助记录不倒推成未获得帮助，但保持升级前的观测行为");
        assertEquals(1, masteryRow.getConceptEvidenceCount());
        verify(masteryMapper).updateById(any());
        JSONObject evidence = JSONUtil.parseObj(event.getEvidence());
        assertEquals("UNKNOWN", evidence.getStr("helpLevel"));
    }

    @Test
    void fiveArgumentLegacyEntryDelegatesWithUnknownHelpLevel() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true);
        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(1), event.getIsFirst());
        assertEquals("UNKNOWN", JSONUtil.parseObj(event.getEvidence()).getStr("helpLevel"));
    }

    @Test
    void nullQualificationIsTreatedAsUnknownNotNullPointer() {
        stubAttemptMapping();
        assertDoesNotThrow(() -> service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true, null));
        assertEquals("UNKNOWN", JSONUtil.parseObj(capturedEvent().getEvidence()).getStr("helpLevel"));
    }

    @Test
    void repeatAttemptNeverUpdatesMasteryWhateverTheHelpLevel() {
        stubAttemptMapping();
        service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, false,
                EvidenceQualification.of(555L, EvidenceQualification.HELP_NONE, null));
        LearningEvent event = capturedEvent();
        assertEquals(Integer.valueOf(0), event.getIsFirst());
        assertEquals(0, masteryRow.getConceptEvidenceCount());
        verify(masteryMapper, never()).updateById(any());
        assertTrue(JSONUtil.parseObj(event.getEvidence()).getStr("note").contains("重复作答"));
    }

    @Test
    void unmappedQuestionRecordsNothing() {
        when(questionNodeMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.recordAttemptEvents(7L, attemptQuestion(), 88L, true, true,
                EvidenceQualification.of(555L, EvidenceQualification.HELP_NONE, null)).isEmpty());
        verify(eventMapper, never()).insert(any(LearningEvent.class));
        verify(masteryMapper, never()).updateById(any());
    }

    @Test
    void eventIdsOfAttemptReadsOnlyThatUsersEventGroup() {
        LearningEvent first = new LearningEvent();
        first.setId(9001L);
        LearningEvent second = new LearningEvent();
        second.setId(9002L);
        when(eventMapper.selectList(any())).thenReturn(List.of(first, second));
        assertEquals(List.of(9001L, 9002L), service.eventIdsOfAttempt(7L, 88L));
        assertTrue(service.eventIdsOfAttempt(7L, null).isEmpty());
        assertTrue(service.eventIdsOfAttempt(null, 88L).isEmpty());
    }

    private LearningQuestion attemptQuestion() {
        LearningQuestion question = new LearningQuestion();
        question.setId(101L);
        question.setTitle("LRU 命中后应换出哪一页");
        question.setType("SINGLE");
        question.setAnswer("\"B\"");
        return question;
    }

    private void stubAttemptMapping() {
        LearningQuestionNode mapping = new LearningQuestionNode();
        mapping.setQuestionId(101L);
        mapping.setNodeName("进程调度");
        mapping.setNodeCode("os.cpu.scheduling");
        mapping.setWeight(1.0);
        when(questionNodeMapper.selectList(any())).thenReturn(List.of(mapping));
    }

    // endregion

    // region fixtures

    private LearningMastery masteryRow() {
        LearningMastery mastery = new LearningMastery();
        mastery.setId(1L);
        mastery.setUserId(7L);
        mastery.setNodeName("进程调度");
        mastery.setPKnown(BktModel.DEFAULT_P_INIT);
        mastery.setConfidence(0.0);
        mastery.setConceptScore(0.0);
        mastery.setPracticeScore(0.0);
        mastery.setConceptEvidenceCount(0);
        mastery.setPracticeEvidenceCount(0);
        mastery.setStatus("GRAY");
        return mastery;
    }

    private LearningEvent capturedEvent() {
        ArgumentCaptor<LearningEvent> captor = ArgumentCaptor.forClass(LearningEvent.class);
        verify(eventMapper).insert(captor.capture());
        return captor.getValue();
    }

    /** 事件详情必须保留语言/实验/错误类型（时间由落库补齐） */
    private void assertEvidenceDetail(LearningEvent event, String expectedNote) {
        JSONObject evidence = JSONUtil.parseObj(event.getEvidence());
        assertEquals("java", evidence.getStr("language"));
        assertEquals("进程调度模拟实验", evidence.getStr("title"));
        assertEquals(expectedNote, evidence.getStr("note"));
    }

    // endregion
}
