package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.learning.MasterySnapshotCodec;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.MasterySnapshotVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.model.vo.learning.WeakNodeVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import com.yupi.springbootinit.service.LearningMasteryService;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

/**
 * Phase 7 §11 集成测试：本人时间线、事件快照与基线、只读回放、待巩固失败归类。
 */
@SpringBootTest
class LearningRecordsServiceTest {

    @Resource
    private LearningQuestionService learningQuestionService;
    @Resource
    private LearningMasteryService learningMasteryService;
    @Resource
    private UserService userService;
    @Resource
    private LearningEventMapper learningEventMapper;
    @Resource
    private KnowledgeGraphService knowledgeGraphService;

    private User student() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo1"));
    }

    private User teacher() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "teacher_demo"));
    }

    private LearningQuestion publishedLruQuestion() {
        List<LearningQuestion> questions = learningQuestionService.list(
                new LambdaQueryWrapper<LearningQuestion>()
                        .eq(LearningQuestion::getReviewStatus, "PUBLISHED")
                        .eq(LearningQuestion::getIsDelete, 0)
                        .like(LearningQuestion::getTitle, "LRU"));
        Assertions.assertFalse(questions.isEmpty(), "种子中应有已发布 LRU 普通题");
        return questions.get(0);
    }

    private void answer(Long questionId, String answer) {
        LearningAnswerRequest req = new LearningAnswerRequest();
        req.setQuestionId(questionId);
        req.setAnswer(answer);
        learningQuestionService.answerQuestion(req, student());
    }

    @Test
    void timelineReturnsOwnEventsWithGroupKeyAndFilters() {
        User user = student();
        answer(publishedLruQuestion().getId(), "true");

        TimelineVO all = learningMasteryService.myTimeline(user, null, null, null, null, 1, 10);
        Assertions.assertTrue(all.getTotal() >= 1, "作答后应有本人事件");
        Assertions.assertTrue(all.getEvents().size() <= 10, "分页大小生效");
        Assertions.assertTrue(all.getEvents().stream()
                        .anyMatch(e -> e.getEventGroupKey() != null
                                && e.getEventGroupKey().startsWith("attempt:")),
                "新事件应携带 attempt 事件组键");

        TimelineVO attempts = learningMasteryService.myTimeline(user, null, "ATTEMPT", null, null, 1, 100);
        Assertions.assertTrue(attempts.getEvents().stream()
                .allMatch(e -> "ATTEMPT".equals(e.getEventType())), "事件类型过滤生效");

        TimelineVO byNode = learningMasteryService.myTimeline(user, "LRU 页面置换算法", null, null, null, 1, 100);
        Assertions.assertTrue(byNode.getEvents().stream()
                .allMatch(e -> "LRU 页面置换算法".equals(e.getNodeName())), "节点过滤生效");

        TimelineVO inRange = learningMasteryService.myTimeline(user, null, null,
                "2020-01-01 00:00:00", "2035-01-01 00:00:00", 1, 10);
        Assertions.assertTrue(inRange.getTotal() >= 1, "宽时间范围应包含事件");
        TimelineVO futureOnly = learningMasteryService.myTimeline(user, null, null,
                "2035-01-01 00:00:00", "2035-01-02 00:00:00", 1, 10);
        Assertions.assertEquals(0, futureOnly.getTotal(), "未来时间范围不应有事件");
    }

    @Test
    void newAttemptCarriesFullSnapshotAndLegacyNodeGetsBaseline() {
        User user = student();
        answer(publishedLruQuestion().getId(), "true");

        LearningEvent newest = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .eq(LearningEvent::getNodeName, "LRU 页面置换算法")
                        .eq(LearningEvent::getEventType, "ATTEMPT")
                        .orderByDesc(LearningEvent::getId)
                        .last("limit 1")).get(0);
        Assertions.assertNotNull(newest.getSnapshotAfter(), "新事件应携带完整快照");
        MasterySnapshotCodec.MasterySnapshot snap = MasterySnapshotCodec.parse(newest.getSnapshotAfter());
        Assertions.assertNotNull(snap);
        Assertions.assertNotNull(snap.pKnown);
        Assertions.assertNotNull(snap.confidence);
        Assertions.assertNotNull(snap.conceptScore);
        Assertions.assertNotNull(snap.practiceScore);
        Assertions.assertNotNull(snap.conceptEvidenceCount);
        Assertions.assertNotNull(snap.practiceEvidenceCount);
        Assertions.assertNotNull(snap.status);
        Assertions.assertEquals(newest.getMasteryAfter(), snap.pKnown, "快照 pKnown 与事件 after 一致");

        // 历史节点（升级前事件无快照）应补 STATE_BASELINE 基线，且不是独立观测
        LearningEvent baseline = learningEventMapper.selectOne(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .eq(LearningEvent::getNodeName, "LRU 页面置换算法")
                        .eq(LearningEvent::getEventType, "STATE_BASELINE"));
        Assertions.assertNotNull(baseline, "存在历史事件的节点应补状态基线");
        Assertions.assertEquals(0, baseline.getIsFirst(), "基线不是独立观测");
        Assertions.assertNotNull(baseline.getSnapshotAfter());
        Assertions.assertNotNull(baseline.getEventGroupKey());
    }

    @Test
    void replaySnapshotIsReadOnlyAndRejectsForeignEvent() {
        User user = student();
        answer(publishedLruQuestion().getId(), "true");
        LearningEvent latest = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .orderByDesc(LearningEvent::getCreateTime)
                        .orderByDesc(LearningEvent::getId)
                        .last("limit 1")).get(0);
        Long beforeCount = learningEventMapper.selectCount(
                new LambdaQueryWrapper<LearningEvent>().eq(LearningEvent::getUserId, user.getId()));

        MasterySnapshotVO vo = learningMasteryService.replaySnapshot(user, latest.getId());
        Assertions.assertEquals(latest.getId(), vo.getAtEventId());
        Assertions.assertNotNull(vo.getGraph());
        Assertions.assertTrue(vo.getGraph().getTotalNodes() > 0, "回放应生成图谱投影");
        Assertions.assertTrue(vo.getGraph().getNodes().stream()
                        .anyMatch(n -> Boolean.TRUE.equals(n.getSnapshotAvailable())),
                "至少一个节点在回放中有完整快照");

        // 回放只读：不写事件
        Long afterCount = learningEventMapper.selectCount(
                new LambdaQueryWrapper<LearningEvent>().eq(LearningEvent::getUserId, user.getId()));
        Assertions.assertEquals(beforeCount, afterCount, "回放不得写事件");

        // 不存在/他人事件 id 拒绝
        Assertions.assertThrows(BusinessException.class,
                () -> learningMasteryService.replaySnapshot(user, 1L));
    }

    @Test
    void weaknessClassifiesCurrentThenCorrected() {
        User user = student();
        User teacher = teacher();
        // 选一个图谱中真实存在、且当前学生没有任何事件的节点，保证首答错误必然 RED
        String freshNode = null;
        for (String candidate : List.of("FIFO 页面置换算法", "OPT 页面置换算法",
                "缺页中断", "页表", "虚拟内存", "进程管理", "进程状态")) {
            if (knowledgeGraphService.findByName(candidate).isEmpty()) {
                continue;
            }
            Long count = learningEventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                    .eq(LearningEvent::getUserId, user.getId())
                    .eq(LearningEvent::getNodeName, candidate));
            if (count == 0) {
                freshNode = candidate;
                break;
            }
        }
        Assumptions.assumeTrue(freshNode != null, "无可用空白节点时跳过（前置数据已覆盖全部候选）");
        final String targetNode = freshNode;

        LearningQuestionAddRequest addReq = new LearningQuestionAddRequest();
        addReq.setTitle("待巩固归类测试题 " + System.currentTimeMillis());
        addReq.setContent("验证失败归类：当前失误与已纠正。");
        addReq.setType("JUDGE");
        addReq.setAnswer("true");
        addReq.setChapter("内存管理");
        addReq.setNodeNames(List.of(targetNode));
        Long qid = learningQuestionService.addQuestion(addReq, teacher);
        for (String action : List.of("SUBMIT", "APPROVE", "PUBLISH")) {
            LearningQuestionReviewRequest review = new LearningQuestionReviewRequest();
            review.setQuestionId(qid);
            review.setAction(action);
            Assertions.assertTrue(learningQuestionService.reviewQuestion(review, teacher));
        }

        try {
            // 首答错误：独立观测 FAIL → 节点 RED，失败归类=当前失误
            answer(qid, "false");
            List<WeakNodeVO> weak = learningMasteryService.weaknessNodes(user);
            WeakNodeVO node = weak.stream()
                    .filter(w -> targetNode.equals(w.getNodeName())).findFirst().orElse(null);
            Assertions.assertNotNull(node, "首答错误后节点应进入待巩固");
            Assertions.assertEquals("RED", node.getStatus());
            Assertions.assertEquals("CURRENT", node.getFailureState(), "无后续活动时为当前失误");
            Assertions.assertNotNull(node.getLastFailure());
            Assertions.assertNotNull(node.getLastFailure().getCertaintyLabel(), "归因应有依据强度标签");
            Assertions.assertNotNull(node.getLastFailure().getAttributionBasis());

            // 同一题再答对（重复作答，同资源）：失败归类变为已纠正
            answer(qid, "true");
            weak = learningMasteryService.weaknessNodes(user);
            node = weak.stream()
                    .filter(w -> targetNode.equals(w.getNodeName())).findFirst().orElse(null);
            Assertions.assertNotNull(node, "重复作答不更新模型，节点仍应在待巩固列表");
            Assertions.assertEquals("CORRECTED", node.getFailureState(), "同资源后续通过为已纠正");
        } finally {
            learningQuestionService.removeById(qid);
        }
    }
}
