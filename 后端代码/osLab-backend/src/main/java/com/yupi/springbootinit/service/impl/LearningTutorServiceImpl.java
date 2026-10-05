package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
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

    @Value("${ai.ark.api-key:}")
    private String apiKey;

    @Value("${ai.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${ai.ark.model:deepseek-v3-250324}")
    private String model;

    private ArkService arkService;

    @PostConstruct
    public void init() {
        this.arkService = StringUtils.isNotBlank(apiKey)
                ? ArkService.builder().baseUrl(baseUrl).apiKey(apiKey).build()
                : null;
    }

    @Override
    public TutorAnswerVO ask(TutorAskRequest request, User user) {
        String question = request == null ? null : request.getQuestion();
        if (StringUtils.isBlank(question)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }
        List<String> trace = new ArrayList<>();
        String seedNode = request.getNodeName();
        TutorIntent intent = classifyIntent(question);
        String seedDescription = null;

        // 1. 种子节点（当前节点保证位；节点不存在直接报错，不伪造证据）
        Set<String> nodeScope = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(seedNode)) {
            List<EntityNode> found = knowledgeGraphService.findByName(seedNode);
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
        List<TutorSourceVO> relatedSources = findRelatedNodeSources(seedNode, nodeScope, relatedQuota);
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
                ? null : buildNextStepSource(user, seedNode, trace);

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

        List<List<TutorSourceVO>> groups = new ArrayList<>();
        groups.add(seedSources);
        switch (intent) {
            case ERROR_DIAGNOSIS:
                groups.add(eventSources);
                groups.add(nextStepSources);
                groups.add(questionSources);
                groups.add(relatedSources);
                break;
            case NEXT_STEP:
                groups.add(nextStepSources);
                groups.add(eventSources);
                groups.add(questionSources);
                groups.add(relatedSources);
                break;
            case CONCEPT:
            default:
                groups.add(relatedSources);
                groups.add(questionSources);
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

        // 8. AI 生成（无密钥或调用失败时降级为规则回答，不依赖外部 key）
        if (arkService == null) {
            trace.add("未配置 AI 密钥，使用规则降级回答");
            return fallbackResult(question, seedNode, sources, trace);
        }
        String answer;
        try {
            answer = generate(question, seedNode, sources);
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
    private List<TutorSourceVO> findRelatedNodeSources(String seedNode, Set<String> nodeScope, int quota) {
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
                if (StringUtils.isBlank(name) || name.equals(seedNode) || nodeScope.contains(name)) {
                    continue;
                }
                nodeScope.add(name);
                List<EntityNode> nodes = knowledgeGraphService.findByName(name);
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
     * 编程实验（tags 命中范围内节点，配额 1）
     */
    private List<Question> findExperimentQuestions(Set<String> nodeScope) {
        if (CollectionUtils.isEmpty(nodeScope)) {
            return new ArrayList<>();
        }
        try {
            List<Question> all = questionService.list();
            List<Question> matched = new ArrayList<>();
            for (Question q : all) {
                if (StringUtils.isBlank(q.getTags())) {
                    continue;
                }
                List<String> tags = JSONUtil.toList(q.getTags(), String.class);
                if (CollectionUtils.isNotEmpty(tags) && nodeScope.stream().anyMatch(tags::contains)) {
                    matched.add(q);
                }
                if (matched.size() >= 1) {
                    break;
                }
            }
            return matched;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 可执行下一步（§5.4.1 保证位）：调用既有 LearningPathService 补救路径，
     * 取第一项组装为结构化行动来源（kind=EXPERIMENT 指向实验，其余指向节点）。
     * 路径服务不可用或无路径项时返回 null（不伪造动作）。
     */
    private TutorSourceVO buildNextStepSource(User user, String seedNode, List<String> trace) {
        try {
            LearningPathVO path = learningPathService.buildPath(user, seedNode);
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
            }
            vo.setSourceId(vo.getTargetId());
            vo.setTitle("下一步：" + first.getNodeName());
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
    private String generate(String question, String seedNode, List<TutorSourceVO> sources) {
        StringBuilder material = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            TutorSourceVO s = sources.get(i);
            material.append("[").append(i + 1).append("] 类型=").append(s.getSourceType())
                    .append(" 标题=").append(s.getTitle())
                    .append(" 摘要=").append(StringUtils.abbreviate(s.getSnippet(), 300))
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
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(system).build());
        messages.add(ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build());
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model).messages(messages).build();
        StringBuilder response = new StringBuilder();
        arkService.createChatCompletion(chatRequest).getChoices()
                .forEach(choice -> response.append(choice.getMessage().getContent()));
        return response.toString();
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
                    .filter(s -> "NODE".equals(s.getSourceType()) && seedNode.equals(s.getSourceId()))
                    .findFirst().orElse(null);
            sb.append(nodeSource == null || StringUtils.isBlank(nodeSource.getSnippet())
                    ? "暂无描述" : nodeSource.getSnippet()).append("\n\n");
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
