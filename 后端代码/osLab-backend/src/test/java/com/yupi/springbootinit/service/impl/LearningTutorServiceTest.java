package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.learning.TutorAskRequest;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningActionVO;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.model.vo.learning.PathItemVO;
import com.yupi.springbootinit.model.vo.learning.TutorAnswerVO;
import com.yupi.springbootinit.model.vo.learning.TutorSourceVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningPathService;
import com.yupi.springbootinit.service.QuestionService;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阶段 5 §5.4 Tutor 证据配额/来源/行动单元回归：
 * 分组配额保证本人失败/提交记录、已发布内容/前置知识、可执行下一步不被邻居/题目挤掉；
 * 概念/错误/下一步三类问题按意图分配来源；actionType/targetId 结构化且与引用一致；
 * 无 AI key 时仍返回规则化来源与下一步动作；他人事件绝不进入证据包。
 */
class LearningTutorServiceTest {

    private static final String SEED = "进程调度";
    private static final List<String> NEIGHBORS = List.of("LRU", "FIFO", "时间片轮转", "多级反馈队列");

    private final KnowledgeGraphService knowledgeGraphService = mock(KnowledgeGraphService.class);
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final LearningEventMapper eventMapper = mock(LearningEventMapper.class);
    private final QuestionService questionService = mock(QuestionService.class);
    private final LearningPathService pathService = mock(LearningPathService.class);
    private final com.yupi.springbootinit.learning.ExperimentMappingService experiments = mock(com.yupi.springbootinit.learning.ExperimentMappingService.class);
    private final LearningTutorServiceImpl service = new LearningTutorServiceImpl();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "tutor-quota-test");
        TableInfoHelper.initTableInfo(assistant, LearningEvent.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);

        ReflectionTestUtils.setField(service, "knowledgeGraphService", knowledgeGraphService);
        ReflectionTestUtils.setField(service, "learningQuestionMapper", questionMapper);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "learningEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "questionService", questionService);
        ReflectionTestUtils.setField(service, "learningPathService", pathService);
        ReflectionTestUtils.setField(service, "experimentMappings", experiments);
        // 不配置 AI key：arkService 保持 null，ask() 走规则降级（测试不依赖外部 AI）

        user.setId(7L);
        stubCommonScene();
    }

    /** 四邻居 + 六道已发布题目 + 本人失败/通过/编译错误事件 + 一个实验 + 路径下一步 */
    private void stubCommonScene() {
        when(knowledgeGraphService.findByName(SEED))
                .thenReturn(List.of(graphNode(SEED, "进程调度负责决定 CPU 在可运行进程间的分配")));
        when(knowledgeGraphService.getAllRelatedNames(SEED)).thenReturn(new ArrayList<>(NEIGHBORS));
        for (String name : NEIGHBORS) {
            when(knowledgeGraphService.findByName(name)).thenReturn(List.of(graphNode(name, name + " 的节点说明")));
        }
        List<LearningQuestionNode> mappings = new ArrayList<>();
        List<LearningQuestion> questions = new ArrayList<>();
        for (long id = 101; id <= 106; id++) {
            LearningQuestionNode mapping = new LearningQuestionNode();
            mapping.setQuestionId(id);
            mapping.setNodeName(SEED);
            mappings.add(mapping);
            LearningQuestion question = new LearningQuestion();
            question.setId(id);
            question.setTitle("题目" + id);
            question.setContent("题干" + id);
            question.setAnalysis("解析" + id);
            question.setReviewStatus("PUBLISHED");
            question.setIsDelete(0);
            questions.add(question);
        }
        when(nodeMapper.selectList(any())).thenReturn(mappings);
        when(questionMapper.selectList(any())).thenReturn(questions);
        // 时间倒序返回（最新在前）：FAIL 在中间，验证失败优先的排序保留
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event(902L, "PASS"),
                event(901L, "FAIL"),
                event(903L, "COMPILE_ERROR")));
        Question experiment = new Question();
        experiment.setId(501L);
        experiment.setTitle("实现一个进程调度器");
        experiment.setContent("按时间片轮转实现调度器");
        experiment.setTags("[\"" + SEED + "\"]");
        when(questionService.list()).thenReturn(List.of(experiment));
        when(experiments.experiments(SEED)).thenReturn(List.of(experiment));
        when(pathService.buildPath(eq(user), eq(SEED))).thenReturn(nextStepPath());
    }

    @Test
    void conceptQuestionKeepsQuotaAndGuaranteedSourcesWithinEight() {
        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        assertEquals("FALLBACK", vo.getMode(), "无 AI key 时必须降级而非报错");
        List<TutorSourceVO> sources = vo.getSources();
        assertEquals(8, sources.size(), "概念问题各分组配额恰好填满 8 条上限");
        // 引用 [1]：当前节点保证位
        assertEquals("NODE", sources.get(0).getSourceType());
        assertEquals(SEED, sources.get(0).getSourceId());
        assertEquals("NODE", sources.get(0).getActionType());
        assertEquals(SEED, sources.get(0).getTargetId());
        // 本人失败记录在列（配额保证），且优先取 FAIL 事件
        TutorSourceVO eventSource = onlyEvent(sources);
        assertNotNull(eventSource, "本人失败/提交记录不得被邻居与题目挤掉");
        assertEquals("901", eventSource.getTargetId());
        assertEquals("EVENT", eventSource.getActionType());
        // 可执行下一步保证位（来自既有 LearningPathService）
        TutorSourceVO nextStep = nextStepSource(sources);
        assertNotNull(nextStep);
        assertEquals("NODE", nextStep.getActionType());
        assertEquals("LRU", nextStep.getTargetId());
        // 结构化行动字段：类型合法、targetId/targetUrl 至少其一、与引用编号一一对应
        assertStructuredActionFields(sources);
        // 已发布内容在列（QUESTION 引用为数字题目 id，可直跳）
        assertTrue(sources.stream().anyMatch(s -> "QUESTION".equals(s.getSourceType())));
        sources.stream().filter(s -> "QUESTION".equals(s.getSourceType()))
                .forEach(s -> assertDoesNotThrow(() -> Long.parseLong(s.getTargetId())));
        assertTrue(vo.getAnswer().contains("可执行下一步"));
    }

    @Test
    void errorDiagnosisQuestionPutsUserFailuresFirst() {
        TutorAnswerVO vo = service.ask(ask("为什么我的提交总是报错？"), user);

        List<TutorSourceVO> sources = vo.getSources();
        // 错误诊断：种子之后紧跟本人失败（901 FAIL）与编译错误诊断（903）
        assertEquals("EVENT", sources.get(1).getSourceType());
        assertEquals("901", sources.get(1).getTargetId());
        assertEquals("EVENT", sources.get(2).getSourceType());
        assertEquals("903", sources.get(2).getTargetId());
        // 邻居与题目让位：各保留 1 条（下一步动作也是 NODE 形态，按标题排除）
        assertEquals(1, sources.stream().filter(s -> "QUESTION".equals(s.getSourceType())).count());
        assertEquals(1, sources.stream()
                .filter(s -> "NODE".equals(s.getSourceType())
                        && !SEED.equals(s.getSourceId())
                        && (s.getTitle() == null || !s.getTitle().startsWith("下一步")))
                .count());
        assertEquals(7, sources.size());
        assertStructuredActionFields(sources);
    }

    @Test
    void nextStepQuestionLeadsWithPathAction() {
        TutorAnswerVO vo = service.ask(ask("下一步应该先学什么？"), user);

        List<TutorSourceVO> sources = vo.getSources();
        TutorSourceVO nextStep = nextStepSource(sources);
        assertNotNull(nextStep);
        assertEquals(1, sources.indexOf(nextStep), "下一步问题：路径动作紧跟当前节点");
        assertEquals("LRU", nextStep.getTargetId());
        assertTrue(vo.getAnswer().contains("可执行下一步"));
    }

    @Test
    void missingEventsStillYieldSourcesAndNextStep() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        List<TutorSourceVO> sources = vo.getSources();
        assertFalse(sources.stream().anyMatch(s -> "EVENT".equals(s.getSourceType())));
        assertEquals(7, sources.size(), "无本人事件时其余保证位仍在");
        assertNotNull(nextStepSource(sources));
        assertEquals("FALLBACK", vo.getMode());
    }

    @Test
    void pathUnavailableFallsBackWithoutFabricatedAction() {
        when(pathService.buildPath(any(User.class), any(String.class)))
                .thenThrow(new RuntimeException("neo4j down"));

        TutorAnswerVO vo = service.ask(ask("下一步应该先学什么？"), user);

        assertTrue(vo.getSources().stream().noneMatch(s -> s.getTitle() != null
                && s.getTitle().startsWith("下一步")));
        assertTrue(vo.getAnswer().contains("暂无可生成的路径动作"),
                "无路径时不伪造动作，降级为规则建议");
        assertTrue(vo.getRetrievalTrace().stream().anyMatch(line -> line.contains("学习路径暂不可用")));
    }

    @Test
    void otherUsersEventsNeverEnterEvidencePack() {
        service.ask(ask("为什么我的提交总是报错？"), user);

        ArgumentCaptor<LambdaQueryWrapper<LearningEvent>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(eventMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment();
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(7L),
                "事件查询必须限定当前用户");
    }

    @Test
    void unknownSeedNodeIsRejected() {
        when(knowledgeGraphService.findByName("不存在的节点")).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.ask(askOfNode("不存在的节点", "什么是进程调度？"), user));
    }

    @Test
    void citationValidationAndIntentClassification() {
        String validated = LearningTutorServiceImpl.validateCitations("[1] ok [3] [99]", 2);
        assertTrue(validated.contains("[1]"));
        assertFalse(validated.contains("[3]"));
        assertFalse(validated.contains("[99]"));

        assertEquals(LearningTutorServiceImpl.TutorIntent.CONCEPT,
                LearningTutorServiceImpl.classifyIntent("什么是进程调度？"));
        assertEquals(LearningTutorServiceImpl.TutorIntent.ERROR_DIAGNOSIS,
                LearningTutorServiceImpl.classifyIntent("为什么总是错误？"));
        assertEquals(LearningTutorServiceImpl.TutorIntent.NEXT_STEP,
                LearningTutorServiceImpl.classifyIntent("下一步先学什么？"));
    }

    @Test void textbookSourceRetainsRealProvenanceAndUsesCodeForSeedLookup() {
        com.yupi.springbootinit.learning.NodeIdentityService identities=mock(com.yupi.springbootinit.learning.NodeIdentityService.class);
        com.yupi.springbootinit.learning.PublishedLearningContentService published=mock(com.yupi.springbootinit.learning.PublishedLearningContentService.class);
        ReflectionTestUtils.setField(service,"nodeIdentities",identities);ReflectionTestUtils.setField(service,"publishedContent",published);
        when(identities.storageKey("os.cpu.scheduler")).thenReturn(SEED);
        when(identities.byStorageKey(SEED)).thenReturn(new com.yupi.springbootinit.learning.NodeIdentityService.Identity("os.cpu.scheduler","处理机调度",SEED));
        when(knowledgeGraphService.findByName("os.cpu.scheduler")).thenReturn(List.of(graphNode(SEED,"节点概要")));
        when(knowledgeGraphService.getAllRelatedNames("os.cpu.scheduler")).thenReturn(List.of(SEED));
        TutorSourceVO real=new TutorSourceVO();real.setSourceType("TEXTBOOK");real.setActionType("RESOURCE");real.setParentChunkCode("chunk.scheduler");real.setSourceId("chunk.scheduler");real.setTargetId("chunk.scheduler");real.setTitle("教材调度机制");real.setRevision(2);real.setSnippet("正文事实片段");real.setSourceRef(java.util.Map.of("pageStart",42,"locator","2.2.5"));
        when(published.sources(eq(List.of("os.cpu.scheduler")),any(String.class),eq(2))).thenReturn(List.of(real));
        TutorAskRequest request=ask("什么是调度？");request.setNodeCode("os.cpu.scheduler");TutorAnswerVO answer=service.ask(request,user);
        assertTrue(answer.getSources().contains(real));assertTrue(answer.getAnswer().contains("正文事实片段"));
        assertEquals(42,answer.getSources().stream().filter(s->s.getParentChunkCode()!=null).findFirst().get().getSourceRef().get("pageStart"));
        org.mockito.Mockito.verify(knowledgeGraphService).findByName("os.cpu.scheduler");
        org.mockito.Mockito.verify(questionService,org.mockito.Mockito.never()).list();
    }

    // region 旧入口委托统一服务（计划 §6.2 / 任务书 §4）

    /** 旧入口的模型调用必须走统一网关，不再持有第二套 Ark 客户端。 */
    @Test
    void oldEntryRoutesModelCallsThroughTheUnifiedGateway() {
        com.yupi.springbootinit.ai.ModelGateway gateway = mock(com.yupi.springbootinit.ai.ModelGateway.class);
        com.yupi.springbootinit.ai.profile.ModelProfileService profiles =
                mock(com.yupi.springbootinit.ai.profile.ModelProfileService.class);
        com.yupi.springbootinit.ai.ModelEndpoint endpoint =
                com.yupi.springbootinit.ai.ModelEndpoint.platform(
                        "https://ark.cn-beijing.volces.com/api/v3", "deepseek-v3-250324", "sk-test");
        when(profiles.resolveEndpoint(7L, null)).thenReturn(endpoint);
        when(gateway.available(endpoint)).thenReturn(true);
        com.yupi.springbootinit.ai.ModelGateway.ChatResult chat =
                new com.yupi.springbootinit.ai.ModelGateway.ChatResult();
        chat.setContent("先看节点概要，再看你的失败记录 [1]，越界引用 [9] 应被移除");
        when(gateway.chat(eq(endpoint), any())).thenReturn(chat);
        ReflectionTestUtils.setField(service, "modelGateway", gateway);
        ReflectionTestUtils.setField(service, "modelProfileService", profiles);

        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        assertEquals("AI", vo.getMode());
        assertTrue(vo.getAnswer().contains("[1]"));
        assertFalse(vo.getAnswer().contains("[9]"), "引用校验仍然生效：越界编号必须被移除");
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1)).chat(eq(endpoint), any());
        assertTrue(vo.getRetrievalTrace().contains("校验回答引用只指向本次检索来源"));
    }

    /** 平台模型未配置时如实降级，响应结构与升级前一致。 */
    @Test
    void oldEntryDegradesToRuleAnswerWhenThePlatformModelIsUnconfigured() {
        com.yupi.springbootinit.ai.ModelGateway gateway = mock(com.yupi.springbootinit.ai.ModelGateway.class);
        com.yupi.springbootinit.ai.profile.ModelProfileService profiles =
                mock(com.yupi.springbootinit.ai.profile.ModelProfileService.class);
        when(profiles.resolveEndpoint(7L, null)).thenThrow(
                new BusinessException(com.yupi.springbootinit.common.ErrorCode.API_REQUEST_ERROR,
                        "平台默认模型未配置（缺少 AI_ARK_API_KEY）"));
        ReflectionTestUtils.setField(service, "modelGateway", gateway);
        ReflectionTestUtils.setField(service, "modelProfileService", profiles);

        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        assertEquals("FALLBACK", vo.getMode());
        assertTrue(StringUtils.isNotBlank(vo.getAnswer()), "降级也必须给出可用的规则回答");
        assertTrue(vo.getRetrievalTrace().contains("未配置 AI 密钥，使用规则降级回答"));
        org.mockito.Mockito.verifyNoInteractions(gateway);
    }

    /** 模型调用失败不得编造回答，也不得把失败包装成 AI 模式。 */
    @Test
    void oldEntryDegradesWhenTheModelCallFails() {
        com.yupi.springbootinit.ai.ModelGateway gateway = mock(com.yupi.springbootinit.ai.ModelGateway.class);
        com.yupi.springbootinit.ai.profile.ModelProfileService profiles =
                mock(com.yupi.springbootinit.ai.profile.ModelProfileService.class);
        com.yupi.springbootinit.ai.ModelEndpoint endpoint =
                com.yupi.springbootinit.ai.ModelEndpoint.platform(
                        "https://ark.cn-beijing.volces.com/api/v3", "deepseek-v3-250324", "sk-test");
        when(profiles.resolveEndpoint(7L, null)).thenReturn(endpoint);
        when(gateway.available(endpoint)).thenReturn(true);
        when(gateway.chat(eq(endpoint), any())).thenThrow(
                new com.yupi.springbootinit.ai.ModelGateway.ModelCallException(
                        "模型调用失败：鉴权失败（HTTP 401）", false));
        ReflectionTestUtils.setField(service, "modelGateway", gateway);
        ReflectionTestUtils.setField(service, "modelProfileService", profiles);

        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        assertEquals("FALLBACK", vo.getMode());
        assertTrue(StringUtils.isNotBlank(vo.getAnswer()));
        assertTrue(vo.getRetrievalTrace().contains("模型调用失败，使用规则降级回答"));
    }

    /** 旧响应契约不变：answer/sources/retrievalTrace/mode 四个字段，来源不超过 8 条。 */
    @Test
    void oldEntryKeepsTheLegacyResponseShape() {
        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);
        assertNotNull(vo.getAnswer());
        assertNotNull(vo.getSources());
        assertNotNull(vo.getRetrievalTrace());
        assertEquals("FALLBACK", vo.getMode());
        assertTrue(vo.getSources().size() <= 8);
        assertStructuredActionFields(vo.getSources());
    }

    /** 统一检索命中时，教材片段来自联合检索且保留正文块、版本与出处。 */
    @Test
    void oldEntryUsesUnifiedRetrievalForTextbookSourcesWhenTheIndexIsReady() {
        com.yupi.springbootinit.ai.retrieval.HybridRetrievalService retrieval =
                mock(com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.class);
        com.yupi.springbootinit.ai.plan.AiContentScopeService scope =
                mock(com.yupi.springbootinit.ai.plan.AiContentScopeService.class);
        com.yupi.springbootinit.learning.PublishedLearningContentService published =
                mock(com.yupi.springbootinit.learning.PublishedLearningContentService.class);
        com.yupi.springbootinit.learning.NodeIdentityService identities =
                mock(com.yupi.springbootinit.learning.NodeIdentityService.class);
        ReflectionTestUtils.setField(service, "retrieval", retrieval);
        ReflectionTestUtils.setField(service, "contentScope", scope);
        ReflectionTestUtils.setField(service, "publishedContent", published);
        ReflectionTestUtils.setField(service, "nodeIdentities", identities);

        when(identities.storageKey("os.cpu.scheduler")).thenReturn(SEED);
        when(identities.byStorageKey(SEED)).thenReturn(
                new com.yupi.springbootinit.learning.NodeIdentityService.Identity(
                        "os.cpu.scheduler", "处理机调度", SEED));
        when(knowledgeGraphService.findByName("os.cpu.scheduler"))
                .thenReturn(List.of(graphNode(SEED, "节点概要")));
        when(knowledgeGraphService.getAllRelatedNames("os.cpu.scheduler")).thenReturn(List.of(SEED));
        when(scope.courseCodeForNode("os.cpu.scheduler")).thenReturn("course.operating-systems");

        com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Result result =
                new com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Result();
        result.getSources().add(com.yupi.springbootinit.model.vo.ai.SourceRef.of(
                "chunk.scheduler", "COURSE", "course.operating-systems", 2,
                "block.scheduler.1", "2.2.5", "调度机制的正文事实片段"));
        result.setSemanticUsed(true);
        when(retrieval.retrieve(any(), any(String.class))).thenReturn(result);

        TutorAskRequest request = new TutorAskRequest();
        request.setNodeCode("os.cpu.scheduler");
        request.setQuestion("什么是调度？");
        TutorAnswerVO vo = service.ask(request, user);

        TutorSourceVO textbook = vo.getSources().stream()
                .filter(source -> "TEXTBOOK".equals(source.getSourceType())).findFirst().orElse(null);
        assertNotNull(textbook, "统一检索命中后旧响应仍要有教材来源");
        assertEquals("block.scheduler.1", textbook.getParentChunkCode());
        assertEquals(2, textbook.getRevision());
        assertEquals("调度机制的正文事实片段", textbook.getSnippet());
        assertEquals("chunk.scheduler", textbook.getSourceRef().get("sourceId"));
        assertEquals("RESOURCE", textbook.getActionType());
        assertTrue(vo.getRetrievalTrace().stream()
                .anyMatch(line -> line.contains("语义+关键词+图谱")));
        org.mockito.Mockito.verify(published, org.mockito.Mockito.never())
                .sources(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** 个人资料不得混进旧响应：旧契约只下发课程正文。 */
    @Test
    void oldEntryNeverMixesPersonalMaterialsIntoTheLegacyResponse() {
        com.yupi.springbootinit.ai.retrieval.HybridRetrievalService retrieval =
                mock(com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.class);
        com.yupi.springbootinit.ai.plan.AiContentScopeService scope =
                mock(com.yupi.springbootinit.ai.plan.AiContentScopeService.class);
        ReflectionTestUtils.setField(service, "retrieval", retrieval);
        ReflectionTestUtils.setField(service, "contentScope", scope);
        when(scope.singleCourseCode()).thenReturn("course.operating-systems");

        com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Result result =
                new com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Result();
        result.getSources().add(com.yupi.springbootinit.model.vo.ai.SourceRef.of(
                "material:1", "PERSONAL", "course.operating-systems", 1,
                "material:1:0", "我的笔记", "个人粘贴的资料"));
        result.setSemanticUsed(true);
        when(retrieval.retrieve(any(), any(String.class))).thenReturn(result);

        TutorAnswerVO vo = service.ask(ask("什么是进程调度？"), user);

        assertTrue(vo.getSources().stream().noneMatch(
                        source -> "material:1".equals(source.getSourceId())),
                "旧入口不得下发个人资料块");
        // 检索范围必须显式排除个人资料
        ArgumentCaptor<com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Scope> captor =
                ArgumentCaptor.forClass(com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.Scope.class);
        org.mockito.Mockito.verify(retrieval).retrieve(captor.capture(), any(String.class));
        assertFalse(captor.getValue().isIncludePersonal());
    }

    /** 课程无法确定时不猜课程，退回既有发布内容检索。 */
    @Test
    void oldEntryRefusesToGuessACourseAndFallsBackToPublishedContent() {
        com.yupi.springbootinit.ai.retrieval.HybridRetrievalService retrieval =
                mock(com.yupi.springbootinit.ai.retrieval.HybridRetrievalService.class);
        com.yupi.springbootinit.ai.plan.AiContentScopeService scope =
                mock(com.yupi.springbootinit.ai.plan.AiContentScopeService.class);
        ReflectionTestUtils.setField(service, "retrieval", retrieval);
        ReflectionTestUtils.setField(service, "contentScope", scope);
        when(scope.courseCodeForNode(any())).thenReturn(null);
        when(scope.singleCourseCode()).thenReturn(null);

        assertDoesNotThrow(() -> service.ask(ask("什么是进程调度？"), user));
        org.mockito.Mockito.verifyNoInteractions(retrieval);
    }

    // endregion

    // region fixtures

    private TutorAskRequest ask(String question) {
        return askOfNode(SEED, question);
    }

    private TutorAskRequest askOfNode(String nodeName, String question) {
        TutorAskRequest request = new TutorAskRequest();
        request.setNodeName(nodeName);
        request.setQuestion(question);
        return request;
    }

    private EntityNode graphNode(String name, String description) {
        EntityNode node = new EntityNode();
        node.setName(name);
        node.setDescription(description);
        return node;
    }

    private LearningEvent event(long id, String result) {
        LearningEvent event = new LearningEvent();
        event.setId(id);
        event.setUserId(7L);
        event.setNodeName(SEED);
        event.setEventType("JUDGE");
        event.setResult(result);
        event.setEvidence("{\"source\":\"编程实验判题\",\"title\":\"实验\",\"resultText\":\"判题\",\"note\":\"详情\"}");
        return event;
    }

    private LearningPathVO nextStepPath() {
        PathItemVO item = new PathItemVO();
        item.setNodeName("LRU");
        item.setOrder(1);
        item.setStatus("GRAY");
        item.setReason("LRU 是「进程调度」的前置知识，还没有学习记录");
        LearningActionVO action = new LearningActionVO();
        action.setKind("ASSESSMENT");
        action.setNodeName("LRU");
        item.setAction(action);
        LearningPathVO path = new LearningPathVO();
        path.setTargetNodeName(SEED);
        path.setItems(List.of(item));
        return path;
    }

    private TutorSourceVO onlyEvent(List<TutorSourceVO> sources) {
        return sources.stream().filter(s -> "EVENT".equals(s.getSourceType())).findFirst().orElse(null);
    }

    private TutorSourceVO nextStepSource(List<TutorSourceVO> sources) {
        return sources.stream().filter(s -> s.getTitle() != null && s.getTitle().startsWith("下一步"))
                .findFirst().orElse(null);
    }

    /** 每条来源：actionType 稳定合法、targetId/targetUrl 至少其一、引用编号可定位 */
    private void assertStructuredActionFields(List<TutorSourceVO> sources) {
        List<String> legalTypes = List.of("NODE", "QUESTION", "EXPERIMENT", "EVENT", "RESOURCE");
        for (int i = 0; i < sources.size(); i++) {
            TutorSourceVO source = sources.get(i);
            assertTrue(legalTypes.contains(source.getActionType()),
                    "来源 " + (i + 1) + " actionType 非法：" + source.getActionType());
            assertTrue(StringUtils.isNotBlank(source.getTargetId())
                            || StringUtils.isNotBlank(source.getTargetUrl()),
                    "来源 " + (i + 1) + " 缺少行动目标");
            assertNotNull(source.getSourceId(), "来源 " + (i + 1) + " 缺少 sourceId（引用定位依据）");
        }
    }

    // endregion
}
