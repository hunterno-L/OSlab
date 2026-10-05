package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.bkt.BktModel;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
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
