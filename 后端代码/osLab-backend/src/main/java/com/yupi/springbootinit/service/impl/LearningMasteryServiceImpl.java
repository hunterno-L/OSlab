package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.RelationType;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.FailureStateClassifier;
import com.yupi.springbootinit.learning.LearningAttributionService;
import com.yupi.springbootinit.learning.LearningResourceResolver;
import com.yupi.springbootinit.learning.MasterySnapshotCodec;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.vo.learning.ChapterSummaryVO;
import com.yupi.springbootinit.model.vo.learning.ExperimentSummaryVO;
import com.yupi.springbootinit.model.vo.learning.LastFailureVO;
import com.yupi.springbootinit.model.vo.learning.LearningEventVO;
import com.yupi.springbootinit.model.vo.learning.MasterySnapshotVO;
import com.yupi.springbootinit.model.vo.learning.NodeRefVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.model.vo.learning.WeakNodeVO;
import com.yupi.springbootinit.model.vo.learning.QuestionStatsVO;
import com.yupi.springbootinit.model.vo.learning.SimulationEntryVO;
import com.yupi.springbootinit.service.QuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.model.vo.learning.MasteryNodeVO;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.model.vo.learning.PersonalGraphVO;
import com.yupi.springbootinit.model.vo.learning.RecommendationVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningMasteryService;
import com.yupi.springbootinit.service.LearningQuestionService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个人知识图谱与掌握度服务实现（契约 §3.5 / Phase 3）
 * 掌握度只做读取叠加，绝不写回全局图谱节点。
 */
@Service
public class LearningMasteryServiceImpl implements LearningMasteryService {

    /** 图谱依赖关系类型（links 中携带的是 RelationType 的英文值） */
    private static final String RELATION_DEPENDENT = RelationType.DEPENDENT;
    private static final int MAX_EVENTS = 50;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private LearningMasteryMapper learningMasteryMapper;

    @Resource
    private LearningEventMapper learningEventMapper;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private LearningResourceResolver resourceResolver;

    @Resource
    private com.yupi.springbootinit.mapper.LearningNodeContentMapper learningNodeContentMapper;

    @Override
    public PersonalGraphVO personalGraph(User user) {
        // Phase 1：改用一次性快照（全节点，含孤立节点与 chapter），不再走 N+1 关系查询
        GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
        Map<String, LearningMastery> masteryMap = masteryMapOf(user.getId());
        Map<Long, LearningEvent> lastEventMap = lastEventsOf(masteryMap.values());
        PersonalGraphVO vo = new PersonalGraphVO();
        List<MasteryNodeVO> nodes = new ArrayList<>();
        if (graph.getNodes() != null) {
            for (NodeDTO node : graph.getNodes()) {
                MasteryNodeVO masteryNode = new MasteryNodeVO();
                masteryNode.setId(node.getId());
                masteryNode.setName(node.getName());
                masteryNode.setCategory(node.getCategory());
                masteryNode.setChapter(node.getChapter());
                masteryNode.setDescription(node.getDescription());
                LearningMastery mastery = masteryMap.get(node.getName());
                applyMastery(masteryNode, mastery);
                if (mastery != null && mastery.getLastEventId() != null) {
                    LearningEvent lastEvent = lastEventMap.get(mastery.getLastEventId());
                    if (lastEvent != null) {
                        masteryNode.setRecentReason(evidenceSummary(lastEvent));
                        masteryNode.setLastEventType(lastEvent.getEventType());
                        masteryNode.setLastResult(lastEvent.getResult());
                    }
                }
                nodes.add(masteryNode);
            }
        }
        vo.setNodes(nodes);
        vo.setLinks(graph.getLinks() == null ? Collections.emptyList() : graph.getLinks());
        vo.setCategories(graph.getCategories() == null ? Collections.emptyList() : graph.getCategories());
        // ---- Phase 2 聚合字段（真实统计，可 SQL 对照）----
        vo.setTotalNodes(nodes.size());
        vo.setTotalLinks(graph.getLinks() == null ? 0 : graph.getLinks().size());
        long evidenceCount = nodes.stream()
                .filter(n -> n.getStatus() != null && !"GRAY".equals(n.getStatus()))
                .count();
        vo.setEvidenceCount((int) evidenceCount);
        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("GRAY", 0);
        statusCounts.put("RED", 0);
        statusCounts.put("YELLOW", 0);
        statusCounts.put("GREEN", 0);
        for (MasteryNodeVO node : nodes) {
            statusCounts.merge(node.getStatus() == null ? "GRAY" : node.getStatus(), 1, Integer::sum);
        }
        vo.setStatusCounts(statusCounts);
        if (graph.getChapterSummary() != null) {
            List<ChapterSummaryVO> chapters = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : graph.getChapterSummary().entrySet()) {
                ChapterSummaryVO chapterVo = new ChapterSummaryVO();
                chapterVo.setName(entry.getKey());
                chapterVo.setCount(entry.getValue());
                chapters.add(chapterVo);
            }
            vo.setChapters(chapters);
        }
        LearningEvent latest = learningEventMapper.selectOne(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .orderByDesc(LearningEvent::getCreateTime)
                        .last("limit 1"));
        vo.setLastUpdatedAt(latest == null ? null : latest.getCreateTime());
        vo.setRecommendedNodeName(recommendedNodeName(nodes));
        return vo;
    }

    /**
     * 推荐节点：薄弱（RED/YELLOW）节点中 pKnown 最低者；无薄弱证据返回 null。
     * 与前端默认焦点规则一致（契约 §6.2 recommendedNodeName）。
     */
    private String recommendedNodeName(List<MasteryNodeVO> nodes) {
        return nodes.stream()
                .filter(n -> ("RED".equals(n.getStatus()) || "YELLOW".equals(n.getStatus()))
                        && n.getPKnown() != null)
                .sorted(Comparator.comparingDouble(MasteryNodeVO::getPKnown))
                .map(MasteryNodeVO::getName)
                .findFirst()
                .orElse(null);
    }

    @Override
    public NodeWorkbenchVO nodeWorkbench(User user, String nodeName) {
        // 一次快照同时提供节点校验、元数据和依赖边，避免高频工作台走旧图谱 N+1 路径。
        GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
        NodeDTO graphNode = graph.getNodes() == null ? null : graph.getNodes().stream()
                .filter(node -> nodeName != null && nodeName.equals(node.getName()))
                .findFirst()
                .orElse(null);
        if (StringUtils.isBlank(nodeName) || graphNode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + nodeName);
        }
        NodeWorkbenchVO vo = new NodeWorkbenchVO();
        Map<String, LearningMastery> masteryMap = masteryMapOf(user.getId());
        LearningMastery masteryRow = masteryMap.get(nodeName);
        List<LearningEvent> nodeEvents = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .eq(LearningEvent::getNodeName, nodeName)
                        .orderByAsc(LearningEvent::getCreateTime)
                        .orderByAsc(LearningEvent::getId));
        LearningEvent latestNodeEvent = nodeEvents.isEmpty() ? null : nodeEvents.get(nodeEvents.size() - 1);
        vo.setMastery(masteryNodeFromGraph(graphNode, masteryRow, latestNodeEvent));
        // 关联已发布普通题
        List<Long> questionIds = learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getNodeName, nodeName))
                .stream().map(LearningQuestionNode::getQuestionId).collect(Collectors.toList());
        vo.setQuestions(learningQuestionService.getQuestionVOs(questionIds, user));
        vo.setEvents(eventVOs(nodeEvents, MAX_EVENTS));
        // 节点内推荐
        List<RecommendationVO> recommendations = new ArrayList<>();
        MasteryNodeVO mastery = vo.getMastery();
        if (mastery != null && !MasteryStatusEnum.GRAY.name().equals(mastery.getStatus())) {
            if (MasteryStatusEnum.RED.name().equals(mastery.getStatus())) {
                recommendations.add(rec(nodeName, "WEAK_NODE",
                        String.format("该节点当前薄弱（掌握度 %.0f%%），建议先完成下方基础题", mastery.getPKnown() * 100), 100));
            } else if (MasteryStatusEnum.YELLOW.name().equals(mastery.getStatus())) {
                recommendations.add(rec(nodeName, "PRACTICE",
                        String.format("待巩固（掌握度 %.0f%%），建议练习巩固", mastery.getPKnown() * 100), 60));
            } else if (mastery.getConfidence() != null && mastery.getConfidence() < 0.5) {
                recommendations.add(rec(nodeName, "PRACTICE",
                        "掌握度较高但证据来源较少（可信度低），建议换题型验证", 40));
            }
        }
        vo.setRecommendations(recommendations);
        // 共享学习素材（Phase 8 §12.2）：仅消费 PUBLISHED，未配置保持 null 由前端回落
        com.yupi.springbootinit.model.entity.LearningNodeContent content =
                learningNodeContentMapper.selectOne(new LambdaQueryWrapper<com.yupi.springbootinit.model.entity.LearningNodeContent>()
                        .eq(com.yupi.springbootinit.model.entity.LearningNodeContent::getNodeName, nodeName)
                        .eq(com.yupi.springbootinit.model.entity.LearningNodeContent::getStatus, "PUBLISHED")
                        .eq(com.yupi.springbootinit.model.entity.LearningNodeContent::getIsDelete, 0)
                        .last("limit 1"));
        if (content != null) {
            vo.setContentMd(content.getContentMd());
            vo.setResourceLinks(content.getResourceLinks());
        }
        fillWorkbenchContract(user, nodeName, vo, graph, masteryMap, nodeEvents);
        return vo;
    }

    /**
     * Phase 2 契约（§6.3）：前置/后继（带状态）、普通题统计、常见误区、
     * 关联实验（最近终态）、最近失败证据、模拟器入口。全部真实数据。
     */
    private void fillWorkbenchContract(User user, String nodeName, NodeWorkbenchVO vo,
                                       GraphDataDTO graph,
                                       Map<String, LearningMastery> masteryMap,
                                       List<LearningEvent> nodeEvents) {
        Map<String, String> idToName = new HashMap<>();
        if (graph.getNodes() != null) {
            for (NodeDTO node : graph.getNodes()) {
                idToName.put(node.getId(), node.getName());
            }
        }

        // 前置/后继（dependent 边：source 依赖 target）
        List<NodeRefVO> prerequisites = new ArrayList<>();
        List<NodeRefVO> successors = new ArrayList<>();
        for (LinkDTO link : graph.getLinks() == null ? Collections.<LinkDTO>emptyList() : graph.getLinks()) {
            if (!"dependent".equals(link.getRelationType())) {
                continue;
            }
            String sourceName = idToName.get(link.getSource());
            String targetName = idToName.get(link.getTarget());
            if (nodeName.equals(sourceName) && targetName != null) {
                prerequisites.add(toNodeRef(targetName, masteryMap));
            }
            if (nodeName.equals(targetName) && sourceName != null) {
                successors.add(toNodeRef(sourceName, masteryMap));
            }
        }
        vo.setPrerequisites(prerequisites);
        vo.setSuccessors(successors);
        vo.setPrerequisiteNodes(prerequisites.stream().map(NodeRefVO::getName).collect(Collectors.toList()));

        // 普通题统计（来自已发布题目列表 + 当前用户作答状态）
        QuestionStatsVO stats = new QuestionStatsVO();
        stats.setTotal(vo.getQuestions() == null ? 0 : vo.getQuestions().size());
        int attempted = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        List<String> misconceptions = new ArrayList<>();
        if (vo.getQuestions() != null) {
            for (LearningQuestionVO q : vo.getQuestions()) {
                if (Boolean.TRUE.equals(q.getHasAttempted())) {
                    attempted++;
                }
                if (q.getType() != null) {
                    byType.merge(q.getType(), 1, Integer::sum);
                }
                if (q.getMisconception() != null && !q.getMisconception().isBlank()
                        && !misconceptions.contains(q.getMisconception())
                        && misconceptions.size() < 3) {
                    misconceptions.add(q.getMisconception());
                }
            }
        }
        stats.setAttempted(attempted);
        stats.setUnattempted(stats.getTotal() - attempted);
        stats.setByType(byType);
        vo.setQuestionStats(stats);
        vo.setMisconceptions(misconceptions);

        // 关联编程实验（共享资源解析器：tags JSON 精确匹配，U13）+ 当前用户最近提交终态
        List<ExperimentSummaryVO> experiments = new ArrayList<>();
        List<Question> tagged = resourceResolver.findExperimentsByNode(nodeName);
        Map<Long, String> latestSubmitResults = latestSubmitResults(user.getId(), tagged);
        for (Question q : tagged) {
            ExperimentSummaryVO experimentVo = new ExperimentSummaryVO();
            experimentVo.setId(q.getId());
            experimentVo.setTitle(q.getTitle());
            experimentVo.setLastResult(latestSubmitResults.get(q.getId()));
            experiments.add(experimentVo);
        }
        vo.setExperiments(experiments);

        // 最近失败证据（指向真实资源 id）+ §11.3 失败归类（当前失误/历史失误/已纠正）
        FailureStateClassifier.Result failureState = FailureStateClassifier.classify(nodeEvents);
        if (failureState.lastFail != null) {
            vo.setLastFailure(buildLastFailure(failureState.lastFail, failureState.state));
        }

        // 模拟器入口（共享资源解析器：按已配置节点-算法映射；无匹配返回 null，前端不显示入口）
        vo.setSimulationEntry(resourceResolver.simulationEntryOf(nodeName));
    }

    /**
     * §11.3 归因呈现：规则归因 + 失败归类 + 依据强度标签。
     * 固定规则强度不包装成校准概率：confidence 只用于映射“关联明确/需要进一步确认”标签。
     */
    private LastFailureVO buildLastFailure(LearningEvent lastFail, String failureState) {
        LastFailureVO failureVo = new LastFailureVO();
        failureVo.setResourceType(lastFail.getResourceType());
        failureVo.setResourceId(lastFail.getResourceId());
        failureVo.setResult(lastFail.getResult());
        failureVo.setEvidence(lastFail.getEvidence());
        failureVo.setCreateTime(lastFail.getCreateTime());
        String judgeMessage = null;
        try {
            JSONObject evidenceJson = JSONUtil.parseObj(
                    lastFail.getEvidence() == null ? "{}" : lastFail.getEvidence());
            judgeMessage = evidenceJson.getStr("note");
        } catch (Exception e) {
            // 证据格式异常时按空消息归因
        }
        LearningAttributionService.AttributionResult attribution =
                LearningAttributionService.attribute(lastFail.getEventType(), judgeMessage, lastFail.getNodeName());
        failureVo.setErrorType(attribution.getErrorType());
        failureVo.setConfidence(attribution.getConfidence());
        failureVo.setRecommendedAction(attribution.getRecommendedAction());
        failureVo.setKnowledgeImpact(attribution.getKnowledgeImpact());
        failureVo.setFailureState(failureState);
        failureVo.setCertaintyLabel(attribution.getConfidence() >= 0.8 ? "关联明确" : "需要进一步确认");
        failureVo.setAttributionBasis(attribution.getEvidence());
        return failureVo;
    }

    private NodeRefVO toNodeRef(String name, Map<String, LearningMastery> masteryMap) {
        NodeRefVO ref = new NodeRefVO();
        ref.setName(name);
        LearningMastery mastery = masteryMap.get(name);
        ref.setStatus(mastery == null ? "GRAY" : mastery.getStatus());
        return ref;
    }

    /**
     * 批量取当前用户每个编程实验的最近终态消息。
     * NOT EXISTS 排除同一学生同题更晚的提交，不将提交代码或整段历史读入内存。
     */
    private Map<Long, String> latestSubmitResults(Long userId, List<Question> questions) {
        if (CollectionUtils.isEmpty(questions)) {
            return Collections.emptyMap();
        }
        List<Long> questionIds = questions.stream().map(Question::getId).distinct().collect(Collectors.toList());
        List<QuestionSubmit> submits = questionSubmitService.list(
                new LambdaQueryWrapper<QuestionSubmit>()
                        .select(QuestionSubmit::getQuestionId, QuestionSubmit::getJudgeInfo, QuestionSubmit::getStatus)
                        .eq(QuestionSubmit::getUserId, userId)
                        .in(QuestionSubmit::getQuestionId, questionIds)
                        .apply("NOT EXISTS (SELECT 1 FROM question_submit newer "
                                + "WHERE newer.userId = question_submit.userId "
                                + "AND newer.questionId = question_submit.questionId AND newer.isDelete = 0 "
                                + "AND (newer.createTime > question_submit.createTime "
                                + "OR (newer.createTime = question_submit.createTime AND newer.id > question_submit.id)))")
                        .orderByDesc(QuestionSubmit::getCreateTime)
                        .orderByDesc(QuestionSubmit::getId));
        Map<Long, String> result = new HashMap<>();
        for (QuestionSubmit submit : submits) {
            if (result.containsKey(submit.getQuestionId())) {
                continue;
            }
            String raw = submit.getJudgeInfo();
            if (StringUtils.isBlank(raw)) {
                result.put(submit.getQuestionId(), Integer.valueOf(0).equals(submit.getStatus()) ? "待判题"
                        : Integer.valueOf(1).equals(submit.getStatus()) ? "判题中" : null);
                continue;
            }
            try {
                JSONObject json = JSONUtil.parseObj(raw);
                result.put(submit.getQuestionId(), json.getStr("message", raw));
            } catch (Exception e) {
                result.put(submit.getQuestionId(), raw);
            }
        }
        return result;
    }

    @Override
    public List<RecommendationVO> recommendations(User user) {
        List<RecommendationVO> result = new ArrayList<>();
        // 掌握状态行仅在产生首个独立观测后创建，行存在即代表有证据
        List<LearningMastery> masteries = learningMasteryMapper.selectList(
                new LambdaQueryWrapper<LearningMastery>()
                        .eq(LearningMastery::getUserId, user.getId()));
        // 薄弱节点优先（pKnown 升序）
        List<LearningMastery> weak = new ArrayList<>();
        for (LearningMastery m : masteries) {
            if (MasteryStatusEnum.RED.name().equals(m.getStatus())
                    || MasteryStatusEnum.YELLOW.name().equals(m.getStatus())) {
                weak.add(m);
            }
        }
        weak.sort(Comparator.comparingDouble(LearningMastery::getPKnown));
        GraphDataDTO graph = null;
        if (!weak.isEmpty()) {
            try {
                graph = knowledgeGraphService.getGraphSnapshot();
            } catch (Exception e) {
                // 图谱暂不可用时仍返回基于本人掌握记录的推荐。
            }
        }
        Map<String, LearningMastery> masteryMap = masteries.stream()
                .collect(Collectors.toMap(LearningMastery::getNodeName, mastery -> mastery));
        Set<String> prerequisiteAdded = new HashSet<>();
        int limit = 3;
        for (int i = 0; i < weak.size() && i < limit; i++) {
            LearningMastery m = weak.get(i);
            String statusText = MasteryStatusEnum.RED.name().equals(m.getStatus()) ? "薄弱" : "待巩固";
            result.add(rec(m.getNodeName(), "WEAK_NODE",
                    String.format("掌握度 %.0f%%（%s），最近一次学习后建议针对性补救", m.getPKnown() * 100, statusText),
                    100 - m.getPKnown() * 50));
            // 前置缺口
            for (String pre : prerequisiteNames(m.getNodeName(), graph)) {
                if (prerequisiteAdded.contains(pre)) {
                    continue;
                }
                LearningMastery preMastery = masteryMap.get(pre);
                boolean noEvidence = preMastery == null || totalEvidence(preMastery) == 0;
                if (noEvidence) {
                    prerequisiteAdded.add(pre);
                    result.add(rec(pre, "PREREQUISITE",
                            String.format("「%s」的前置知识，你还没有学习记录，建议先学", m.getNodeName()), 80));
                }
            }
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.size() > 5 ? result.subList(0, 5) : result;
    }

    @Override
    public MasteryNodeVO nodeMastery(Long userId, String nodeName) {
        LearningMastery mastery = learningMasteryMapper.selectOne(new LambdaQueryWrapper<LearningMastery>()
                .eq(LearningMastery::getUserId, userId)
                .eq(LearningMastery::getNodeName, nodeName));
        MasteryNodeVO vo = new MasteryNodeVO();
        vo.setName(nodeName);
        // 概念卡：节点描述来自 Neo4j EntityNode
        try {
            List<com.yupi.springbootinit.model.entity.EntityNode> graphNodes =
                    knowledgeGraphService.findByName(nodeName);
            if (CollectionUtils.isNotEmpty(graphNodes)) {
                vo.setId(graphNodes.get(0).getId() == null ? null : String.valueOf(graphNodes.get(0).getId()));
                vo.setCategory(graphNodes.get(0).getLabel());
                vo.setDescription(graphNodes.get(0).getDescription());
            }
        } catch (Exception e) {
            // 图谱不可用时不阻塞掌握状态读取
        }
        applyMastery(vo, mastery);
        if (mastery != null && mastery.getLastEventId() != null) {
            LearningEvent event = learningEventMapper.selectById(mastery.getLastEventId());
            if (event != null) {
                vo.setRecentReason(evidenceSummary(event));
                vo.setLastEventType(event.getEventType());
                vo.setLastResult(event.getResult());
            }
        }
        return vo;
    }

    /** Assemble node metadata from the already-fetched graph snapshot. */
    private MasteryNodeVO masteryNodeFromGraph(NodeDTO node, LearningMastery mastery,
                                               LearningEvent latestEvent) {
        MasteryNodeVO vo = new MasteryNodeVO();
        vo.setId(node.getId());
        vo.setName(node.getName());
        vo.setCategory(node.getCategory());
        vo.setChapter(node.getChapter());
        vo.setDescription(node.getDescription());
        applyMastery(vo, mastery);
        if (latestEvent != null) {
            vo.setRecentReason(evidenceSummary(latestEvent));
            vo.setLastEventType(latestEvent.getEventType());
            vo.setLastResult(latestEvent.getResult());
        }
        return vo;
    }

    // region Phase 7 §11 学习记录

    @Override
    public TimelineVO myTimeline(User user, String nodeName, String eventType,
                                 String startTime, String endTime, long current, long pageSize) {
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR, "每页最多 100 条");
        LambdaQueryWrapper<LearningEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningEvent::getUserId, user.getId());
        wrapper.eq(StringUtils.isNotBlank(nodeName), LearningEvent::getNodeName, nodeName);
        wrapper.eq(StringUtils.isNotBlank(eventType), LearningEvent::getEventType, eventType);
        if (StringUtils.isNotBlank(startTime)) {
            wrapper.ge(LearningEvent::getCreateTime, cn.hutool.core.date.DateUtil.parse(startTime));
        }
        if (StringUtils.isNotBlank(endTime)) {
            wrapper.le(LearningEvent::getCreateTime, cn.hutool.core.date.DateUtil.parse(endTime));
        }
        // §11.2：事件按 createTime,id 排序（同一事务的多节点事件顺序稳定）
        wrapper.orderByDesc(LearningEvent::getCreateTime).orderByDesc(LearningEvent::getId);
        Page<LearningEvent> page = learningEventMapper.selectPage(new Page<>(current, pageSize), wrapper);
        TimelineVO vo = new TimelineVO();
        vo.setTotal(page.getTotal());
        List<LearningEventVO> events = new ArrayList<>();
        for (LearningEvent e : page.getRecords()) {
            events.add(toEventVO(e, null));
        }
        vo.setEvents(events);
        return vo;
    }

    @Override
    public MasterySnapshotVO replaySnapshot(User user, Long atEventId) {
        if (atEventId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回放事件 id 不能为空");
        }
        LearningEvent anchor = learningEventMapper.selectById(atEventId);
        if (anchor == null || !user.getId().equals(anchor.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "回放事件不存在或无权访问");
        }
        // 该用户全部事件按 (createTime,id) 升序，内存推进到锚定事件为止
        List<LearningEvent> allEvents = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .orderByAsc(LearningEvent::getCreateTime)
                        .orderByAsc(LearningEvent::getId));
        Map<String, LearningEvent> lastEventByNode = new HashMap<>();
        Map<String, LearningEvent> snapshotEventByNode = new HashMap<>();
        for (LearningEvent e : allEvents) {
            // 锚定事件组整体计入该时点：避免停在多节点事务的部分更新状态
            boolean inGroup = anchor.getEventGroupKey() != null
                    && anchor.getEventGroupKey().equals(e.getEventGroupKey());
            if (!inGroup && afterAnchor(e, anchor)) {
                continue;
            }
            lastEventByNode.put(e.getNodeName(), e);
            if (StringUtils.isNotBlank(e.getSnapshotAfter())) {
                snapshotEventByNode.put(e.getNodeName(), e);
            }
        }

        // 图谱投影：底图使用当前课程结构（不宣称重建历史拓扑）
        GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
        PersonalGraphVO graphVo = new PersonalGraphVO();
        List<MasteryNodeVO> nodes = new ArrayList<>();
        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("GRAY", 0);
        statusCounts.put("RED", 0);
        statusCounts.put("YELLOW", 0);
        statusCounts.put("GREEN", 0);
        int evidenceCount = 0;
        if (graph.getNodes() != null) {
            for (NodeDTO node : graph.getNodes()) {
                MasteryNodeVO vo = new MasteryNodeVO();
                vo.setId(node.getId());
                vo.setName(node.getName());
                vo.setCategory(node.getCategory());
                vo.setChapter(node.getChapter());
                vo.setDescription(node.getDescription());
                LearningEvent last = lastEventByNode.get(node.getName());
                LearningEvent snapEvent = snapshotEventByNode.get(node.getName());
                MasterySnapshotCodec.MasterySnapshot snap = snapEvent == null
                        ? null : MasterySnapshotCodec.parse(snapEvent.getSnapshotAfter());
                if (snap != null) {
                    // 该时点有完整快照：展示真实记录状态
                    vo.setPKnown(snap.pKnown);
                    vo.setConfidence(snap.confidence);
                    vo.setConceptScore(snap.conceptScore);
                    vo.setPracticeScore(snap.practiceScore);
                    vo.setConceptEvidenceCount(snap.conceptEvidenceCount == null ? 0 : snap.conceptEvidenceCount);
                    vo.setPracticeEvidenceCount(snap.practiceEvidenceCount == null ? 0 : snap.practiceEvidenceCount);
                    vo.setStatus(StringUtils.isBlank(snap.status) ? MasteryStatusEnum.GRAY.name() : snap.status);
                    vo.setSnapshotAvailable(true);
                } else if (last != null) {
                    // 历史事件（升级前）无完整快照：展示已记录数值，不凭分数猜状态
                    vo.setPKnown(last.getMasteryAfter());
                    vo.setConceptEvidenceCount(0);
                    vo.setPracticeEvidenceCount(0);
                    vo.setStatus(MasteryStatusEnum.GRAY.name());
                    vo.setSnapshotAvailable(false);
                } else {
                    // 该时点未学习
                    applyMastery(vo, null);
                }
                if (last != null) {
                    vo.setLastEventId(last.getId());
                    vo.setRecentReason(evidenceSummary(last));
                    vo.setLastEventType(last.getEventType());
                    vo.setLastResult(last.getResult());
                }
                statusCounts.merge(vo.getStatus() == null ? "GRAY" : vo.getStatus(), 1, Integer::sum);
                if (vo.getStatus() != null && !"GRAY".equals(vo.getStatus())) {
                    evidenceCount++;
                }
                nodes.add(vo);
            }
        }
        graphVo.setNodes(nodes);
        graphVo.setLinks(graph.getLinks() == null ? Collections.emptyList() : graph.getLinks());
        graphVo.setCategories(graph.getCategories() == null ? Collections.emptyList() : graph.getCategories());
        graphVo.setTotalNodes(nodes.size());
        graphVo.setTotalLinks(graph.getLinks() == null ? 0 : graph.getLinks().size());
        graphVo.setEvidenceCount(evidenceCount);
        graphVo.setStatusCounts(statusCounts);
        graphVo.setChapters(chapterVOs(graph));
        graphVo.setLastUpdatedAt(anchor.getCreateTime());

        MasterySnapshotVO result = new MasterySnapshotVO();
        result.setAtEventId(anchor.getId());
        result.setReplayAt(anchor.getCreateTime());
        result.setEventGroupKey(anchor.getEventGroupKey());
        result.setGraph(graphVo);
        return result;
    }

    @Override
    public List<WeakNodeVO> weaknessNodes(User user) {
        List<LearningMastery> masteries = learningMasteryMapper.selectList(
                new LambdaQueryWrapper<LearningMastery>().eq(LearningMastery::getUserId, user.getId()));
        List<LearningMastery> weak = new ArrayList<>();
        for (LearningMastery m : masteries) {
            if (MasteryStatusEnum.RED.name().equals(m.getStatus())
                    || MasteryStatusEnum.YELLOW.name().equals(m.getStatus())) {
                weak.add(m);
            }
        }
        weak.sort(Comparator.comparingDouble(LearningMastery::getPKnown));
        Map<String, String> chapterOf = chapterMap();
        List<WeakNodeVO> result = new ArrayList<>();
        for (LearningMastery m : weak) {
            WeakNodeVO vo = new WeakNodeVO();
            vo.setNodeName(m.getNodeName());
            vo.setChapter(chapterOf.get(m.getNodeName()));
            vo.setStatus(m.getStatus());
            vo.setPKnown(m.getPKnown());
            vo.setConceptScore(m.getConceptScore());
            vo.setPracticeScore(m.getPracticeScore());
            vo.setConceptEvidenceCount(conceptCount(m));
            vo.setPracticeEvidenceCount(practiceCount(m));
            List<LearningEvent> events = learningEventMapper.selectList(
                    new LambdaQueryWrapper<LearningEvent>()
                            .eq(LearningEvent::getUserId, user.getId())
                            .eq(LearningEvent::getNodeName, m.getNodeName())
                            .orderByAsc(LearningEvent::getCreateTime)
                            .orderByAsc(LearningEvent::getId));
            FailureStateClassifier.Result fr = FailureStateClassifier.classify(events);
            vo.setFailureState(fr.state);
            if (!events.isEmpty()) {
                vo.setLastActivityTime(events.get(events.size() - 1).getCreateTime());
            }
            if (fr.lastFail != null) {
                vo.setLastFailure(buildLastFailure(fr.lastFail, fr.state));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 事件是否晚于锚定事件（按 createTime,id 字典序）
     */
    private boolean afterAnchor(LearningEvent e, LearningEvent anchor) {
        if (e.getCreateTime() == null || anchor.getCreateTime() == null) {
            return false;
        }
        int cmp = e.getCreateTime().compareTo(anchor.getCreateTime());
        if (cmp != 0) {
            return cmp > 0;
        }
        return e.getId() != null && anchor.getId() != null && e.getId() > anchor.getId();
    }

    private List<ChapterSummaryVO> chapterVOs(GraphDataDTO graph) {
        List<ChapterSummaryVO> chapters = new ArrayList<>();
        if (graph.getChapterSummary() != null) {
            for (Map.Entry<String, Integer> entry : graph.getChapterSummary().entrySet()) {
                ChapterSummaryVO chapterVo = new ChapterSummaryVO();
                chapterVo.setName(entry.getKey());
                chapterVo.setCount(entry.getValue());
                chapters.add(chapterVo);
            }
        }
        return chapters;
    }

    private Map<String, String> chapterMap() {
        Map<String, String> map = new HashMap<>();
        try {
            GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
            if (graph.getNodes() != null) {
                for (NodeDTO node : graph.getNodes()) {
                    map.put(node.getName(), node.getChapter());
                }
            }
        } catch (Exception e) {
            // 图谱不可用时章节留空，不阻塞待巩固列表
        }
        return map;
    }

    // endregion

    // region 私有工具

    private void applyMastery(MasteryNodeVO vo, LearningMastery mastery) {
        if (mastery == null || totalEvidence(mastery) == 0) {
            vo.setPKnown(null);
            vo.setConfidence(null);
            vo.setConceptScore(null);
            vo.setPracticeScore(null);
            vo.setConceptEvidenceCount(0);
            vo.setPracticeEvidenceCount(0);
            vo.setStatus(MasteryStatusEnum.GRAY.name());
            return;
        }
        vo.setPKnown(mastery.getPKnown());
        vo.setConfidence(mastery.getConfidence());
        vo.setConceptScore(mastery.getConceptScore());
        vo.setPracticeScore(mastery.getPracticeScore());
        vo.setConceptEvidenceCount(conceptCount(mastery));
        vo.setPracticeEvidenceCount(practiceCount(mastery));
        vo.setStatus(mastery.getStatus());
        vo.setLastEventId(mastery.getLastEventId());
    }

    private int totalEvidence(LearningMastery mastery) {
        return conceptCount(mastery) + practiceCount(mastery);
    }

    private int conceptCount(LearningMastery mastery) {
        return mastery.getConceptEvidenceCount() == null ? 0 : mastery.getConceptEvidenceCount();
    }

    private int practiceCount(LearningMastery mastery) {
        return mastery.getPracticeEvidenceCount() == null ? 0 : mastery.getPracticeEvidenceCount();
    }

    private Map<String, LearningMastery> masteryMapOf(Long userId) {
        Map<String, LearningMastery> map = new HashMap<>();
        List<LearningMastery> rows = learningMasteryMapper.selectList(
                new LambdaQueryWrapper<LearningMastery>().eq(LearningMastery::getUserId, userId));
        for (LearningMastery row : rows) {
            map.put(row.getNodeName(), row);
        }
        return map;
    }

    /**
     * 批量取每个节点最近事件（一次查询，避免图谱逐节点查询）
     */
    private Map<Long, LearningEvent> lastEventsOf(Iterable<LearningMastery> masteries) {
        Map<Long, LearningEvent> map = new HashMap<>();
        List<Long> eventIds = new ArrayList<>();
        for (LearningMastery m : masteries) {
            if (m.getLastEventId() != null) {
                eventIds.add(m.getLastEventId());
            }
        }
        if (eventIds.isEmpty()) {
            return map;
        }
        List<LearningEvent> events = learningEventMapper.selectBatchIds(eventIds);
        for (LearningEvent event : events) {
            map.put(event.getId(), event);
        }
        return map;
    }

    /**
     * 从事件 evidence JSON 中提取可读摘要（resultText + note）
     */
    static String evidenceSummary(LearningEvent event) {
        String evidence = event.getEvidence();
        if (StringUtils.isBlank(evidence)) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(evidence);
            String resultText = json.getStr("resultText", "");
            String note = json.getStr("note", "");
            String title = json.getStr("title", "");
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotBlank(title)) {
                sb.append(title).append("：");
            }
            sb.append(resultText);
            if (StringUtils.isNotBlank(note)) {
                sb.append("（").append(note).append("）");
            }
            return sb.toString();
        } catch (Exception e) {
            return evidence;
        }
    }

    private List<LearningEventVO> eventVOs(List<LearningEvent> events, int limit) {
        List<LearningEventVO> vos = new ArrayList<>();
        int from = Math.max(0, events.size() - limit);
        for (int index = events.size() - 1; index >= from; index--) {
            vos.add(toEventVO(events.get(index), null));
        }
        return vos;
    }

    static LearningEventVO toEventVO(LearningEvent e, String anonymousName) {
        LearningEventVO vo = new LearningEventVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setAnonymousName(anonymousName);
        vo.setNodeName(e.getNodeName());
        vo.setEventType(e.getEventType());
        vo.setResourceType(e.getResourceType());
        vo.setResourceId(e.getResourceId());
        vo.setResult(e.getResult());
        vo.setIsFirst(e.getIsFirst() != null && e.getIsFirst() == 1);
        vo.setMasteryBefore(e.getMasteryBefore());
        vo.setMasteryAfter(e.getMasteryAfter());
        vo.setEvidence(e.getEvidence());
        vo.setEventGroupKey(e.getEventGroupKey());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /** 前置节点名：从共享图谱快照的依赖边中找 source=当前节点的 target。 */
    private List<String> prerequisiteNames(String nodeName, GraphDataDTO graph) {
        List<String> result = new ArrayList<>();
        if (graph == null || graph.getNodes() == null || graph.getLinks() == null) {
            return result;
        }
        Map<String, String> idToName = new HashMap<>();
        for (NodeDTO node : graph.getNodes()) {
            idToName.put(node.getId(), node.getName());
        }
        for (LinkDTO link : graph.getLinks()) {
            if (RELATION_DEPENDENT.equals(link.getRelationType())
                    && nodeName.equals(idToName.get(link.getSource()))) {
                String targetName = idToName.get(link.getTarget());
                if (targetName != null) {
                    result.add(targetName);
                }
            }
        }
        return result;
    }

    private RecommendationVO rec(String nodeName, String type, String reason, double score) {
        RecommendationVO vo = new RecommendationVO();
        vo.setNodeName(nodeName);
        vo.setType(type);
        vo.setReason(reason);
        vo.setScore(Math.round(score * 100.0) / 100.0);
        return vo;
    }

    // endregion
}
