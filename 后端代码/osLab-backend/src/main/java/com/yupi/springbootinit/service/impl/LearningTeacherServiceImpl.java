package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.LearningResourceResolver;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.mapper.LearningNodeContentMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.mapper.QuestionMapper;
import com.yupi.springbootinit.mapper.QuestionSubmitMapper;
import com.yupi.springbootinit.mapper.StudentsMapper;
import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningNodeContent;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.entity.Students;
import com.yupi.springbootinit.model.enums.learning.LearningEventTypeEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import com.yupi.springbootinit.model.vo.learning.ChapterCoverageVO;
import com.yupi.springbootinit.model.vo.learning.ClassOptionVO;
import com.yupi.springbootinit.model.vo.learning.LearningEventVO;
import com.yupi.springbootinit.model.vo.learning.MisconceptionStatVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillExperimentVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillQuestionVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillVO;
import com.yupi.springbootinit.model.vo.learning.NodeHeatVO;
import com.yupi.springbootinit.model.vo.learning.QuestionAccuracyVO;
import com.yupi.springbootinit.model.vo.learning.TeacherDashboardVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningTeacherService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 教师班级看板服务实现（契约 §3.5 / Phase 8）
 *
 * <p>全部统计由真实学习事件/掌握度/作答聚合而来，可用 SQL 直接对照。
 * 班级成员只从现有 students 表解析（userId/className），P0 不维护班级成员表。
 * Phase 8 §12：班级列表真实化、节点下钻精确关联、时间线班级约束、章节资源完整度。
 */
@Service
public class LearningTeacherServiceImpl implements LearningTeacherService {

    /** 未关联资源节点最多展示数（章节完整度） */
    private static final int MAX_UNCOVERED_NODES = 20;

    @Resource
    private StudentsMapper studentsMapper;

    @Resource
    private LearningMasteryMapper learningMasteryMapper;

    @Resource
    private LearningEventMapper learningEventMapper;

    @Resource
    private LearningAttemptMapper learningAttemptMapper;

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionSubmitMapper questionSubmitMapper;

    @Resource
    private LearningNodeContentMapper learningNodeContentMapper;

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private LearningResourceResolver resourceResolver;

    @Override
    public TeacherDashboardVO dashboard(String className) {
        if (StringUtils.isBlank(className)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "班级名称不能为空");
        }
        List<Long> studentIds = studentIdsOfClass(className);
        TeacherDashboardVO vo = new TeacherDashboardVO();
        vo.setClassName(className);
        vo.setStudentCount(studentIds.size());
        if (CollectionUtils.isEmpty(studentIds)) {
            vo.setNodeHeat(new ArrayList<>());
            vo.setWeakTop(new ArrayList<>());
            vo.setQuestionAccuracy(new ArrayList<>());
            vo.setExperimentPassRate(null);
            vo.setRecentEvents(new ArrayList<>());
            return vo;
        }
        vo.setNodeHeat(nodeHeat(studentIds));
        vo.setWeakTop(weakTop(vo.getNodeHeat()));
        vo.setQuestionAccuracy(questionAccuracy(studentIds));
        vo.setExperimentPassRate(experimentPassRate(studentIds));
        vo.setRecentEvents(recentEvents(studentIds));
        return vo;
    }

    @Override
    public TimelineVO timeline(Long userId, String className, String nodeName, String eventType, long current, long pageSize) {
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR, "每页最多 100 条");
        ThrowUtils.throwIf(StringUtils.isBlank(className), ErrorCode.PARAMS_ERROR, "班级名称不能为空");
        List<Long> members = studentIdsOfClass(className);
        LambdaQueryWrapper<LearningEvent> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            // 指定学生必须属于所选班级，不允许跨班查看
            ThrowUtils.throwIf(!members.contains(userId), ErrorCode.PARAMS_ERROR, "该学生不属于所选班级");
            wrapper.eq(LearningEvent::getUserId, userId);
        } else {
            // 未指定学生也限定班级成员集合，不落到全库事件
            if (members.isEmpty()) {
                TimelineVO empty = new TimelineVO();
                empty.setTotal(0L);
                empty.setEvents(new ArrayList<>());
                return empty;
            }
            wrapper.in(LearningEvent::getUserId, members);
        }
        wrapper.eq(StringUtils.isNotBlank(nodeName), LearningEvent::getNodeName, nodeName);
        wrapper.eq(StringUtils.isNotBlank(eventType), LearningEvent::getEventType, eventType);
        wrapper.orderByDesc(LearningEvent::getCreateTime);
        Page<LearningEvent> page = learningEventMapper.selectPage(new Page<>(current, pageSize), wrapper);
        TimelineVO vo = new TimelineVO();
        vo.setTotal(page.getTotal());
        List<LearningEventVO> events = new ArrayList<>();
        Set<Long> userIds = new HashSet<>();
        for (LearningEvent event : page.getRecords()) {
            userIds.add(event.getUserId());
        }
        Map<Long, String> anonymousNames = anonymousNames(userIds);
        for (LearningEvent event : page.getRecords()) {
            events.add(LearningMasteryServiceImpl.toEventVO(event, anonymousNames.get(event.getUserId())));
        }
        vo.setEvents(events);
        return vo;
    }

    @Override
    public List<ClassOptionVO> classes() {
        List<Students> rows = studentsMapper.selectList(new LambdaQueryWrapper<Students>()
                .eq(Students::getIsDelete, 0)
                .isNotNull(Students::getUserId));
        Map<String, Integer> countByClass = new TreeMap<>();
        for (Students row : rows) {
            if (StringUtils.isNotBlank(row.getClassName())) {
                countByClass.merge(row.getClassName(), 1, Integer::sum);
            }
        }
        List<ClassOptionVO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countByClass.entrySet()) {
            ClassOptionVO vo = new ClassOptionVO();
            vo.setClassName(entry.getKey());
            vo.setStudentCount(entry.getValue());
            result.add(vo);
        }
        return result;
    }

    @Override
    public NodeDrillVO nodeDetail(String className, String nodeName) {
        ThrowUtils.throwIf(StringUtils.isBlank(className), ErrorCode.PARAMS_ERROR, "班级名称不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(nodeName), ErrorCode.PARAMS_ERROR, "节点名称不能为空");
        ThrowUtils.throwIf(CollectionUtils.isEmpty(knowledgeGraphService.findByName(nodeName)),
                ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + nodeName);
        List<Long> members = studentIdsOfClass(className);
        NodeDrillVO vo = new NodeDrillVO();
        vo.setClassName(className);
        vo.setNodeName(nodeName);
        vo.setStudentCount(members.size());

        // 掌握证据：有证据人数 / 薄弱人数 / 平均掌握度 / 学习覆盖率
        List<LearningMastery> masteries = members.isEmpty() ? new ArrayList<>()
                : learningMasteryMapper.selectList(new LambdaQueryWrapper<LearningMastery>()
                        .in(LearningMastery::getUserId, members)
                        .eq(LearningMastery::getNodeName, nodeName));
        vo.setEvidenceCount(masteries.size());
        int weak = 0;
        double sum = 0;
        for (LearningMastery m : masteries) {
            sum += m.getPKnown();
            if (MasteryStatusEnum.RED.name().equals(m.getStatus())) {
                weak++;
            }
        }
        vo.setWeakCount(weak);
        vo.setAvgPKnown(masteries.isEmpty() ? null
                : Math.round(sum / masteries.size() * 10000.0) / 10000.0);
        vo.setCoverage(members.isEmpty() ? 0.0
                : Math.round((double) masteries.size() / members.size() * 10000.0) / 10000.0);

        // 关联测评题：映射表精确匹配（不按标题模糊）
        List<Long> mappedIds = learningQuestionNodeMapper.selectList(
                        new LambdaQueryWrapper<LearningQuestionNode>()
                                .eq(LearningQuestionNode::getNodeName, nodeName))
                .stream().map(LearningQuestionNode::getQuestionId).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, LearningQuestion> questionMap = new HashMap<>();
        if (!mappedIds.isEmpty()) {
            for (LearningQuestion q : learningQuestionMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestion>()
                            .in(LearningQuestion::getId, mappedIds)
                            .eq(LearningQuestion::getIsDelete, 0))) {
                questionMap.put(q.getId(), q);
            }
        }
        List<LearningAttempt> attempts = (!mappedIds.isEmpty() && !members.isEmpty())
                ? learningAttemptMapper.selectList(new LambdaQueryWrapper<LearningAttempt>()
                        .in(LearningAttempt::getUserId, members)
                        .in(LearningAttempt::getQuestionId, mappedIds))
                : new ArrayList<>();
        Map<Long, List<LearningAttempt>> attemptsByQuestion = new HashMap<>();
        for (LearningAttempt a : attempts) {
            attemptsByQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }
        List<NodeDrillQuestionVO> questionVos = new ArrayList<>();
        int totalFirst = 0;
        int totalFirstCorrect = 0;
        Map<String, Integer> misconceptionWrong = new LinkedHashMap<>();
        for (Map.Entry<Long, List<LearningAttempt>> entry : attemptsByQuestion.entrySet()) {
            LearningQuestion question = questionMap.get(entry.getKey());
            if (question == null) {
                continue;
            }
            NodeDrillQuestionVO qVo = buildQuestionStat(question, entry.getValue());
            questionVos.add(qVo);
            totalFirst += qVo.getFirstAttemptCount();
            totalFirstCorrect += qVo.getFirstCorrectCount();
            if (StringUtils.isNotBlank(question.getMisconception())) {
                int wrong = 0;
                for (LearningAttempt a : entry.getValue()) {
                    if (a.getIsCorrect() == 0) {
                        wrong++;
                    }
                }
                if (wrong > 0) {
                    misconceptionWrong.merge(question.getMisconception(), wrong, Integer::sum);
                }
            }
        }
        // 有映射但班级尚无作答的题目也要出现在下钻中（样本数 0，不给 0% 结论）
        for (LearningQuestion q : questionMap.values()) {
            if (!attemptsByQuestion.containsKey(q.getId())) {
                questionVos.add(buildQuestionStat(q, new ArrayList<>()));
            }
        }
        questionVos.sort(Comparator.comparing(NodeDrillQuestionVO::getQuestionId));
        vo.setQuestions(questionVos);
        // 首次测评正确率：分母=首次有效作答数；无样本为 null
        vo.setFirstAccuracy(totalFirst == 0 ? null
                : Math.round((double) totalFirstCorrect / totalFirst * 10000.0) / 10000.0);
        vo.setMisconceptionStats(topMisconceptions(misconceptionWrong));

        // 关联编程实验：tags JSON 精确匹配；按提交去重、排除系统故障
        Map<Long, String> submitResult = new LinkedHashMap<>();
        if (!members.isEmpty()) {
            for (LearningEvent e : learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                    .in(LearningEvent::getUserId, members)
                    .eq(LearningEvent::getNodeName, nodeName)
                    .eq(LearningEvent::getEventType, LearningEventTypeEnum.JUDGE.name()))) {
                // 同一提交多节点事件结果一致，按提交 id 去重
                submitResult.put(e.getResourceId(), e.getResult());
            }
        }
        vo.setExperimentPassRate(computeExperimentPassRate(submitResult));
        vo.setExperiments(experimentStats(resourceResolver.findExperimentsByNode(nodeName), submitResult));
        return vo;
    }

    @Override
    public List<ChapterCoverageVO> chapterCoverage() {
        GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
        Map<String, List<String>> nodesByChapter = new TreeMap<>();
        Set<String> allNodeNames = new HashSet<>();
        if (graph.getNodes() != null) {
            for (NodeDTO node : graph.getNodes()) {
                String chapter = StringUtils.isBlank(node.getChapter()) ? "未分类" : node.getChapter();
                nodesByChapter.computeIfAbsent(chapter, k -> new ArrayList<>()).add(node.getName());
                allNodeNames.add(node.getName());
            }
        }
        // 已发布素材节点
        Set<String> contentNodes = new HashSet<>();
        for (LearningNodeContent content : learningNodeContentMapper.selectList(
                new LambdaQueryWrapper<LearningNodeContent>()
                        .eq(LearningNodeContent::getStatus, "PUBLISHED")
                        .eq(LearningNodeContent::getIsDelete, 0))) {
            contentNodes.add(content.getNodeName());
        }
        // 有已发布测评题的节点（映射表精确匹配）
        Set<Long> publishedIds = new HashSet<>();
        for (LearningQuestion q : learningQuestionMapper.selectList(new LambdaQueryWrapper<LearningQuestion>()
                .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                .eq(LearningQuestion::getIsDelete, 0)
                .select(LearningQuestion::getId))) {
            publishedIds.add(q.getId());
        }
        Set<String> assessmentNodes = new HashSet<>();
        for (LearningQuestionNode mapping : learningQuestionNodeMapper.selectList(null)) {
            if (publishedIds.contains(mapping.getQuestionId())) {
                assessmentNodes.add(mapping.getNodeName());
            }
        }
        // 有编程实验的节点（tags JSON 精确匹配）
        Set<String> experimentNodes = new HashSet<>();
        for (Question q : questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getIsDelete, 0)
                .select(Question::getTags))) {
            for (String tag : parseTags(q.getTags())) {
                if (allNodeNames.contains(tag)) {
                    experimentNodes.add(tag);
                }
            }
        }
        List<ChapterCoverageVO> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : nodesByChapter.entrySet()) {
            ChapterCoverageVO vo = new ChapterCoverageVO();
            vo.setChapter(entry.getKey());
            vo.setNodeCount(entry.getValue().size());
            List<String> uncovered = new ArrayList<>();
            int content = 0;
            int assessment = 0;
            int experiment = 0;
            for (String nodeName : entry.getValue()) {
                boolean hasContent = contentNodes.contains(nodeName);
                boolean hasAssessment = assessmentNodes.contains(nodeName);
                boolean hasExperiment = experimentNodes.contains(nodeName);
                if (hasContent) {
                    content++;
                }
                if (hasAssessment) {
                    assessment++;
                }
                if (hasExperiment) {
                    experiment++;
                }
                if (!hasContent && !hasAssessment && !hasExperiment) {
                    uncovered.add(nodeName);
                }
            }
            vo.setPublishedContentCount(content);
            vo.setAssessmentCoveredCount(assessment);
            vo.setExperimentCoveredCount(experiment);
            vo.setUncoveredCount(uncovered.size());
            vo.setUncoveredNodes(uncovered.size() > MAX_UNCOVERED_NODES
                    ? uncovered.subList(0, MAX_UNCOVERED_NODES) : uncovered);
            result.add(vo);
        }
        result.sort(Comparator.comparing(ChapterCoverageVO::getNodeCount).reversed());
        return result;
    }

    // region 统计

    /**
     * 班级学生 id：students 表中该班级全部有效绑定 userId
     */
    private List<Long> studentIdsOfClass(String className) {
        List<Students> rows = studentsMapper.selectList(new LambdaQueryWrapper<Students>()
                .eq(Students::getClassName, className)
                .eq(Students::getIsDelete, 0)
                .isNotNull(Students::getUserId));
        List<Long> ids = new ArrayList<>();
        for (Students row : rows) {
            ids.add(row.getUserId().longValue());
        }
        return ids;
    }

    private NodeDrillQuestionVO buildQuestionStat(LearningQuestion question, List<LearningAttempt> attempts) {
        NodeDrillQuestionVO qVo = new NodeDrillQuestionVO();
        qVo.setQuestionId(question.getId());
        qVo.setTitle(question.getTitle());
        qVo.setType(question.getType());
        qVo.setReviewStatus(question.getReviewStatus());
        qVo.setAttemptCount(attempts.size());
        int first = 0;
        int firstCorrect = 0;
        for (LearningAttempt a : attempts) {
            if (a.getIsFirst() == 1) {
                first++;
                if (a.getIsCorrect() == 1) {
                    firstCorrect++;
                }
            }
        }
        qVo.setFirstAttemptCount(first);
        qVo.setFirstCorrectCount(firstCorrect);
        qVo.setFirstCorrectRate(first == 0 ? null
                : Math.round((double) firstCorrect / first * 10000.0) / 10000.0);
        return qVo;
    }

    private List<MisconceptionStatVO> topMisconceptions(Map<String, Integer> misconceptionWrong) {
        List<MisconceptionStatVO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : misconceptionWrong.entrySet()) {
            MisconceptionStatVO vo = new MisconceptionStatVO();
            vo.setMisconception(entry.getKey());
            vo.setWrongCount(entry.getValue());
            result.add(vo);
        }
        result.sort((a, b) -> {
            int byCount = Integer.compare(b.getWrongCount(), a.getWrongCount());
            return byCount != 0 ? byCount : a.getMisconception().compareTo(b.getMisconception());
        });
        return result.size() > 5 ? result.subList(0, 5) : result;
    }

    /**
     * 实验统计：按实验题聚合班级有效提交（提交 id 去重、排除系统故障）
     */
    private List<NodeDrillExperimentVO> experimentStats(List<Question> experiments, Map<Long, String> submitResult) {
        List<NodeDrillExperimentVO> result = new ArrayList<>();
        if (experiments.isEmpty()) {
            return result;
        }
        // 提交 id → 实验题 id
        Map<Long, Long> submitQuestion = new HashMap<>();
        if (!submitResult.isEmpty()) {
            for (QuestionSubmit submit : questionSubmitMapper.selectList(
                    new LambdaQueryWrapper<QuestionSubmit>()
                            .in(QuestionSubmit::getId, submitResult.keySet())
                            .eq(QuestionSubmit::getIsDelete, 0))) {
                submitQuestion.put(submit.getId(), submit.getQuestionId());
            }
        }
        for (Question q : experiments) {
            NodeDrillExperimentVO vo = new NodeDrillExperimentVO();
            vo.setQuestionId(q.getId());
            vo.setTitle(q.getTitle());
            int valid = 0;
            int pass = 0;
            for (Map.Entry<Long, String> entry : submitResult.entrySet()) {
                String resultStr = entry.getValue();
                boolean validResult = LearningResultEnum.PASS.name().equals(resultStr)
                        || LearningResultEnum.FAIL.name().equals(resultStr);
                if (validResult && q.getId().equals(submitQuestion.get(entry.getKey()))) {
                    valid++;
                    if (LearningResultEnum.PASS.name().equals(resultStr)) {
                        pass++;
                    }
                }
            }
            vo.setSubmitCount(valid);
            vo.setPassCount(pass);
            vo.setPassRate(valid == 0 ? null : Math.round((double) pass / valid * 10000.0) / 10000.0);
            result.add(vo);
        }
        result.sort(Comparator.comparing(NodeDrillExperimentVO::getQuestionId));
        return result;
    }

    /**
     * 实验有效通过率（U14 修正）：JUDGE 事件按 questionSubmitId 去重（同一提交映射多节点只算一次），
     * 排除 SYSTEM_ERROR；无有效样本返回 null（不显示 0% 宣称全班不会）。
     */
    static Double computeExperimentPassRate(Map<Long, String> submitResult) {
        int pass = 0;
        int valid = 0;
        for (String result : submitResult.values()) {
            if (LearningResultEnum.PASS.name().equals(result)) {
                pass++;
                valid++;
            } else if (LearningResultEnum.FAIL.name().equals(result)) {
                valid++;
            }
        }
        return valid == 0 ? null : Math.round((double) pass / valid * 10000.0) / 10000.0;
    }

    private List<NodeHeatVO> nodeHeat(List<Long> studentIds) {
        List<LearningMastery> masteries = learningMasteryMapper.selectList(
                new LambdaQueryWrapper<LearningMastery>()
                        .in(LearningMastery::getUserId, studentIds));
        Map<String, List<LearningMastery>> byNode = new HashMap<>();
        for (LearningMastery m : masteries) {
            byNode.computeIfAbsent(m.getNodeName(), k -> new ArrayList<>()).add(m);
        }
        List<NodeHeatVO> result = new ArrayList<>();
        for (Map.Entry<String, List<LearningMastery>> entry : byNode.entrySet()) {
            NodeHeatVO heat = new NodeHeatVO();
            heat.setNodeName(entry.getKey());
            List<LearningMastery> rows = entry.getValue();
            heat.setStudentCount(rows.size());
            double sum = 0;
            int weak = 0;
            for (LearningMastery m : rows) {
                sum += m.getPKnown();
                if (MasteryStatusEnum.RED.name().equals(m.getStatus())) {
                    weak++;
                }
            }
            heat.setAvgPKnown(Math.round(sum / rows.size() * 10000.0) / 10000.0);
            heat.setWeakCount(weak);
            result.add(heat);
        }
        result.sort(Comparator.comparingDouble(NodeHeatVO::getAvgPKnown));
        return result;
    }

    private List<NodeHeatVO> weakTop(List<NodeHeatVO> nodeHeat) {
        List<NodeHeatVO> sorted = new ArrayList<>(nodeHeat);
        sorted.sort((a, b) -> {
            int byWeak = Integer.compare(b.getWeakCount(), a.getWeakCount());
            return byWeak != 0 ? byWeak : Double.compare(a.getAvgPKnown(), b.getAvgPKnown());
        });
        return sorted.size() > 5 ? sorted.subList(0, 5) : sorted;
    }

    private List<QuestionAccuracyVO> questionAccuracy(List<Long> studentIds) {
        List<LearningAttempt> attempts = learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>().in(LearningAttempt::getUserId, studentIds));
        Map<Long, List<LearningAttempt>> byQuestion = new HashMap<>();
        for (LearningAttempt a : attempts) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }
        List<QuestionAccuracyVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<LearningAttempt>> entry : byQuestion.entrySet()) {
            LearningQuestion question = learningQuestionMapper.selectById(entry.getKey());
            if (question == null || question.getIsDelete() == 1) {
                continue;
            }
            QuestionAccuracyVO vo = new QuestionAccuracyVO();
            vo.setQuestionId(entry.getKey());
            vo.setTitle(question.getTitle());
            vo.setType(question.getType());
            List<LearningAttempt> list = entry.getValue();
            vo.setAttemptCount(list.size());
            int firstCorrect = 0;
            int correct = 0;
            for (LearningAttempt a : list) {
                if (a.getIsCorrect() == 1) {
                    correct++;
                    if (a.getIsFirst() == 1) {
                        firstCorrect++;
                    }
                }
            }
            vo.setFirstCorrectCount(firstCorrect);
            vo.setCorrectRate(list.isEmpty() ? 0.0
                    : Math.round((double) correct / list.size() * 10000.0) / 10000.0);
            result.add(vo);
        }
        result.sort(Comparator.comparingDouble(QuestionAccuracyVO::getCorrectRate));
        return result;
    }

    /**
     * 班级实验通过率：JUDGE 事件按提交去重、排除系统故障（U14）
     */
    private Double experimentPassRate(List<Long> studentIds) {
        List<LearningEvent> judgeEvents = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .in(LearningEvent::getUserId, studentIds)
                        .eq(LearningEvent::getEventType, LearningEventTypeEnum.JUDGE.name()));
        Map<Long, String> submitResult = new LinkedHashMap<>();
        for (LearningEvent e : judgeEvents) {
            submitResult.put(e.getResourceId(), e.getResult());
        }
        return computeExperimentPassRate(submitResult);
    }

    private List<LearningEventVO> recentEvents(List<Long> studentIds) {
        List<LearningEvent> events = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .in(LearningEvent::getUserId, studentIds)
                        .orderByDesc(LearningEvent::getCreateTime)
                        .last("limit 20"));
        Map<Long, String> anonymousNames = anonymousNames(new HashSet<>(studentIds));
        List<LearningEventVO> vos = new ArrayList<>();
        for (LearningEvent e : events) {
            vos.add(LearningMasteryServiceImpl.toEventVO(e, anonymousNames.get(e.getUserId())));
        }
        return vos;
    }

    // endregion

    // region 工具

    /**
     * 匿名化展示名：学生-{userId 后 4 位}
     */
    private Map<Long, String> anonymousNames(Set<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        for (Long id : userIds) {
            String suffix = String.valueOf(id);
            suffix = suffix.length() <= 4 ? suffix : suffix.substring(suffix.length() - 4);
            map.put(id, "学生-" + suffix);
        }
        return map;
    }

    /**
     * 解析 question.tags JSON 数组为集合；非法 JSON 返回空集合
     */
    private Set<String> parseTags(String tagsJson) {
        Set<String> tags = new HashSet<>();
        if (StringUtils.isBlank(tagsJson)) {
            return tags;
        }
        try {
            List<String> list = JSONUtil.toList(tagsJson, String.class);
            if (list != null) {
                tags.addAll(list);
            }
        } catch (Exception ignored) {
            // 非法 JSON 按无标签处理
        }
        return tags;
    }

    // endregion
}
