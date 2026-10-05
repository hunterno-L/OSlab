package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.learning.SimulationEntryVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.QuestionService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 节点学习资源解析器（第三阶段 §6.3：工作台、课程目录、路径与导师共用的唯一解析入口）
 *
 * <p>实验关联：question.tags 为 JSON 数组，解析后精确匹配节点名（替代旧 LIKE 模糊匹配，U13）。
 * 模拟入口：按已配置的节点-算法映射返回；无匹配节点不出现无关模拟按钮。
 */
@Component
public class LearningResourceResolver {

    /** 页面置换类节点：名称含「置换」进入置换模拟（FIFO/LRU/OPT，OPT 仅对比） */
    private static final String PAGE_SIM_KEYWORD = "置换";
    /** CPU 调度类节点：名称含「调度」进入调度模拟（FCFS/SJF/RR） */
    private static final String CPU_SIM_KEYWORD = "调度";

    @Resource
    private QuestionService questionService;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    /**
     * 节点关联的编程实验：tags JSON 精确匹配（含该节点名的数组元素）。
     * LIKE '"节点名"' 仅作粗筛（JSON 数组元素必然带引号），再解析校验，避免误命中子串。
     */
    public List<Question> findExperimentsByNode(String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return Collections.emptyList();
        }
        List<Question> candidates = questionService.list(new LambdaQueryWrapper<Question>()
                .like(Question::getTags, "\"" + nodeName + "\"")
                .eq(Question::getIsDelete, 0)
                .orderByDesc(Question::getCreateTime)
                .last("limit 20"));
        List<Question> result = new ArrayList<>();
        for (Question q : candidates) {
            if (tagMatches(q.getTags(), nodeName)) {
                result.add(q);
            }
        }
        return result;
    }

    /** tags JSON 数组中是否存在与 nodeName 完全相等的元素 */
    private boolean tagMatches(String tagsJson, String nodeName) {
        if (StringUtils.isBlank(tagsJson)) {
            return false;
        }
        try {
            List<String> tags = JSONUtil.toList(tagsJson, String.class);
            return tags != null && tags.contains(nodeName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 模拟器入口：按节点名称映射返回页面置换或 CPU 调度；无匹配返回 null（前端不显示入口）
     */
    public SimulationEntryVO simulationEntryOf(String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return null;
        }
        SimulationEntryVO entry = null;
        if (nodeName.contains(PAGE_SIM_KEYWORD)) {
            entry = new SimulationEntryVO();
            entry.setNodeName(nodeName);
            entry.setAlgorithms(java.util.Arrays.asList("FIFO", "LRU", "OPT"));
        } else if (nodeName.contains(CPU_SIM_KEYWORD)) {
            entry = new SimulationEntryVO();
            entry.setNodeName(nodeName);
            entry.setAlgorithms(java.util.Arrays.asList("FCFS", "SJF", "RR"));
        }
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
