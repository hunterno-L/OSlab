package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningNodeContentMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.ChapterCoverageVO;
import com.yupi.springbootinit.model.vo.learning.ClassOptionVO;
import com.yupi.springbootinit.model.vo.learning.ImportPreviewVO;
import com.yupi.springbootinit.model.vo.learning.ImportResultVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillQuestionVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillVO;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.service.LearningMasteryService;
import com.yupi.springbootinit.service.LearningNodeContentService;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.LearningTeacherService;
import com.yupi.springbootinit.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 8 §12 集成测试：真实班级列表、节点精确下钻、时间线班级约束、
 * 实验通过率提交去重、JSON 导入预览/提交、共享学习素材消费。
 */
@SpringBootTest
class LearningTeacherServiceTest {

    private static final String CLASS_OS2026 = "os2026";
    private static final String NODE_LRU = "LRU 页面置换算法";

    @Resource
    private LearningTeacherService learningTeacherService;

    @Resource
    private LearningQuestionService learningQuestionService;

    @Resource
    private LearningNodeContentService learningNodeContentService;

    @Resource
    private LearningMasteryService learningMasteryService;

    @Resource
    private UserService userService;

    @Resource
    private LearningQuestionNodeMapper learningQuestionNodeMapper;

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningNodeContentMapper learningNodeContentMapper;

    private User teacher() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "teacher_demo"));
    }

    private User student1() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo1"));
    }

    private User student2() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo2"));
    }

    @Test
    void classesReturnsRealClassesFromStudentsTable() {
        List<ClassOptionVO> classes = learningTeacherService.classes();
        Assertions.assertFalse(classes.isEmpty(), "students 表有班级，应返回非空列表");
        ClassOptionVO os2026 = classes.stream()
                .filter(c -> CLASS_OS2026.equals(c.getClassName())).findFirst().orElse(null);
        Assertions.assertNotNull(os2026, "os2026 应出现在真实班级列表");
        Assertions.assertTrue(os2026.getStudentCount() >= 2, "os2026 至少 2 名绑定学生");
    }


    @Test
    void nodeDetailMatchesMappingTableExactly() {
        NodeDrillVO drill = learningTeacherService.nodeDetail(CLASS_OS2026, NODE_LRU);
        Assertions.assertEquals(CLASS_OS2026, drill.getClassName());
        Assertions.assertTrue(drill.getStudentCount() >= 2, "全班人数来自 students 绑定");
        // 期望集合：映射表中该节点的未删除题目（与标题无关）
        Set<Long> expected = learningQuestionNodeMapper.selectList(
                        new LambdaQueryWrapper<LearningQuestionNode>()
                                .eq(LearningQuestionNode::getNodeName, NODE_LRU))
                .stream().map(LearningQuestionNode::getQuestionId).distinct()
                .filter(id -> {
                    LearningQuestion q = learningQuestionMapper.selectById(id);
                    return q != null && q.getIsDelete() == 0;
                }).collect(Collectors.toSet());
        Set<Long> actual = drill.getQuestions().stream()
                .map(NodeDrillQuestionVO::getQuestionId).collect(Collectors.toSet());
        Assertions.assertEquals(expected, actual, "下钻题目集合必须与映射表精确一致");
        // 验收：标题不含节点名的映射题仍出现在下钻
        Assertions.assertTrue(drill.getQuestions().stream().anyMatch(
                        q -> q.getTitle() != null && !q.getTitle().contains(NODE_LRU)),
                "存在标题不含节点名的映射题，且必须出现在下钻结果中");
        // 口径：无首次作答样本的题不给 0%，而是 null
        for (NodeDrillQuestionVO q : drill.getQuestions()) {
            if (q.getFirstAttemptCount() == 0) {
                Assertions.assertNull(q.getFirstCorrectRate(), "无样本题目 firstCorrectRate 应为 null");
            } else {
                Assertions.assertNotNull(q.getFirstCorrectRate());
                Assertions.assertTrue(q.getFirstCorrectRate() >= 0 && q.getFirstCorrectRate() <= 1);
            }
        }
        if (drill.getFirstAccuracy() != null) {
            Assertions.assertTrue(drill.getFirstAccuracy() >= 0 && drill.getFirstAccuracy() <= 1);
        }
        // 覆盖率与平均掌握度分开展示
        Assertions.assertNotNull(drill.getCoverage());
        Assertions.assertTrue(drill.getCoverage() >= 0 && drill.getCoverage() <= 1);
    }

    @Test
    void timelineRequiresClassNameAndClassMembership() {
        // className 缺省不允许（防全库事件）
        Assertions.assertThrows(BusinessException.class,
                () -> learningTeacherService.timeline(null, "", null, null, 1, 10));
        // 指定学生必须属于班级（teacher_demo 未绑定 os2026）
        User teacher = teacher();
        Assertions.assertThrows(BusinessException.class,
                () -> learningTeacherService.timeline(teacher.getId(), CLASS_OS2026, null, null, 1, 10));
        // 未指定学生时限定班级成员集合
        User s1 = student1();
        User s2 = student2();
        TimelineVO timeline = learningTeacherService.timeline(null, CLASS_OS2026, null, null, 1, 50);
        Set<Long> members = new HashSet<>();
        members.add(s1.getId());
        members.add(s2.getId());
        for (com.yupi.springbootinit.model.vo.learning.LearningEventVO event : timeline.getEvents()) {
            Assertions.assertTrue(members.contains(event.getUserId()),
                    "时间线事件只能来自班级成员");
        }
    }

    @Test
    void experimentPassRateDedupesBySubmissionAndExcludesSystemError() {
        // 同一提交映射 3 个节点 → 只算一次；SYSTEM_ERROR 不进分母
        List<LearningEvent> events = new ArrayList<>();
        events.add(judgeEvent(1001L, "PASS"));
        events.add(judgeEvent(1001L, "PASS"));
        events.add(judgeEvent(1001L, "PASS"));
        events.add(judgeEvent(1002L, "FAIL"));
        events.add(judgeEvent(1003L, "SYSTEM_ERROR"));
        events.add(judgeEvent(1004L, "PASS"));
        Map<Long, String> submitResult = new java.util.LinkedHashMap<>();
        for (LearningEvent e : events) {
            submitResult.put(e.getResourceId(), e.getResult());
        }
        Double rate = LearningTeacherServiceImpl.computeExperimentPassRate(submitResult);
        Assertions.assertNotNull(rate);
        // 有效提交：1001(PASS)/1002(FAIL)/1004(PASS)，通过 2/3
        Assertions.assertEquals(2.0 / 3.0, rate, 0.0001);
        // 无有效样本 → null（不显示 0%）
        Map<Long, String> onlySystemError = new java.util.LinkedHashMap<>();
        onlySystemError.put(2001L, "SYSTEM_ERROR");
        Assertions.assertNull(LearningTeacherServiceImpl.computeExperimentPassRate(onlySystemError));
        Assertions.assertNull(LearningTeacherServiceImpl.computeExperimentPassRate(new java.util.LinkedHashMap<>()));
    }

    @Test
    void chapterCoverageAggregatesRealResources() {
        List<ChapterCoverageVO> coverage = learningTeacherService.chapterCoverage();
        Assertions.assertFalse(coverage.isEmpty(), "图谱有章节，应返回完整度列表");
        int totalNodes = coverage.stream().mapToInt(ChapterCoverageVO::getNodeCount).sum();
        Assertions.assertTrue(totalNodes > 0);
        for (ChapterCoverageVO chapter : coverage) {
            Assertions.assertTrue(chapter.getPublishedContentCount() <= chapter.getNodeCount());
            Assertions.assertTrue(chapter.getAssessmentCoveredCount() <= chapter.getNodeCount());
            Assertions.assertTrue(chapter.getExperimentCoveredCount() <= chapter.getNodeCount());
            Assertions.assertTrue(chapter.getUncoveredNodes().size() <= chapter.getUncoveredCount());
        }
    }

    @Test
    void importPreviewValidatesItemsWithoutSaving() {
        String content = "[" +
                "{\"title\":\"导入预览-判断题\",\"content\":\"题干\",\"type\":\"JUDGE\",\"answer\":\"true\"," +
                "\"nodeNames\":[\"" + NODE_LRU + "\"],\"chapter\":\"内存管理\"}," +
                "{\"title\":\"导入预览-坏题\",\"content\":\"题干\",\"type\":\"SINGLE\"," +
                "\"options\":[{\"key\":\"A\",\"value\":\"甲\"},{\"key\":\"B\",\"value\":\"乙\"}]," +
                "\"answer\":\"C\",\"nodeNames\":[\"" + NODE_LRU + "\"]}" +
                "]";
        ImportPreviewVO preview = learningQuestionService.importPreview(content);
        Assertions.assertEquals(2, preview.getTotal());
        Assertions.assertEquals(1, preview.getValidCount());
        Assertions.assertEquals(1, preview.getInvalidCount());
        Assertions.assertTrue(preview.getItems().get(1).getError().contains("答案 key 不在选项中"));
        // 确认导入：仅有效项入库为 DRAFT
        ImportResultVO result = learningQuestionService.importCommit(content, teacher());
        Assertions.assertEquals(1, result.getCreatedCount());
        Assertions.assertEquals(1, result.getSkippedCount());
        try {
            Long createdId = result.getQuestionIds().get(0);
            LearningQuestion created = learningQuestionMapper.selectById(createdId);
            Assertions.assertEquals("DRAFT", created.getReviewStatus());
            List<LearningQuestionNode> mappings = learningQuestionNodeMapper.selectList(
                    new LambdaQueryWrapper<LearningQuestionNode>()
                            .eq(LearningQuestionNode::getQuestionId, createdId));
            Assertions.assertEquals(1, mappings.size());
            Assertions.assertEquals(NODE_LRU, mappings.get(0).getNodeName());
        } finally {
            // 物理清理导入产生的题目与映射
            for (Long id : result.getQuestionIds()) {
                learningQuestionNodeMapper.delete(new LambdaQueryWrapper<LearningQuestionNode>()
                        .eq(LearningQuestionNode::getQuestionId, id));
                learningQuestionMapper.deleteById(id);
            }
        }
    }

    @Test
    void nodeContentPublishedOnlyForWorkbench() {
        User teacher = teacher();
        User student = student1();
        com.yupi.springbootinit.model.dto.learning.LearningNodeContentSaveRequest request =
                new com.yupi.springbootinit.model.dto.learning.LearningNodeContentSaveRequest();
        request.setNodeName(NODE_LRU);
        request.setContentMd("LRU 学习素材：命中后移到队尾，缺页淘汰队首。");
        request.setStatus("PUBLISHED");
        try {
            Assertions.assertTrue(learningNodeContentService.saveContent(request, teacher));
            Assertions.assertEquals(request.getContentMd(),
                    learningNodeContentService.getByNode(NODE_LRU).getContentMd());
            // 学生工作台消费 PUBLISHED 素材
            NodeWorkbenchVO workbench = learningMasteryService.nodeWorkbench(student, NODE_LRU);
            Assertions.assertEquals(request.getContentMd(), workbench.getContentMd());
            // 草稿不下发
            request.setStatus("DRAFT");
            Assertions.assertTrue(learningNodeContentService.saveContent(request, teacher));
            NodeWorkbenchVO draftWorkbench = learningMasteryService.nodeWorkbench(student, NODE_LRU);
            Assertions.assertNull(draftWorkbench.getContentMd(), "DRAFT 素材不应下发学生侧");
        } finally {
            // 物理清理：本表 nodeName 唯一键，逻辑删除行会占用键导致后续插入冲突
            learningNodeContentMapper.physicalDeleteByNodeName(NODE_LRU);
        }
    }

    private LearningEvent judgeEvent(Long submitId, String result) {
        LearningEvent event = new LearningEvent();
        event.setResourceId(submitId);
        event.setResult(result);
        return event;
    }
}
