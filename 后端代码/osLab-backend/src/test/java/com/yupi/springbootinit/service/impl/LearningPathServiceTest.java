package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.model.vo.learning.PathItemVO;
import com.yupi.springbootinit.service.LearningPathService;
import com.yupi.springbootinit.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 补救学习路径测试（第三阶段 §6.3 / §14.1 LearningPathServiceTest）
 * 覆盖：多前置、方向（前置在前）、依赖环、无资源节点回退 NODE、真实 action。
 */
@SpringBootTest
class LearningPathServiceTest {

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private UserService userService;

    private User student() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo1"));
    }

    // ---- 合成图：环检测与拓扑排序（纯算法，不依赖外部数据） ----

    private Map<String, LinkedHashSet<String>> graph(String... edges) {
        // edges: "A>B" 表示 A 依赖 B（B 为 A 的前置）
        Map<String, LinkedHashSet<String>> dependsOn = new HashMap<>();
        for (String e : edges) {
            String[] parts = e.split(">");
            dependsOn.computeIfAbsent(parts[0], k -> new LinkedHashSet<>()).add(parts[1]);
        }
        return dependsOn;
    }

    @Test
    void cycleDetection_reportsCycleNodes() {
        // A -> B -> C -> A 成环；D -> A 被环阻塞；E -> D 独立链
        Map<String, LinkedHashSet<String>> dependsOn =
                graph("A>B", "B>C", "C>A", "D>A", "E>D");
        Set<String> scope = new HashSet<>(Arrays.asList("A", "B", "C", "D", "E"));
        List<String> cycles = LearningPathServiceImpl.findCycleNodes(dependsOn, scope);
        Assertions.assertEquals(Arrays.asList("A", "B", "C"),
                cycles.stream().sorted().collect(Collectors.toList()), "应检出 A/B/C 环");
    }

    @Test
    void topologicalOrder_prerequisitesFirstAndCycleExcluded() {
        // 目标 T 依赖 A、B；A 依赖 C；B 与 C 无前置
        Map<String, LinkedHashSet<String>> dependsOn = graph("T>A", "T>B", "A>C");
        Set<String> scope = new HashSet<>(Arrays.asList("T", "A", "B", "C"));
        List<String> ordered = LearningPathServiceImpl.topologicalOrder(
                dependsOn, scope, new HashMap<>(), java.util.Collections.emptyList());
        Assertions.assertEquals(4, ordered.size(), "无环时全部节点可排序");
        Assertions.assertEquals("T", ordered.get(3), "目标节点必须最后");
        Assertions.assertTrue(ordered.indexOf("C") < ordered.indexOf("A"), "C 必须在 A 前");
        // 环场景：环节点不进入顺序
        Map<String, LinkedHashSet<String>> cyclic =
                graph("T>A", "A>B", "B>A");
        Set<String> cyclicScope = new HashSet<>(Arrays.asList("T", "A", "B"));
        List<String> cycleNodes = LearningPathServiceImpl.findCycleNodes(cyclic, cyclicScope);
        List<String> partial = LearningPathServiceImpl.topologicalOrder(
                cyclic, cyclicScope, new HashMap<>(), cycleNodes);
        Assertions.assertFalse(partial.contains("A") || partial.contains("B"), "环上节点不排序");
    }

    // ---- 真实图谱集成 ----

    @Test
    void pathForRealTarget_ordersPrerequisitesBeforeTarget() {
        LearningPathVO vo = learningPathService.buildPath(student(), "LRU 页面置换算法");
        Assertions.assertFalse(vo.getItems().isEmpty(), "路径不能为空");
        Assertions.assertTrue(vo.getCycleNodes().isEmpty(), "种子图谱不应有依赖环");
        List<PathItemVO> items = vo.getItems();
        // order 从 1 连续递增；目标在最后
        for (int i = 0; i < items.size(); i++) {
            Assertions.assertEquals(i + 1, items.get(i).getOrder(), "order 应从 1 连续");
        }
        Assertions.assertEquals("LRU 页面置换算法",
                items.get(items.size() - 1).getNodeName(), "目标节点必须在最后");
        // 方向：任一项的直接前置（路径内）必须排在该项之前
        Map<String, Integer> orderIndex = items.stream()
                .collect(Collectors.toMap(PathItemVO::getNodeName, PathItemVO::getOrder));
        for (PathItemVO item : items) {
            for (String pre : item.getPrerequisites()) {
                Assertions.assertTrue(orderIndex.get(pre) < item.getOrder(),
                        "前置 " + pre + " 必须排在 " + item.getNodeName() + " 前");
            }
        }
        // 每项都有合法行动；EXPERIMENT 必须带真实 resourceId
        for (PathItemVO item : items) {
            Assertions.assertNotNull(item.getAction(), "行动不能为空");
            String kind = item.getAction().getKind();
            Assertions.assertTrue(Arrays.asList("NODE", "ASSESSMENT", "EXPERIMENT", "SIMULATION")
                    .contains(kind), "非法行动类型 " + kind);
            if ("EXPERIMENT".equals(kind)) {
                Assertions.assertNotNull(item.getAction().getResourceId(), "实验行动必须带资源 id");
            }
        }
    }

    @Test
    void nonexistentTarget_throwsNotFound() {
        Assertions.assertThrows(BusinessException.class,
                () -> learningPathService.buildPath(student(), "不存在的节点__测试"));
    }
}
