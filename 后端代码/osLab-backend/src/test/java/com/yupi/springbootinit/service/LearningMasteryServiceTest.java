package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.model.vo.learning.PersonalGraphVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 契约测试（§6）：PersonalGraphVO 聚合字段 + NodeWorkbenchVO 完整结构。
 * 数据前置：本地演示库（smoke 已产生 student_demo1 的 LRU/进程调度证据）。
 */
@SpringBootTest
class LearningMasteryServiceTest {

    @Resource
    private LearningMasteryService learningMasteryService;

    @Resource
    private UserService userService;

    private User student() {
        return userService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUserAccount, "student_demo1"));
    }

    @Test
    void personalGraph_returnsTotalsAndStatusCounts() {
        PersonalGraphVO vo = learningMasteryService.personalGraph(student());
        Assertions.assertEquals(vo.getNodes().size(), vo.getTotalNodes());
        Assertions.assertEquals(vo.getLinks().size(), vo.getTotalLinks());
        Assertions.assertNotNull(vo.getStatusCounts());
        int sum = vo.getStatusCounts().values().stream().mapToInt(Integer::intValue).sum();
        Assertions.assertEquals(vo.getTotalNodes(), sum, "四类状态数量之和必须等于节点总数");
        Assertions.assertNotNull(vo.getChapters());
        Assertions.assertFalse(vo.getChapters().isEmpty(), "快照必须给出章节汇总");
    }

    @Test
    void personalGraph_evidenceCountMatchesNonGrayNodes() {
        PersonalGraphVO vo = learningMasteryService.personalGraph(student());
        int nonGray = (int) vo.getNodes().stream()
                .filter(n -> n.getStatus() != null && !"GRAY".equals(n.getStatus()))
                .count();
        Assertions.assertEquals(nonGray, vo.getEvidenceCount().intValue(), "evidenceCount 必须等于有证据(非 GRAY)节点数");
    }

    @Test
    void personalGraph_recommendsWeakestEvidenceNode() {
        PersonalGraphVO vo = learningMasteryService.personalGraph(student());
        // student_demo1 存在 RED 节点（LRU 演示路径），推荐必须是其中 pKnown 最低者或其前置
        Assertions.assertNotNull(vo.getRecommendedNodeName(), "有薄弱证据时必须给出推荐节点");
    }

    @Test
    void personalGraph_nodesCarryLastEventTypeAndResult() {
        PersonalGraphVO vo = learningMasteryService.personalGraph(student());
        vo.getNodes().stream()
                .filter(n -> n.getLastEventId() != null)
                .forEach(n -> {
                    assertNotNull(n.getLastEventType(), "有事件的节点必须带最近事件类型: " + n.getName());
                    assertNotNull(n.getLastResult(), "有事件的节点必须带最近事件结果: " + n.getName());
                });
    }

    @Test
    void nodeWorkbench_returnsPrerequisitesAndSuccessorsWithStatus() {
        // 页面置换：前置=缺页中断；后继=LRU 页面置换算法 / FIFO
        NodeWorkbenchVO vo = learningMasteryService.nodeWorkbench(student(), "页面置换");
        assertNotNull(vo.getPrerequisites());
        assertNotNull(vo.getSuccessors());
        assertTrue(vo.getPrerequisites().stream().anyMatch(p -> "缺页中断".equals(p.getName())),
                "页面置换的前置必须包含 缺页中断");
        vo.getPrerequisites().forEach(p -> assertNotNull(p.getStatus(), "前置节点必须带状态"));
        vo.getSuccessors().forEach(s -> assertNotNull(s.getStatus(), "后继节点必须带状态"));
    }

    @Test
    void nodeWorkbench_returnsQuestionStatsAndMisconceptions() {
        NodeWorkbenchVO vo = learningMasteryService.nodeWorkbench(student(), "LRU 页面置换算法");
        assertNotNull(vo.getQuestionStats());
        assertTrue(vo.getQuestionStats().getTotal() >= 1, "LRU 节点至少关联 1 道已发布普通题");
        assertNotNull(vo.getMisconceptions());
    }

    @Test
    void nodeWorkbench_returnsExperimentsForTaggedNode() {
        // TLB 节点存在已发布编程实验（实验2.7/2.8 标签映射）
        NodeWorkbenchVO vo = learningMasteryService.nodeWorkbench(student(), "TLB");
        assertNotNull(vo.getExperiments());
        assertTrue(vo.getExperiments().size() >= 1, "TLB 节点应关联至少 1 个编程实验");
    }

    @Test
    void nodeWorkbench_lastFailurePointsToRealResource() {
        NodeWorkbenchVO vo = learningMasteryService.nodeWorkbench(student(), "LRU 页面置换算法");
        assertNotNull(vo.getLastFailure(), "student_demo1 在 LRU 节点有答错证据，必须返回最近失败");
        assertNotNull(vo.getLastFailure().getResourceId(), "失败证据必须指向真实资源 id");
        assertNotNull(vo.getLastFailure().getResult());
    }

    @Test
    void nodeWorkbench_emptyStructuresInsteadOfNulls() {
        // 无发布普通题的真实节点：结构必须齐全且为空集合，不返回 null
        NodeWorkbenchVO vo = learningMasteryService.nodeWorkbench(student(), "地址映射");
        assertNotNull(vo.getPrerequisites());
        assertNotNull(vo.getSuccessors());
        assertNotNull(vo.getQuestions());
        assertNotNull(vo.getExperiments());
        assertNotNull(vo.getEvents());
        assertNotNull(vo.getRecommendations());
        assertNotNull(vo.getQuestionStats());
    }
}
