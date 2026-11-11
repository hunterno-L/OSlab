package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.ModelGateway;
import com.yupi.springbootinit.ai.plan.AiContentScopeService;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.ai.retrieval.HybridRetrievalService;
import com.yupi.springbootinit.common.ErrorCode;
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
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.ai.SourceRef;
import com.yupi.springbootinit.model.vo.learning.LearningActionVO;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.model.vo.learning.PathItemVO;
import com.yupi.springbootinit.model.vo.learning.TutorAnswerVO;
import com.yupi.springbootinit.model.vo.learning.TutorSourceVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningPathService;
import com.yupi.springbootinit.service.LearningTutorService;
import com.yupi.springbootinit.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GraphRAG 节点导师实现（WP4；阶段 5 §5.4 分组配额修订）
 *
 * <p>语料范围 = 图谱节点/关系 + 已发布题目解析 + 编程实验 + 本人学习事件
 * （他人事件、草稿与未发布内容绝不进入证据包）。
 *
 * <p>来源配额（§5.4.1）：MAX_SOURCES=8 不再是"先到先得"的单池，
 * 而是按问题意图分组预留配额——当前节点/关系、已发布内容或前置知识、
 * 本人失败/提交记录、可执行下一步各有保留位，本人学习事件不会被
 * 邻居节点或题目列表挤掉。各组配额之和 <= MAX_SOURCES。
 *
 * <p>行动结构（§5.4.4）：每条来源携带稳定 actionType（NODE/QUESTION/
 * EXPERIMENT/EVENT/RESOURCE）与 targetId/targetUrl，前端可按类型跳转。
 */
@Slf4j
@Service
public class LearningTutorServiceImpl implements LearningTutorService {

    private static final int MAX_SOURCES = 8;
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");
    private static final String MODE_AI = "AI";
    private static final String MODE_FALLBACK = "FALLBACK";

    /** 问题意图（阶段 5 §5.4.2）：概念 / 错误诊断 / 下一步 */
    enum TutorIntent {
        /** 概念问题：优先节点说明、前置/后续关系、已发布内容 */
        CONCEPT,
        /** 错误诊断：优先本人最近失败/提交记录 */
        ERROR_DIAGNOSIS,
        /** 下一步规划：优先既有学习路径服务的可执行行动 */
        NEXT_STEP
    }

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    @Resource
    private LearningEventMapper learningEventMapper;

    @Resource
    private QuestionService questionService;

    @Resource
    private LearningPathService learningPathService;
    @Resource
    private com.yupi.springbootinit.learning.NodeIdentityService nodeIdentities;
    @Resource
    private com.yupi.springbootinit.learning.ExperimentMappingService experimentMappings;
    @Resource
    private com.yupi.springbootinit.learning.PublishedLearningContentService publishedContent;

    /**
     * 统一模型网关（计划 §6.2「旧入口内部委托统一服务」）。
     *
     * <p>旧导师不再持有第二套 Ark SDK 客户端：平台默认模型与个人模型配置只有一个来源，
     * 因此不存在"新入口能配置、旧入口另一套模型"的状态。
     * 单测不注入时保持 null，ask() 走规则降级（与升级前"未配置 key"行为一致）。
     */
    @Resource
    private ModelGateway modelGateway;

    @Resource
    private ModelProfileService modelProfileService;

    /**
     * 统一联合检索（语义 + 关键词 + 图谱）。为空或索引未建立时退回既有发布内容检索，
     * 旧响应结构保持不变。
     */
    @Resource
    private HybridRetrievalService retrieval;

    @Resource
    private AiContentScopeService contentScope;

    @Resource private com.yupi.springbootinit.course.EnabledCourses enabledCourses;
    @Resource private com.yupi.springbootinit.learning.PublishedCourseScopeService courseScope;

    @Override
    public TutorAnswerVO ask(TutorAskRequest request, User user) {
        String question = request == null ? null : request.getQuestion();
        if (StringUtils.isBlank(question)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }
        List<String> trace = new ArrayList<>();
        String seedIdentity = StringUtils.defaultIfBlank(request.getNodeCode(),request.getNodeName());
        if (StringUtils.isBlank(seedIdentity)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请指定当前课程的知识节点");
        }
        com.yupi.springbootinit.learning.NodeIdentityService.Identity requested = nodeIdentities.resolve(seedIdentity);
        String selectedCourse = null;
        Map<String, com.yupi.springbootinit.learning.NodeIdentityService.Identity> selectedNodes = null;
        if (requested != null) {
            for (String code : new java.util.TreeSet<>(enabledCourses.all())) {
                Map<String, com.yupi.springbootinit.learning.NodeIdentityService.Identity> candidate = courseScope.nodes(code);
                if (candidate.containsKey(requested.nodeCode)) {
                    if (selectedCourse != null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点属于多个启用课程，请使用课程导师");
                    selectedCourse = code;
                    selectedNodes = candidate;
                }
            }
        }
        if (selectedCourse == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "课程节点当前不可用");
        Set<String> allowedKeys = new HashSet<>();
        for (com.yupi.springbootinit.learning.NodeIdentityService.Identity identity : selectedNodes.values()) {
            allowedKeys.add(identity.nodeCode); allowedKeys.add(identity.storageKey);
        }
        String seedNode = seedIdentity;
        if (nodeIdentities!=null&&StringUtils.isNotBlank(seedNode)) seedNode=nodeIdentities.storageKey(seedNode);
        TutorIntent intent = classifyIntent(question);
        String seedDescription = null;

        // 1. 种子节点（当前节点保证位；节点不存在直接报错，不伪造证据）
        Set<String> nodeScope = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(seedNode)) {
            List<EntityNode> found = knowledgeGraphService.findByName(seedIdentity);
            if (CollectionUtils.isEmpty(found)) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + seedNode);
            }
            seedDescription = found.get(0).getDescription();
            nodeScope.add(seedNode);
            trace.add("定位种子节点：「" + seedNode + "」");
        } else {
            trace.add("未指定节点，按全图检索");
        }

        // 2. 一跳相关节点（前置/后续关系，配额内；同时扩展检索范围供题目/事件命中）
        int relatedQuota = intent == TutorIntent.CONCEPT ? 2 : 1;
        List<TutorSourceVO> relatedSources = findRelatedNodeSources(seedIdentity, nodeScope, relatedQuota, allowedKeys);
        if (!relatedSources.isEmpty()) {
            trace.add("图谱扩展 " + relatedSources.size() + " 个一跳相关节点");
        } else if (StringUtils.isNotBlank(seedNode)) {
            trace.add("图谱扩展失败（Neo4j 不可用），仅使用种子节点");
        }

        // 3. 本人学习事件（失败/提交优先；绝不包含他人事件；保留配额不被挤掉）
        int eventQuota = intent == TutorIntent.ERROR_DIAGNOSIS ? 2 : 1;
        List<LearningEvent> myEvents = selectMyEvents(user.getId(), nodeScope, eventQuota);
        trace.add("纳入本人近期学习事件 " + myEvents.size() + " 条（失败/提交优先）");

        // 4. 已发布题目（映射到范围内节点；仅 PUBLISHED 且未删除，草稿/下线排除）
        int questionQuota = intent == TutorIntent.ERROR_DIAGNOSIS ? 1 : 2;
        List<LearningQuestion> publishedQuestions = findPublishedQuestions(nodeScope, questionQuota);
        trace.add("检索到 " + publishedQuestions.size() + " 道已发布题目");

        // 5. 编程实验（tags 命中范围内节点，配额 1）
        List<Question> experiments = findExperimentQuestions(nodeScope);
        if (!experiments.isEmpty()) {
            trace.add("检索到 " + experiments.size() + " 道相关编程实验");
        }

        // 6. 可执行下一步（既有 LearningPathService；失败不阻塞问答）
        TutorSourceVO nextStepSource = StringUtils.isBlank(seedNode)
                ? null : buildNextStepSource(user, seedIdentity, trace, selectedCourse);

        // 7. 按意图决定组顺序组装（引用编号 = 列表序号 + 1，与 AI/fallback 引用一致）
        List<TutorSourceVO> seedSources = StringUtils.isBlank(seedNode)
                ? Collections.emptyList()
                : Collections.singletonList(nodeSource(seedNode, seedDescription));
        List<TutorSourceVO> eventSources = myEvents.stream().map(this::eventSource).collect(Collectors.toList());
        List<TutorSourceVO> questionSources = publishedQuestions.stream()
                .map(this::questionSource).collect(Collectors.toList());
        List<TutorSourceVO> nextStepSources = nextStepSource == null
                ? Collections.emptyList() : Collections.singletonList(nextStepSource);
        List<TutorSourceVO> experimentSources = experiments.stream()
                .map(this::experimentSource).collect(Collectors.toList());
        List<String> codes = new ArrayList<>();
        if(nodeIdentities!=null)for(String name:nodeScope){
            com.yupi.springbootinit.learning.NodeIdentityService.Identity identity=nodeIdentities.byStorageKey(name);
            if(identity!=null)codes.add(identity.nodeCode);
        }
        List<TutorSourceVO> textbookSources=retrieveTextbookSources(user,codes,question,2,trace,selectedCourse);
        trace.add("检索到 "+textbookSources.size()+" 个已发布教材片段（保留正文块、版本与出处）");
        // Textbook material shares the content quota; personal evidence and next action keep their reserved places.
        List<TutorSourceVO> contentSources=new ArrayList<>(textbookSources);
        for(TutorSourceVO source:questionSources)if(contentSources.size()<questionQuota)contentSources.add(source);
        if(contentSources.size()>questionQuota)contentSources=new ArrayList<>(contentSources.subList(0,questionQuota));

        List<List<TutorSourceVO>> groups = new ArrayList<>();
        groups.add(seedSources);
        switch (intent) {
            case ERROR_DIAGNOSIS:
                groups.add(eventSources);
                groups.add(nextStepSources);
                groups.add(contentSources);
                groups.add(relatedSources);
                break;
            case NEXT_STEP:
                groups.add(nextStepSources);
                groups.add(eventSources);
                groups.add(contentSources);
                groups.add(relatedSources);
                break;
            case CONCEPT:
            default:
                groups.add(contentSources);
                groups.add(relatedSources);
                groups.add(nextStepSources);
                groups.add(eventSources);
                break;
        }
        groups.add(experimentSources);

        List<TutorSourceVO> sources = new ArrayList<>();
        outer:
        for (List<TutorSourceVO> group : groups) {
            for (TutorSourceVO source : group) {
                if (sources.size() >= MAX_SOURCES) {
                    break outer;
                }
                sources.add(source);
            }
        }

        if (CollectionUtils.isEmpty(sources)) {
            trace.add("证据为空：不足以判断");
            TutorAnswerVO empty = new TutorAnswerVO();
            empty.setSources(sources);
            empty.setRetrievalTrace(trace);
            empty.setMode(MODE_FALLBACK);
            empty.setAnswer("当前没有检索到与该问题相关的课程证据（节点、题目或你的学习事件）。"
                    + "请先确认节点名称是否正确，或先完成该节点的基础题目再提问。");
            return empty;
        }

        // 8. AI 生成（模型未配置或调用失败时降级为规则回答，不依赖外部 key）
        ModelEndpoint endpoint = resolveEndpoint(user, trace);
        if (endpoint == null) {
            trace.add("未配置 AI 密钥，使用规则降级回答");
            return fallbackResult(question, seedNode, sources, trace);
        }
        String answer;
        try {
            answer = generate(endpoint, question, seedNode, sources);
        } catch (Exception e) {
            log.warn("[tutor] AI generation failed, fallback. {}", e.getClass().getSimpleName());
            trace.add("模型调用失败，使用规则降级回答");
            return fallbackResult(question, seedNode, sources, trace);
        }
        trace.add("校验回答引用只指向本次检索来源");
        TutorAnswerVO vo = new TutorAnswerVO();
        vo.setAnswer(validateCitations(answer, sources.size()));
        vo.setSources(sources);
        vo.setRetrievalTrace(trace);
        vo.setMode(MODE_AI);
        return vo;
    }

    // region 私有方法

    /**
     * 问题意图分类（§5.4.2，确定性规则，可单测）：
     * 下一步规划 > 错误诊断 > 概念（默认）。
     */
    static TutorIntent classifyIntent(String question) {
        String q = question == null ? "" : question;
        if (q.contains("下一步") || q.contains("先学") || q.contains("接下来")
                || q.contains("路径") || q.contains("从哪") || q.contains("怎么学")) {
            return TutorIntent.NEXT_STEP;
        }
        if (q.contains("错") || q.contains("失败") || q.contains("报错") || q.contains("为什么")
                || q.contains("编译") || q.contains("运行") || q.contains("超时") || q.contains("排查")) {
            return TutorIntent.ERROR_DIAGNOSIS;
        }
        return TutorIntent.CONCEPT;
    }

    /**
     * 本人学习事件（§5.4.1 保留配额）：只查当前用户、范围内节点；
     * 失败（FAIL）> 诊断（编译/系统错误）> 通过，同级保持时间倒序。
     * 查询上限 10 条后按性质排序取配额，保证失败/提交记录不被挤掉。
     */
    private List<LearningEvent> selectMyEvents(Long userId, Set<String> nodeScope, int quota) {
        if (quota <= 0) {
            return Collections.emptyList();
        }
        List<LearningEvent> recent = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, userId)
                        .in(CollectionUtils.isNotEmpty(nodeScope), LearningEvent::getNodeName, nodeScope)
                        .orderByDesc(LearningEvent::getCreateTime)
                        .orderByDesc(LearningEvent::getId)
                        .last("limit 10"));
        List<LearningEvent> failures = new ArrayList<>();
        List<LearningEvent> diagnostics = new ArrayList<>();
        List<LearningEvent> passes = new ArrayList<>();
        for (LearningEvent event : recent) {
            LearningResultEnum result = LearningResultEnum.parse(event.getResult());
            if (result == LearningResultEnum.FAIL) {
                failures.add(event);
            } else if (result != null && result.isDiagnostic()) {
                diagnostics.add(event);
            } else {
                passes.add(event);
            }
        }
        List<LearningEvent> ordered = new ArrayList<>(recent.size());
        ordered.addAll(failures);
        ordered.addAll(diagnostics);
        ordered.addAll(passes);
        return ordered.size() <= quota ? ordered : ordered.subList(0, quota);
    }

    /**
     * 一跳相关节点（前置/后续）：配额内组装为 NODE 来源，并扩展 nodeScope
     * 供题目/事件检索命中。Neo4j 不可用时降级为空列表，不阻塞问答。
     */
    private List<TutorSourceVO> findRelatedNodeSources(String seedNode, Set<String> nodeScope, int quota, Set<String> allowedKeys) {
        List<TutorSourceVO> result = new ArrayList<>();
        if (StringUtils.isBlank(seedNode) || quota <= 0) {
            return result;
        }
        try {
            List<String> related = knowledgeGraphService.getAllRelatedNames(seedNode);
            for (String name : related) {
                if (result.size() >= quota) {
                    break;
                }
                if (StringUtils.isBlank(name) || !allowedKeys.contains(name) || name.equals(seedNode) || nodeScope.contains(name)) {
                    continue;
                }
                nodeScope.add(name);
                com.yupi.springbootinit.learning.NodeIdentityService.Identity identity=nodeIdentities==null?null:nodeIdentities.byStorageKey(name);
                List<EntityNode> nodes = knowledgeGraphService.findByName(identity==null?name:identity.nodeCode);
                String description = CollectionUtils.isEmpty(nodes) ? "" : nodes.get(0).getDescription();
                result.add(nodeSource(name, description));
            }
        } catch (Exception e) {
            // 图谱扩展失败：保留种子节点证据，问答不中断
        }
        return result;
    }

    /**
     * 已发布题目（仅 PUBLISHED 且未删除；配额替换旧的固定 limit 4）。
     * SQL limit 与内存截断双重保证配额（分组配额之和 <= MAX_SOURCES 的前提）。
     */
    private List<LearningQuestion> findPublishedQuestions(Set<String> nodeScope, int quota) {
        if (quota <= 0) {
            return Collections.emptyList();
        }
        List<LearningQuestion> selected;
        if (CollectionUtils.isEmpty(nodeScope)) {
            selected = learningQuestionMapper.selectList(new LambdaQueryWrapper<LearningQuestion>()
                    .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                    .eq(LearningQuestion::getIsDelete, 0)
                    .orderByDesc(LearningQuestion::getCreateTime)
                    .last("limit " + quota));
        } else {
            List<Long> ids = learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .in(LearningQuestionNode::getNodeName, nodeScope))
                    .stream().map(LearningQuestionNode::getQuestionId).distinct().collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                return new ArrayList<>();
            }
            selected = learningQuestionMapper.selectList(new LambdaQueryWrapper<LearningQuestion>()
                    .in(LearningQuestion::getId, ids)
                    .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                    .eq(LearningQuestion::getIsDelete, 0)
                    .last("limit " + quota));
        }
        return selected.size() <= quota ? selected : selected.subList(0, quota);
    }

    /**
     * 编程实验（正式节点映射，配额 1；标签不构成教学或判题证据）
     */
    private List<Question> findExperimentQuestions(Set<String> nodeScope) {
        if (CollectionUtils.isEmpty(nodeScope)) {
            return new ArrayList<>();
        }
        if (experimentMappings != null) {
            for(String node:nodeScope){List<Question> mapped=experimentMappings.experiments(node);if(!mapped.isEmpty())return Collections.singletonList(mapped.get(0));}
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * 可执行下一步（§5.4.1 保证位）：调用既有 LearningPathService 补救路径，
     * 取第一项组装为结构化行动来源（kind=EXPERIMENT 指向实验，其余指向节点）。
     * 路径服务不可用或无路径项时返回 null（不伪造动作）。
     */
    private TutorSourceVO buildNextStepSource(User user, String seedNode, List<String> trace, String courseCode) {
        try {
            LearningPathVO path = learningPathService.buildPath(user, seedNode, courseCode);
            if (path == null || CollectionUtils.isEmpty(path.getItems())) {
                return null;
            }
            PathItemVO first = path.getItems().get(0);
            LearningActionVO action = first.getAction();
            String kind = action == null || StringUtils.isBlank(action.getKind()) ? "NODE" : action.getKind();
            TutorSourceVO vo = new TutorSourceVO();
            if ("EXPERIMENT".equals(kind) && action.getResourceId() != null) {
                vo.setSourceType("EXPERIMENT");
                vo.setActionType("EXPERIMENT");
                vo.setTargetId(String.valueOf(action.getResourceId()));
            } else {
                vo.setSourceType("NODE");
                vo.setActionType("NODE");
                vo.setTargetId(first.getNodeName());
                if(first.getNodeCode()!=null){vo.setTargetId(first.getNodeCode());vo.setTargetUrl("/knowledge/concept/"+first.getNodeCode());}
            }
            vo.setSourceId(vo.getTargetId());
            if(action!=null&&action.getUnitCode()!=null&&action.getCourseCode()!=null){
                vo.setTargetUrl("/courses/"+action.getCourseCode()+"/units/"+action.getUnitCode()+"?mode=REVIEW");
            }
            vo.setTitle("下一步：" + StringUtils.defaultIfBlank(first.getDisplayName(),first.getNodeName()));
            vo.setSnippet(StringUtils.defaultIfBlank(first.getReason(), "按补救路径先完成该节点"));
            trace.add("生成可执行下一步：" + first.getNodeName());
            return vo;
        } catch (Exception e) {
            trace.add("学习路径暂不可用，未生成下一步动作");
            return null;
        }
    }

    /**
     * AI 生成：grounded prompt + 编号引用要求 + §5.4.5 行为约束
     * （先提示定位再解释；不直接输出完整实验答案或整段可提交代码）
     */
    private String generate(ModelEndpoint endpoint, String question, String seedNode, List<TutorSourceVO> sources) {
        StringBuilder material = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            TutorSourceVO s = sources.get(i);
            material.append("[").append(i + 1).append("] 类型=").append(s.getSourceType())
                    .append(" 标题=").append(s.getTitle())
                    .append(" 摘要=").append(StringUtils.abbreviate(s.getSnippet(), 300))
                    .append(s.getParentChunkCode()==null?"":" 原文块="+s.getParentChunkCode()+" 版本="+s.getRevision()+" 出处="+JSONUtil.toJsonStr(s.getSourceRef()))
                    .append("\n");
        }
        String system = "你是操作系统课程的节点导师。回答规则：先给定位提示（指出学生应先查看"
                + "哪个知识点、题目或自己的哪条记录），再给解释；不直接输出完整实验答案或"
                + "整段可提交代码，只给思路与定位。"
                + "只依据提供的课程资料回答，每个关键结论后必须用 [编号] 标注来源，"
                + "编号只能是资料列表中的编号。"
                + "资料不足以回答的部分要明确说明“资料不足以判断”，禁止编造课程结论。"
                + "资料中可能包含该学生本人的学习事件，可结合其薄弱点给个性化建议，但不要提及任何其他学生。";
        String userPrompt = (StringUtils.isBlank(seedNode) ? "" : "当前学习节点：" + seedNode + "\n")
                + "学生问题：" + question + "\n\n课程资料：\n" + material;
        // 统一网关：协议适配、超时与失败语义都在网关内，导师只负责提示词与引用校验
        ModelGateway.ChatResult result = modelGateway.chat(endpoint,
                ModelGateway.ChatRequest.of(system, userPrompt));
        return result == null || result.getContent() == null ? "" : result.getContent();
    }

    /**
     * 旧入口没有模型配置参数，固定走平台默认端点（{@code ai.ark.*}）。
     *
     * @return 可发起真实调用的端点；未配置或不可用时返回 null，由调用方降级为规则回答
     */
    private ModelEndpoint resolveEndpoint(User user, List<String> trace) {
        if (modelGateway == null || modelProfileService == null || user == null) {
            return null;
        }
        try {
            ModelEndpoint endpoint = modelProfileService.resolveEndpoint(user.getId(), null);
            if (!modelGateway.available(endpoint)) {
                return null;
            }
            return endpoint;
        } catch (BusinessException e) {
            log.info("[tutor] platform model unavailable: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("[tutor] model endpoint resolution failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 教材片段来源：优先走统一联合检索（语义 + 关键词 + 图谱，计划 §5.2），
     * 依赖缺失、课程无法确定或索引为空时退回既有 {@code publishedContent.sources(...)}。
     *
     * <p>两条路径都只下发已发布课程正文：旧契约里没有个人资料的位置，
     * 因此这里显式 {@code includePersonal=false}，避免把个人材料混进旧响应。
     */
    private List<TutorSourceVO> retrieveTextbookSources(User user, List<String> codes, String question,
                                                        int quota, List<String> trace, String courseCode) {
        if (retrieval != null && user != null && StringUtils.isNotBlank(courseCode)) {
            try {
                HybridRetrievalService.Scope scope = new HybridRetrievalService.Scope();
                scope.setUserId(user.getId());
                scope.setCourseCode(courseCode);
                scope.setNodeCodes(codes == null ? new ArrayList<>() : new ArrayList<>(codes));
                scope.setIncludePersonal(false);
                HybridRetrievalService.Result result = retrieval.retrieve(scope, question);
                List<TutorSourceVO> out = new ArrayList<>();
                for (SourceRef ref : result.getSources()) {
                    if (!"COURSE".equals(ref.getOwnerScope())) {
                        continue;
                    }
                    out.add(textbookSource(ref));
                    if (out.size() >= quota) {
                        break;
                    }
                }
                if (!out.isEmpty()) {
                    trace.add(result.isComplete()
                            ? "统一检索（语义+关键词+图谱）命中 " + out.size() + " 个已发布教材片段"
                            : "统一检索命中 " + out.size() + " 个已发布教材片段（语义不可用：仅关键词+图谱）");
                    return out;
                }
                trace.add("统一检索无课程正文命中，退回既有发布内容检索");
            } catch (RuntimeException e) {
                log.warn("[tutor] unified retrieval failed: {}", e.getClass().getSimpleName());
                trace.add("统一检索失败，退回既有发布内容检索");
            }
        }
        return publishedContent == null ? Collections.emptyList() : publishedContent.sourcesForCourse(courseCode, codes, question, quota);
    }

    /**
     * 由节点归属反查课程；节点查不到时用平台唯一课程。
     * 都不成立就返回 null（不猜课程，退回既有检索）。
     */
    private String resolveCourseCode(List<String> codes) {
        if (contentScope == null) {
            return null;
        }
        if (codes != null) {
            for (String code : codes) {
                String courseCode = contentScope.courseCodeForNode(code);
                if (StringUtils.isNotBlank(courseCode)) {
                    return courseCode;
                }
            }
        }
        return contentScope.singleCourseCode();
    }

    /** 统一检索的 SourceRef 映射为旧契约的 TEXTBOOK 来源，保留正文块、版本与出处。 */
    private TutorSourceVO textbookSource(SourceRef ref) {
        TutorSourceVO vo = new TutorSourceVO();
        vo.setSourceType("TEXTBOOK");
        vo.setActionType("RESOURCE");
        vo.setSourceId(StringUtils.defaultIfBlank(ref.getSourceId(), ref.getBlockId()));
        vo.setParentChunkCode(StringUtils.defaultIfBlank(ref.getBlockId(), ref.getSourceId()));
        vo.setTargetId(vo.getParentChunkCode());
        vo.setTitle(StringUtils.defaultIfBlank(ref.getLocator(), vo.getParentChunkCode()));
        vo.setRevision(ref.getRevision());
        vo.setSnippet(ref.getExcerpt());
        vo.setSourceRef(JSONUtil.parseObj(JSONUtil.toJsonStr(ref)));
        vo.setSourceSectionTitle(ref.getLocator());
        vo.setTargetUrl("#chunk-" + vo.getParentChunkCode());
        return vo;
    }

    /**
     * 引用校验：只允许指向本次检索到的来源，越界引用直接移除
     */
    static String validateCitations(String answer, int sourceCount) {
        if (StringUtils.isBlank(answer)) {
            return answer;
        }
        Matcher matcher = CITATION.matcher(answer);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = (index >= 1 && index <= sourceCount) ? matcher.group() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 规则降级回答：确定性组装证据卡 + 下一步动作（不依赖 AI key）
     */
    private TutorAnswerVO fallbackResult(String question, String seedNode,
                                         List<TutorSourceVO> sources, List<String> trace) {
        TutorAnswerVO vo = new TutorAnswerVO();
        vo.setAnswer(buildFallbackAnswer(question, seedNode, sources));
        vo.setSources(sources);
        vo.setRetrievalTrace(trace);
        vo.setMode(MODE_FALLBACK);
        return vo;
    }

    private String buildFallbackAnswer(String question, String seedNode, List<TutorSourceVO> sources) {
        StringBuilder sb = new StringBuilder();
        boolean failRelated = classifyIntent(question) == TutorIntent.ERROR_DIAGNOSIS;
        boolean nextRelated = classifyIntent(question) == TutorIntent.NEXT_STEP;
        sb.append("（降级模式：AI 未配置或暂不可用，以下为基于课程图谱与你的学习记录的确定性回答）\n\n");
        if (StringUtils.isNotBlank(seedNode)) {
            sb.append("当前节点「").append(seedNode).append("」的说明：");
            TutorSourceVO nodeSource = sources.stream()
                    .filter(s -> "NODE".equals(s.getSourceType()) && s.getTitle()!=null&&!s.getTitle().startsWith("下一步"))
                    .findFirst().orElse(null);
            sb.append(nodeSource == null || StringUtils.isBlank(nodeSource.getSnippet())
                    ? "暂无描述" : nodeSource.getSnippet()).append("\n\n");
        }
        for (int i=0;i<sources.size();i++) {
            TutorSourceVO source=sources.get(i);
            if("TEXTBOOK".equals(source.getSourceType())) sb.append(source.getTitle()).append(" [").append(i+1).append("]\n")
                    .append(source.getSnippet()).append("\n出处：").append(source.getSourceSectionTitle()==null?"":source.getSourceSectionTitle())
                    .append(" ").append(JSONUtil.toJsonStr(source.getSourceRef())).append("\n\n");
        }
        List<TutorSourceVO> events = sources.stream()
                .filter(s -> "EVENT".equals(s.getSourceType())).collect(Collectors.toList());
        if (!events.isEmpty()) {
            sb.append("结合你最近的学习记录：\n");
            for (TutorSourceVO event : events) {
                sb.append("- ").append(event.getTitle()).append("：").append(event.getSnippet()).append("\n");
            }
            if (failRelated) {
                sb.append("建议先定位失败点（查看对应题目解析或提交详情），再完成前置知识练习。\n");
            }
            sb.append("\n");
        }
        List<TutorSourceVO> materials = sources.stream()
                .filter(s -> "QUESTION".equals(s.getSourceType()) || "EXPERIMENT".equals(s.getSourceType()))
                .collect(Collectors.toList());
        if (!materials.isEmpty()) {
            sb.append("可先学习以下资料：\n");
            int limit = Math.min(3, materials.size());
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(materials.get(i).getTitle()).append("\n");
            }
            sb.append("\n");
        }
        sources.stream()
                .filter(s -> s.getTitle() != null && s.getTitle().startsWith("下一步"))
                .findFirst()
                .ifPresent(next -> sb.append("可执行下一步：").append(next.getTitle()).append("——")
                        .append(StringUtils.defaultIfBlank(next.getSnippet(), "按路径完成该节点"))
                        .append("\n"));
        if (nextRelated && sources.stream().noneMatch(s -> s.getTitle() != null
                && s.getTitle().startsWith("下一步"))) {
            sb.append("暂无可生成的路径动作，建议先完成当前节点的基础题目。\n");
        }
        sb.append("\n以上每条建议均可按来源跳转到对应节点、题目、实验或学习记录。");
        return sb.toString();
    }

    // ---- 来源组装：每条来源携带稳定 actionType 与 targetId/targetUrl（§5.4.4）----

    private TutorSourceVO nodeSource(String name, String description) {
        TutorSourceVO vo = new TutorSourceVO();
        vo.setSourceType("NODE");
        vo.setSourceId(name);
        vo.setActionType("NODE");
        vo.setTargetId(name);
        vo.setTitle("节点：" + name);
        if(nodeIdentities!=null){
            com.yupi.springbootinit.learning.NodeIdentityService.Identity identity=nodeIdentities.byStorageKey(name);
            if(identity!=null){vo.setSourceId(identity.nodeCode);vo.setTargetId(identity.nodeCode);vo.setTitle("节点："+identity.name);vo.setTargetUrl("/knowledge/concept/"+identity.nodeCode);}
        }
        vo.setSnippet(StringUtils.abbreviate(description, 200));
        return vo;
    }

    private TutorSourceVO questionSource(LearningQuestion q) {
        TutorSourceVO vo = new TutorSourceVO();
        vo.setSourceType("QUESTION");
        vo.setSourceId(String.valueOf(q.getId()));
        vo.setActionType("QUESTION");
        vo.setTargetId(String.valueOf(q.getId()));
        vo.setTitle("题目：" + q.getTitle());
        vo.setSnippet(StringUtils.abbreviate(StringUtils.defaultIfBlank(q.getAnalysis(), q.getContent()), 200));
        return vo;
    }

    private TutorSourceVO experimentSource(Question q) {
        TutorSourceVO vo = new TutorSourceVO();
        vo.setSourceType("EXPERIMENT");
        vo.setSourceId(String.valueOf(q.getId()));
        vo.setActionType("EXPERIMENT");
        vo.setTargetId(String.valueOf(q.getId()));
        vo.setTitle("编程实验：" + q.getTitle());
        vo.setSnippet(StringUtils.abbreviate(q.getContent(), 200));
        return vo;
    }

    private TutorSourceVO eventSource(LearningEvent e) {
        TutorSourceVO vo = new TutorSourceVO();
        vo.setSourceType("EVENT");
        vo.setSourceId(String.valueOf(e.getId()));
        vo.setActionType("EVENT");
        vo.setTargetId(String.valueOf(e.getId()));
        vo.setTitle("我的学习事件：" + e.getNodeName());
        String evidence = StringUtils.isBlank(e.getEvidence()) ? "" : e.getEvidence();
        String resultText = LearningResultEnum.PASS.name().equals(e.getResult()) ? "通过" : "未通过";
        vo.setSnippet(resultText + "，" + StringUtils.abbreviate(evidence, 150));
        return vo;
    }

    // endregion
}
