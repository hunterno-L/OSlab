package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.learning.SimulationEntryVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点学习资源解析器（第三阶段 §6.3：工作台、课程目录、路径与导师共用的唯一解析入口）
 *
 * <p>实验关联唯一来源 experiment_node；question.tags 仅用于展示筛选。
 * 模拟入口：按稳定 nodeCode 映射到 18 项算法键；无匹配节点不出现无关模拟按钮。
 */
@Component
public class LearningResourceResolver {

    /** 页面置换类节点兜底（稳定代码缺省时）：FIFO/LRU/OPT，OPT 仅对比 */
    private static final String PAGE_SIM_KEYWORD = "置换";
    /** CPU 调度类节点兜底（稳定代码缺省时）：FCFS/SJF/RR */
    private static final String CPU_SIM_KEYWORD = "调度";

    /** 稳定 nodeCode → 算法键；算法键与前端 simulation/catalog.ts 一致。 */
    private static final Map<String, List<String>> NODE_SIMULATION_KEYS = new HashMap<>();

    static {
        NODE_SIMULATION_KEYS.put("os.cpu.fcfs", Collections.singletonList("cpu.fcfs"));
        NODE_SIMULATION_KEYS.put("os.cpu.sjf", Collections.singletonList("cpu.sjf"));
        NODE_SIMULATION_KEYS.put("os.cpu.hrrn", Collections.singletonList("cpu.hrrn"));
        NODE_SIMULATION_KEYS.put("os.cpu.priority_nonpreemptive", Collections.singletonList("cpu.priority_nonpreemptive"));
        NODE_SIMULATION_KEYS.put("os.cpu.priority_preemptive", Collections.singletonList("cpu.priority_preemptive"));
        NODE_SIMULATION_KEYS.put("os.cpu.rr", Collections.singletonList("cpu.rr"));
        NODE_SIMULATION_KEYS.put("os.cpu.mlfq", Collections.singletonList("cpu.mlfq"));
        NODE_SIMULATION_KEYS.put("os.memory.first_fit", Collections.singletonList("allocation.first_fit"));
        NODE_SIMULATION_KEYS.put("os.memory.best_fit", Collections.singletonList("allocation.best_fit"));
        NODE_SIMULATION_KEYS.put("os.memory.worst_fit", Collections.singletonList("allocation.worst_fit"));
        NODE_SIMULATION_KEYS.put("os.memory.next_fit", Collections.singletonList("allocation.next_fit"));
        NODE_SIMULATION_KEYS.put("os.memory.fifo", Collections.singletonList("page.fifo"));
        NODE_SIMULATION_KEYS.put("os.memory.lru", Collections.singletonList("page.lru"));
        NODE_SIMULATION_KEYS.put("os.memory.opt", Collections.singletonList("page.opt"));
        NODE_SIMULATION_KEYS.put("os.disk.fcfs", Collections.singletonList("disk.fcfs"));
        NODE_SIMULATION_KEYS.put("os.disk.sstf", Collections.singletonList("disk.sstf"));
        NODE_SIMULATION_KEYS.put("os.disk.scan", Collections.singletonList("disk.scan"));
        NODE_SIMULATION_KEYS.put("os.disk.cscan", Collections.singletonList("disk.cscan"));
    }

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;
    @Resource
    private ExperimentMappingService experimentMappings;
    @Resource
    private NodeIdentityService nodeIdentities;

    /** Resource lists and judge attribution share the same formal mapping. */
    public List<Question> findExperimentsByNode(String nodeName) {
        return StringUtils.isBlank(nodeName) || experimentMappings == null
                ? Collections.emptyList() : experimentMappings.experiments(nodeName);
    }

    /**
     * 模拟器入口：优先按稳定 nodeCode 映射；无绑定时保留旧名称关键词兜底。
     * 返回的 algorithms 为 18 项算法键（与前端 simulation/catalog.ts 一致）。
     */
    public SimulationEntryVO simulationEntryOf(String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return null;
        }
        List<String> algorithms = null;
        try {
            NodeIdentityService.Identity identity = nodeIdentities == null ? null : nodeIdentities.resolve(nodeName);
            if (identity != null) algorithms = NODE_SIMULATION_KEYS.get(identity.nodeCode);
        } catch (RuntimeException ignored) {
            algorithms = null;
        }
        if (algorithms == null) {
            if (nodeName.contains(PAGE_SIM_KEYWORD)) {
                algorithms = Arrays.asList("page.fifo", "page.lru", "page.opt");
            } else if (nodeName.contains(CPU_SIM_KEYWORD)) {
                algorithms = Arrays.asList("cpu.fcfs", "cpu.sjf", "cpu.rr");
            }
        }
        if (algorithms == null) return null;
        SimulationEntryVO entry = new SimulationEntryVO();
        entry.setNodeName(nodeName);
        entry.setAlgorithms(algorithms);
        return entry;
    }

    /**
     * 节点是否有关联的已发布测评题（learning_question_node 映射表精确匹配）
     */
    public boolean hasPublishedAssessments(String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return false;
        }
        List<LearningQuestionNode> mappings = learningQuestionNodeMapper.selectList(
                new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getNodeName, nodeName));
        if (CollectionUtils.isEmpty(mappings)) {
            return false;
        }
        List<Long> questionIds = new ArrayList<>();
        for (LearningQuestionNode m : mappings) {
            questionIds.add(m.getQuestionId());
        }
        Long published = learningQuestionService.count(new LambdaQueryWrapper<LearningQuestion>()
                .in(LearningQuestion::getId, questionIds)
                .eq(LearningQuestion::getReviewStatus, LearningReviewStatusEnum.PUBLISHED.name())
                .eq(LearningQuestion::getIsDelete, 0));
        return published != null && published > 0;
    }
}
