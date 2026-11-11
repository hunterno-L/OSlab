package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.RelationType;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.learning.LearningResourceResolver;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.LinkDTO;
import com.yupi.springbootinit.model.dto.knowledgegraph.NodeDTO;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.MasteryStatusEnum;
import com.yupi.springbootinit.model.vo.learning.LearningActionVO;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.model.vo.learning.PathItemVO;
import com.yupi.springbootinit.model.vo.learning.SimulationEntryVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningPathService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 补救学习路径服务实现（第三阶段 §6.3）
 *
 * <p>依赖边语义：A -dependent-> B 表示 A 依赖 B，学习顺序必须 B 在 A 前。
 * 只将 dependent 边用于拓扑排序，related/contain/implements 不参与（由现有知识链验证）。
 */
@Service
public class LearningPathServiceImpl implements LearningPathService {

    /** Kahn 就绪队列优先级：未学习 > 薄弱 > 待巩固 > 已掌握（缺失/薄弱前置先安排） */
    private static final Map<String, Integer> STATUS_PRIORITY = new HashMap<>();

    static {
        STATUS_PRIORITY.put("GRAY", 0);
        STATUS_PRIORITY.put("RED", 1);
        STATUS_PRIORITY.put("YELLOW", 2);
        STATUS_PRIORITY.put("GREEN", 3);
    }

    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    @Resource
    private LearningMasteryMapper learningMasteryMapper;

    @Resource
    private LearningResourceResolver resourceResolver;
    @Resource
    private com.yupi.springbootinit.learning.NodeIdentityService nodeIdentities;
    @Resource
    private com.yupi.springbootinit.course.CourseService courseService;

    @Resource private com.yupi.springbootinit.learning.PublishedCourseScopeService courseScope;
    @Resource private com.yupi.springbootinit.course.EnabledCourses enabledCourses;

    @Override
    public LearningPathVO buildPath(User user, String targetNodeName) {
        return buildPath(user, targetNodeName, null);
    }

    @Override
    public LearningPathVO buildPath(User user, String targetNodeName, String courseCode) {
        if (StringUtils.isBlank(targetNodeName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标节点不能为空");
        }
        com.yupi.springbootinit.learning.NodeIdentityService.Identity identity=nodeIdentities==null?null:nodeIdentities.resolve(targetNodeName);
        if(identity!=null)targetNodeName=identity.storageKey;
        GraphDataDTO graph = knowledgeGraphService.getGraphSnapshot();
        Map<String, com.yupi.springbootinit.learning.NodeIdentityService.Identity> scopeIdentities = null;
        if (StringUtils.isNotBlank(courseCode)) {
            enabledCourses.require(courseCode);
            scopeIdentities = courseScope.nodes(courseCode);
            graph = courseScope.filterGraph(graph, scopeIdentities);
        }
        NodeDTO selected=KnowledgeGraphService.resolveNode(graph,identity==null?null:identity.nodeCode,targetNodeName);
        if(selected==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"知识节点不存在："+targetNodeName);
        Map<String, NodeDTO> nodeByName = new HashMap<>();
        Set<String> ambiguous=new HashSet<>();
        Map<String, String> idToName = new HashMap<>();
        for (NodeDTO node : graph.getNodes() == null ? new ArrayList<NodeDTO>() : graph.getNodes()) {
            String key = scopeIdentities != null && scopeIdentities.containsKey(node.getNodeCode())
                    ? scopeIdentities.get(node.getNodeCode()).storageKey : storageKey(node);
            if(nodeByName.putIfAbsent(key,node)!=null)ambiguous.add(key);
            if (node.getId() != null) {
                idToName.put(node.getId(), key);
            }
        }
        if (!nodeByName.containsKey(targetNodeName)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识节点不存在：" + targetNodeName);
        }

        // 1. 多前置邻接表：dependsOn[A] = A 的直接前置集合（保序去重）
        Map<String, LinkedHashSet<String>> dependsOn = new HashMap<>();
        for (LinkDTO link : graph.getLinks() == null ? new ArrayList<LinkDTO>() : graph.getLinks()) {
            if (!KnowledgeGraphService.isPrerequisiteRelation(link.getRelationType())) {
                continue;
            }
            String source = idToName.get(link.getSource());
            String target = idToName.get(link.getTarget());
            if (source == null || target == null || source.equals(target)) {
                continue;
            }
            dependsOn.computeIfAbsent(source, k -> new LinkedHashSet<>()).add(target);
        }

        // 2. 前置闭包（BFS，visited 防环死循环）
        Set<String> closure = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(targetNodeName);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String pre : dependsOn.getOrDefault(current, new LinkedHashSet<>())) {
                if (closure.add(pre)) {
                    queue.add(pre);
                }
            }
        }
        Set<String> scope = new LinkedHashSet<>(closure);
        scope.add(targetNodeName);
        for(String key:scope)if(ambiguous.contains(key))throw new BusinessException(ErrorCode.PARAMS_ERROR,"图谱节点身份冲突，请先确认稳定代码："+key);

        // 3. 依赖环检测（Tarjan SCC，scope 内）
        List<String> cycleNodes = findCycleNodes(dependsOn, scope);

        // 4. 本人掌握状态
        Map<String, LearningMastery> masteryMap = masteryMapOf(user.getId());

        // 5. 拓扑排序（Kahn + 优先队列：环上节点与其被阻塞节点排除在顺序外，仍作为路径项返回）
        List<String> ordered = topologicalOrder(dependsOn, scope, masteryMap, cycleNodes);

        // 6. 组装路径项
        LearningPathVO vo = new LearningPathVO();
        vo.setTargetNodeName(targetNodeName);
        vo.setCycleNodes(cycleNodes);
        Map<String, Integer> orderIndex = new HashMap<>();
        List<PathItemVO> items = new ArrayList<>();
        int order = 1;
        for (String name : ordered) {
            PathItemVO item = buildItem(name, targetNodeName, dependsOn, scope,
                    masteryMap.get(name), nodeByName.get(name));
            item.setOrder(order);
            attachUnit(item,user.getId());
            orderIndex.put(name, order);
            order++;
            items.add(item);
        }
        // 环上节点（无法安全排序）追加在末尾，标记为待处理环
        for (String name : scope) {
            if (!orderIndex.containsKey(name)) {
                PathItemVO item = buildItem(name, targetNodeName, dependsOn, scope,
                        masteryMap.get(name), nodeByName.get(name));
                item.setOrder(order);
                attachUnit(item,user.getId());
                item.setReason("该节点位于依赖环中，请管理员检查知识图谱依赖关系（" + item.getReason() + "）");
                order++;
                items.add(item);
            }
        }
        vo.setItems(items);

        // 7. 依据事件：路径中薄弱/待巩固节点的最近事件（可追溯）
        List<Long> basisEventIds = new ArrayList<>();
        for (String name : scope) {
            LearningMastery m = masteryMap.get(name);
            if (m != null && m.getLastEventId() != null
                    && (MasteryStatusEnum.RED.name().equals(m.getStatus())
                    || MasteryStatusEnum.YELLOW.name().equals(m.getStatus()))
                    && basisEventIds.size() < 10) {
                basisEventIds.add(m.getLastEventId());
            }
        }
        vo.setBasisEventIds(basisEventIds);
        return vo;
    }

    // region 私有工具

    private Map<String, LearningMastery> masteryMapOf(Long userId) {
        Map<String, LearningMastery> map = new HashMap<>();
        for (LearningMastery row : learningMasteryMapper.selectList(
                new LambdaQueryWrapper<LearningMastery>().eq(LearningMastery::getUserId, userId))) {
            map.put(row.getNodeName(), row);
        }
        return map;
    }

    static String statusOf(LearningMastery mastery) {
        if (mastery == null) {
            return MasteryStatusEnum.GRAY.name();
        }
        int evidence = nz(mastery.getConceptEvidenceCount()) + nz(mastery.getPracticeEvidenceCount());
        return evidence == 0 ? MasteryStatusEnum.GRAY.name() : mastery.getStatus();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Tarjan 强连通分量：返回位于环上的节点（SCC 大小 > 1 或自环）。
     * 包级可见：供环检测单测直接构造合成图验证（无需真实图谱）。
     */
    static List<String> findCycleNodes(Map<String, LinkedHashSet<String>> dependsOn, Set<String> scope) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, Boolean> onStack = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        List<String> cycleNodes = new ArrayList<>();
        int[] counter = {0};
        for (String start : scope) {
            if (index.containsKey(start)) {
                continue;
            }
            strongConnect(start, dependsOn, scope, index, low, onStack, stack, counter, cycleNodes);
        }
        cycleNodes.sort(Comparator.naturalOrder());
        return cycleNodes;
    }

    private static void strongConnect(String v, Map<String, LinkedHashSet<String>> dependsOn, Set<String> scope,
                               Map<String, Integer> index, Map<String, Integer> low,
                               Map<String, Boolean> onStack, Deque<String> stack,
                               int[] counter, List<String> cycleNodes) {
        // 显式栈 DFS，避免深图递归溢出
        Deque<Object[]> frames = new ArrayDeque<>();
        frames.push(new Object[]{v, dependsOn.getOrDefault(v, new LinkedHashSet<>()).iterator()});
        index.put(v, counter[0]);
        low.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.put(v, true);
        while (!frames.isEmpty()) {
            Object[] frame = frames.peek();
            String node = (String) frame[0];
            java.util.Iterator<String> it = (java.util.Iterator<String>) frame[1];
            if (it.hasNext()) {
                String next = it.next();
                if (!scope.contains(next)) {
                    continue;
                }
                if (!index.containsKey(next)) {
                    index.put(next, counter[0]);
                    low.put(next, counter[0]);
                    counter[0]++;
                    stack.push(next);
                    onStack.put(next, true);
                    frames.push(new Object[]{next,
                            dependsOn.getOrDefault(next, new LinkedHashSet<>()).iterator()});
                } else if (Boolean.TRUE.equals(onStack.get(next))) {
                    low.put(node, Math.min(low.get(node), index.get(next)));
                }
            } else {
                frames.pop();
                if (!frames.isEmpty()) {
                    String parent = (String) frames.peek()[0];
                    low.put(parent, Math.min(low.get(parent), low.get(node)));
                }
                if (low.get(node).equals(index.get(node))) {
                    List<String> scc = new ArrayList<>();
                    String w;
                    do {
                        w = stack.pop();
                        onStack.put(w, false);
                        scc.add(w);
                    } while (!w.equals(node));
                    if (scc.size() > 1 || dependsOn.getOrDefault(node, new LinkedHashSet<>()).contains(node)) {
                        cycleNodes.addAll(scc);
                    }
                }
            }
        }
    }

    /**
     * Kahn 拓扑排序（就绪节点按 掌握状态优先级 + pKnown 升序 出队）。
     * 环上节点永远无法就绪，自然被排除；由调用方补回到路径末尾。
     * 包级可见：供单测直接构造合成图验证顺序与环行为。
     */
    static List<String> topologicalOrder(Map<String, LinkedHashSet<String>> dependsOn, Set<String> scope,
                                          Map<String, LearningMastery> masteryMap, List<String> cycleNodes) {
        Set<String> cycleSet = new HashSet<>(cycleNodes);
        Map<String, Integer> inDegree = new HashMap<>();
        for (String name : scope) {
            inDegree.put(name, 0);
        }
        // 诱导子图内计数：仅统计 scope 内的前置
        for (String name : scope) {
            for (String pre : dependsOn.getOrDefault(name, new LinkedHashSet<>())) {
                if (scope.contains(pre) && !pre.equals(name)) {
                    inDegree.merge(name, 1, Integer::sum);
                }
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>(
                Comparator.<String>comparingInt(
                        n -> STATUS_PRIORITY.getOrDefault(statusOf(masteryMap.get(n)), 3))
                        .thenComparingDouble(n -> {
                            LearningMastery m = masteryMap.get(n);
                            return m == null || m.getPKnown() == null ? 1.0 : m.getPKnown();
                        })
                        .thenComparing(Comparator.naturalOrder()));
        for (String name : scope) {
            if (inDegree.get(name) == 0) {
                ready.add(name);
            }
        }
        List<String> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            if (!done.add(current)) {
                continue;
            }
            ordered.add(current);
            // current 是其他 scope 节点的前置：完成后消除入度
            for (String name : scope) {
                if (done.contains(name)) {
                    continue;
                }
                if (dependsOn.getOrDefault(name, new LinkedHashSet<>()).contains(current)) {
                    int remain = inDegree.merge(name, -1, Integer::sum);
                    if (remain == 0 && !cycleSet.contains(name)) {
                        ready.add(name);
                    }
                }
            }
        }
        return ordered;
    }

    private PathItemVO buildItem(String name, String targetNodeName,
                                 Map<String, LinkedHashSet<String>> dependsOn, Set<String> scope,
                                 LearningMastery mastery, NodeDTO node) {
        PathItemVO item = new PathItemVO();
        item.setNodeName(name);
        if(node!=null){item.setNodeCode(node.getNodeCode());item.setDisplayName(node.getDisplayName());}
        String status = statusOf(mastery);
        item.setStatus(status);
        // 路径内直接前置
        List<String> prerequisites = new ArrayList<>();
        for (String pre : dependsOn.getOrDefault(name, new LinkedHashSet<>())) {
            if (scope.contains(pre) && !pre.equals(name)) {
                prerequisites.add(pre);
            }
        }
        item.setPrerequisites(prerequisites);
        // 原因
        if (name.equals(targetNodeName)) {
            item.setReason("目标节点（" + statusText(mastery) + "）");
        } else {
            switch (status) {
                case "GRAY":
                    item.setReason("前置知识，尚无学习记录");
                    break;
                case "RED":
                    item.setReason(String.format("前置薄弱（掌握度 %.0f%%）", pKnownOf(mastery) * 100));
                    break;
                case "YELLOW":
                    item.setReason(String.format("前置待巩固（掌握度 %.0f%%）", pKnownOf(mastery) * 100));
                    break;
                default:
                    item.setReason("已掌握，可跳过");
                    break;
            }
        }
        // 行动：未掌握先测评；已掌握优先实践（实验/模拟）
        item.setAction(actionOf(name, status, node));
        return item;
    }

    private LearningActionVO actionOf(String nodeName, String status, NodeDTO node) {
        LearningActionVO action = new LearningActionVO();
        action.setNodeName(nodeName);
        action.setNodeCode(node==null?null:node.getNodeCode());
        action.setChapter(node == null ? null : node.getChapter());
        boolean mastered = MasteryStatusEnum.GREEN.name().equals(status);
        SimulationEntryVO simulation = resourceResolver.simulationEntryOf(nodeName);
        List<Question> experiments = resourceResolver.findExperimentsByNode(nodeName);
        boolean hasAssessments = resourceResolver.hasPublishedAssessments(nodeName);
        if (!mastered && hasAssessments) {
            action.setKind("ASSESSMENT");
        } else if (simulation != null) {
            action.setKind("SIMULATION");
            action.setAlgorithms(simulation.getAlgorithms());
        } else if (CollectionUtils.isNotEmpty(experiments)) {
            action.setKind("EXPERIMENT");
            action.setResourceId(experiments.get(0).getId());
        } else if (hasAssessments) {
            action.setKind("ASSESSMENT");
        } else {
            action.setKind("NODE");
        }
        return action;
    }

    private double pKnownOf(LearningMastery mastery) {
        return mastery == null || mastery.getPKnown() == null ? 0.0 : mastery.getPKnown();
    }
    private void attachUnit(PathItemVO item,Long userId) {
        if(courseService==null||item.getNodeCode()==null||item.getAction()==null)return;
        List<Map<String,Object>> units=courseService.nodeUnits(item.getNodeCode(),userId);
        for(Map<String,Object> unit:units)if("TEACHES".equals(unit.get("role"))){
            item.getAction().setUnitCode(String.valueOf(unit.get("unitCode")));
            item.getAction().setCourseCode(String.valueOf(unit.get("courseCode")));return;
        }
    }
    private String storageKey(NodeDTO node){
        return nodeIdentities!=null&&node.getNodeCode()!=null?nodeIdentities.requireCode(node.getNodeCode()).storageKey:node.getName();
    }

    private String statusText(LearningMastery mastery) {
        String status = statusOf(mastery);
        for (MasteryStatusEnum e : MasteryStatusEnum.values()) {
            if (e.name().equals(status)) {
                return e.getText();
            }
        }
        return status;
    }

    // endregion
}
