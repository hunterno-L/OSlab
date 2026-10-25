package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.learning.LearningResourceResolver;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.mapper.LearningNodeContentMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 工作台读取边界测试，不连接数据库，不产生学习事件。 */
class LearningWorkbenchQueryTest {
    private final KnowledgeGraphService graphService = mock(KnowledgeGraphService.class);
    private final LearningMasteryMapper masteryMapper = mock(LearningMasteryMapper.class);
    private final LearningEventMapper eventMapper = mock(LearningEventMapper.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final LearningQuestionService questionService = mock(LearningQuestionService.class);
    private final QuestionSubmitService submitService = mock(QuestionSubmitService.class);
    private final LearningResourceResolver resolver = mock(LearningResourceResolver.class);
    private final LearningMasteryServiceImpl service = new LearningMasteryServiceImpl();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "workbench-test");
        TableInfoHelper.initTableInfo(assistant, QuestionSubmit.class);
        TableInfoHelper.initTableInfo(assistant, LearningMastery.class);
        TableInfoHelper.initTableInfo(assistant, LearningEvent.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);
        user.setId(7L);
        ReflectionTestUtils.setField(service, "knowledgeGraphService", graphService);
        ReflectionTestUtils.setField(service, "learningMasteryMapper", masteryMapper);
        ReflectionTestUtils.setField(service, "learningEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "learningQuestionService", questionService);
        ReflectionTestUtils.setField(service, "questionSubmitService", submitService);
        ReflectionTestUtils.setField(service, "resourceResolver", resolver);
        ReflectionTestUtils.setField(service, "learningNodeContentMapper", mock(LearningNodeContentMapper.class));
    }

    @Test
    void oneSnapshotAndBatchedReadsServeAllWorkbenchSections() {
        GraphDataDTO graph = new GraphDataDTO();
        graph.setNodes(List.of(node("1", "LRU"), node("2", "页面置换"), node("3", "后继")));
        graph.setLinks(List.of(edge("1", "2"), edge("3", "1")));
        when(graphService.getGraphSnapshot()).thenReturn(graph);
        LearningMastery mastery = new LearningMastery();
        mastery.setNodeName("LRU");
        mastery.setStatus("RED");
        mastery.setPKnown(0.3);
        mastery.setConfidence(0.2);
        mastery.setConceptEvidenceCount(1);
        mastery.setPracticeEvidenceCount(0);
        when(masteryMapper.selectList(any())).thenReturn(List.of(mastery));
        LearningEvent failed = event(10L, "FAIL");
        LearningEvent corrected = event(11L, "PASS");
        when(eventMapper.selectList(any())).thenReturn(List.of(failed, corrected));

        LearningQuestionNode firstMapping = new LearningQuestionNode();
        firstMapping.setQuestionId(101L);
        LearningQuestionNode secondMapping = new LearningQuestionNode();
        secondMapping.setQuestionId(102L);
        when(nodeMapper.selectList(any())).thenReturn(List.of(firstMapping, secondMapping));
        LearningQuestionVO firstQuestion = new LearningQuestionVO();
        firstQuestion.setId(101L);
        firstQuestion.setHasAttempted(true);
        firstQuestion.setType("JUDGE");
        LearningQuestionVO secondQuestion = new LearningQuestionVO();
        secondQuestion.setId(102L);
        secondQuestion.setHasAttempted(false);
        secondQuestion.setType("SINGLE");
        when(questionService.getQuestionVOs(List.of(101L, 102L), user))
                .thenReturn(List.of(firstQuestion, secondQuestion));
        Question firstExperiment = new Question();
        firstExperiment.setId(201L);
        firstExperiment.setTitle("实验一");
        Question secondExperiment = new Question();
        secondExperiment.setId(202L);
        secondExperiment.setTitle("实验二");
        when(resolver.findExperimentsByNode("LRU")).thenReturn(List.of(firstExperiment, secondExperiment));
        QuestionSubmit accepted = new QuestionSubmit();
        accepted.setQuestionId(201L);
        accepted.setJudgeInfo("{\"message\":\"Accepted\"}");
        QuestionSubmit pending = new QuestionSubmit();
        pending.setQuestionId(202L);
        pending.setStatus(1);
        when(submitService.list(any())).thenReturn(List.of(accepted, pending));

        NodeWorkbenchVO workbench = service.nodeWorkbench(user, "LRU");

        assertEquals("内存管理", workbench.getMastery().getChapter());
        assertEquals("PASS", workbench.getMastery().getLastResult());
        assertEquals(11L, workbench.getEvents().get(0).getId());
        assertEquals("CORRECTED", workbench.getLastFailure().getFailureState());
        assertEquals(List.of("页面置换"), workbench.getPrerequisiteNodes());
        assertEquals("后继", workbench.getSuccessors().get(0).getName());
        assertEquals(2, workbench.getQuestionStats().getTotal());
        assertEquals(1, workbench.getQuestionStats().getAttempted());
        assertEquals("Accepted", workbench.getExperiments().get(0).getLastResult());
        assertEquals("判题中", workbench.getExperiments().get(1).getLastResult());
        verify(graphService, times(1)).getGraphSnapshot();
        verifyNoMoreInteractions(graphService);
        verify(masteryMapper, times(1)).selectList(any());
        verify(eventMapper, times(1)).selectList(any());
        verify(eventMapper, never()).selectById(any());
        verify(questionService, times(1)).getQuestionVOs(List.of(101L, 102L), user);
        verify(questionService, never()).getQuestionVO(anyLong(), any());
        verify(submitService, times(1)).list(any());

        ArgumentCaptor<LambdaQueryWrapper<QuestionSubmit>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(submitService).list(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("NOT EXISTS"), "只读取各题最新提交，不回传全部历史");
        assertTrue(sql.contains("newer.userId = question_submit.userId"));
        assertTrue(sql.contains("newer.isDelete = 0"));
        assertTrue(sql.contains("newer.id > question_submit.id"), "同时间戳按 id 稳定判定");
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(201L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(202L));
        assertFalse(query.getValue().getSqlSelect().contains("code"), "不读取提交源码");
    }

    @Test
    void unknownNodeStopsBeforeRelationalQueries() {
        GraphDataDTO graph = new GraphDataDTO();
        graph.setNodes(List.of(node("1", "已存在")));
        when(graphService.getGraphSnapshot()).thenReturn(graph);
        assertThrows(BusinessException.class, () -> service.nodeWorkbench(user, "不存在"));
        verify(graphService).getGraphSnapshot();
        verifyNoInteractions(masteryMapper, eventMapper, nodeMapper, questionService, submitService, resolver);
    }

    @Test
    void stableCodeLoadsFrozenEvidenceAfterRenameAndCanonicalPrerequisites() {
        com.yupi.springbootinit.learning.NodeIdentityService identities=mock(com.yupi.springbootinit.learning.NodeIdentityService.class);
        when(identities.resolve("os.memory.lru")).thenReturn(new com.yupi.springbootinit.learning.NodeIdentityService.Identity("os.memory.lru","最近最少使用","LRU"));
        when(identities.requireCode("os.memory.lru")).thenReturn(new com.yupi.springbootinit.learning.NodeIdentityService.Identity("os.memory.lru","最近最少使用","LRU"));
        ReflectionTestUtils.setField(service,"nodeIdentities",identities);
        GraphDataDTO graph=new GraphDataDTO();NodeDTO renamed=node("1","LRU");renamed.setNodeCode("os.memory.lru");renamed.setDisplayName("最近最少使用");
        graph.setNodes(List.of(renamed,node("2","页面置换")));LinkDTO requires=edge("1","2");requires.setRelationType("REQUIRES");graph.setLinks(List.of(requires));
        when(graphService.getGraphSnapshot()).thenReturn(graph);
        LearningMastery mastery=new LearningMastery();mastery.setNodeName("LRU");mastery.setPKnown(.3);mastery.setStatus("RED");mastery.setConceptEvidenceCount(1);
        when(masteryMapper.selectList(any())).thenReturn(List.of(mastery));when(eventMapper.selectList(any())).thenReturn(List.of(event(10L,"FAIL")));
        when(nodeMapper.selectList(any())).thenReturn(List.of());when(questionService.getQuestionVOs(List.of(),user)).thenReturn(List.of());when(resolver.findExperimentsByNode("LRU")).thenReturn(List.of());
        NodeWorkbenchVO workbench=service.nodeWorkbench(user,"os.memory.lru");
        assertEquals(.3,workbench.getMastery().getPKnown());assertEquals("最近最少使用",workbench.getMastery().getDisplayName());assertEquals(10L,workbench.getEvents().get(0).getId());
        assertEquals("页面置换",workbench.getPrerequisites().get(0).getName());
        ArgumentCaptor<LambdaQueryWrapper<LearningEvent>> query=ArgumentCaptor.forClass(LambdaQueryWrapper.class);verify(eventMapper).selectList(query.capture());query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("LRU"));
        assertFalse(query.getValue().getParamNameValuePairs().containsValue("最近最少使用"));
    }

    @Test void duplicateLegacyNamesNeverPickArbitrarily() {
        GraphDataDTO graph=new GraphDataDTO();graph.setNodes(List.of(node("1","FCFS"),node("2","FCFS")));when(graphService.getGraphSnapshot()).thenReturn(graph);
        assertThrows(BusinessException.class,()->service.nodeWorkbench(user,"FCFS"));verifyNoInteractions(masteryMapper,eventMapper);
    }

    private NodeDTO node(String id, String name) {
        NodeDTO node = new NodeDTO();
        node.setId(id);
        node.setName(name);
        node.setChapter("内存管理");
        node.setCategory("基础");
        node.setDescription("真实节点描述");
        return node;
    }

    private LinkDTO edge(String source, String target) {
        LinkDTO link = new LinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setRelationType("dependent");
        return link;
    }

    private LearningEvent event(Long id, String result) {
        LearningEvent event = new LearningEvent();
        event.setId(id);
        event.setNodeName("LRU");
        event.setUserId(7L);
        event.setEventType("ATTEMPT");
        event.setResourceType("LEARNING_QUESTION");
        event.setResourceId(101L);
        event.setResult(result);
        event.setIsFirst(id == 10L ? 1 : 0);
        event.setCreateTime(new Date(id * 1000));
        return event;
    }
}
