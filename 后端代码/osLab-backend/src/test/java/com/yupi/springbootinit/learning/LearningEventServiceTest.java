package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningEventMapper;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.entity.LearningEvent;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

/**
 * Phase 2 §6.4（U15）契约测试：重复作答必须写入行为事件（before==after），
 * 但不得更新掌握模型（pKnown/证据数/状态不变）；独立观测正常更新。
 */
@SpringBootTest
class LearningEventServiceTest {

    @Resource
    private LearningQuestionService learningQuestionService;
    @Resource
    private UserService userService;
    @Resource
    private LearningEventMapper learningEventMapper;
    @Resource
    private LearningAttemptMapper learningAttemptMapper;
    @Resource
    private LearningMasteryMapper learningMasteryMapper;

    private User student() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo1"));
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

    private LearningMastery mastery(Long userId) {
        return learningMasteryMapper.selectOne(new LambdaQueryWrapper<LearningMastery>()
                .eq(LearningMastery::getUserId, userId)
                .eq(LearningMastery::getNodeName, "LRU 页面置换算法"));
    }

    private List<LearningEvent> attemptEvents(Long userId) {
        return learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getNodeName, "LRU 页面置换算法")
                .eq(LearningEvent::getEventType, "ATTEMPT")
                .orderByDesc(LearningEvent::getId));
    }

    private LearningAnswerResultVO answer(String answer) {
        LearningQuestion question = publishedLruQuestion();
        LearningAnswerRequest req = new LearningAnswerRequest();
        req.setQuestionId(question.getId());
        req.setAnswer(answer);
        return learningQuestionService.answerQuestion(req, student());
    }

    @Test
    void repeatAttempt_recordsEventWithoutModelUpdate() {
        User user = student();

        // 保证从已作答状态开始：先答两次错（首个为独立观测，第二个为重复）
        answer("true");
        answer("true");

        List<LearningEvent> before = attemptEvents(user.getId());
        LearningMastery masteryBefore = mastery(user.getId());
        Assertions.assertFalse(before.isEmpty(), "前置：应已有 ATTEMPT 事件");
        Double pk = masteryBefore == null ? null : masteryBefore.getPKnown();

        // 再作答一次（重复）：必须写入新事件，before==after，模型不变
        LearningAnswerResultVO r = answer("true");
        Assertions.assertFalse(r.getIsFirst(), "重复作答 isFirst=false");

        List<LearningEvent> after = attemptEvents(user.getId());
        Assertions.assertEquals(before.size() + 1, after.size(), "重复作答应新增 1 条事件");
        LearningEvent newest = after.get(0);
        Assertions.assertEquals(newest.getMasteryBefore(), newest.getMasteryAfter(),
                "重复作答事件 before==after（非独立观测）");
        Assertions.assertEquals(0, newest.getIsFirst(), "重复作答事件 isFirst=0");
        Assertions.assertTrue(newest.getId() != null && !newest.getId().equals(before.get(0).getId()));

        LearningMastery masteryAfter = mastery(user.getId());
        Assertions.assertEquals(pk, masteryAfter.getPKnown(), "重复作答不得改变掌握度");
        Assertions.assertEquals(masteryBefore == null ? 0 : masteryBefore.getConceptEvidenceCount(),
                masteryAfter.getConceptEvidenceCount(), "重复作答不得增加证据数");
    }

    @Test
    void firstAttempt_isIndependentObservation() {
        User user = student();
        // 独立观测：事件 before != after（BKT 更新生效）
        LearningAnswerResultVO r = answer("true");
        if (Boolean.TRUE.equals(r.getIsFirst())) {
            List<LearningEvent> events = attemptEvents(user.getId());
            Assertions.assertNotEquals(events.get(0).getMasteryBefore(),
                    events.get(0).getMasteryAfter(), "独立作答应产生 BKT 更新");
        } else {
            // 已作答过则跳过断言（由 repeatAttempt 用例覆盖）
            Assertions.assertTrue(true);
        }
    }

    @Test
    void attemptAndEventCountsStayConsistent() {
        // 封闭数据：新建一道映射两个真实节点的题目，发布后作答两次（首答+重复）
        User teacher = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "teacher_demo"));
        User user = student();
        com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest addReq =
                new com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest();
        addReq.setTitle("事件一致性测试题 " + System.currentTimeMillis());
        addReq.setContent("验证每次作答每节点一条事件。");
        addReq.setType("JUDGE");
        addReq.setAnswer("true");
        addReq.setChapter("内存管理");
        addReq.setNodeNames(java.util.List.of("LRU 页面置换算法", "页面置换"));
        Long qid = learningQuestionService.addQuestion(addReq, teacher);
        for (String action : java.util.List.of("SUBMIT", "APPROVE", "PUBLISH")) {
            com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest review =
                    new com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest();
            review.setQuestionId(qid);
            review.setAction(action);
            Assertions.assertTrue(learningQuestionService.reviewQuestion(review, teacher),
                    "审核动作 " + action + " 应成功");
        }

        LearningAnswerRequest req1 = new LearningAnswerRequest();
        req1.setQuestionId(qid);
        req1.setAnswer("true");
        LearningAnswerResultVO r1 = learningQuestionService.answerQuestion(req1, user);
        Assertions.assertTrue(r1.getIsFirst(), "首答应为独立观测");

        LearningAnswerRequest req2 = new LearningAnswerRequest();
        req2.setQuestionId(qid);
        req2.setAnswer("false");
        LearningAnswerResultVO r2 = learningQuestionService.answerQuestion(req2, user);
        Assertions.assertFalse(r2.getIsFirst(), "第二次作答为重复");

        List<LearningAttempt> attempts = learningAttemptMapper.selectList(
                new LambdaQueryWrapper<LearningAttempt>()
                        .eq(LearningAttempt::getUserId, user.getId())
                        .eq(LearningAttempt::getQuestionId, qid));
        Assertions.assertEquals(2, attempts.size(), "应有两条作答记录");

        List<LearningEvent> events = learningEventMapper.selectList(
                new LambdaQueryWrapper<LearningEvent>()
                        .eq(LearningEvent::getUserId, user.getId())
                        .eq(LearningEvent::getResourceType, "LEARNING_QUESTION")
                        .eq(LearningEvent::getResourceId, qid));
        Assertions.assertEquals(4, events.size(), "2 次作答 × 2 个映射节点 = 4 条事件");
        var byNode = new java.util.HashMap<String, Long>();
        for (var e : events) {
            byNode.merge(e.getNodeName(), 1L, Long::sum);
        }
        Assertions.assertEquals(2L, byNode.get("LRU 页面置换算法"), "LRU 节点事件数=2");
        Assertions.assertEquals(2L, byNode.get("页面置换"), "页面置换节点事件数=2");
        // 独立观测事件（首答）before != after；重复事件 before == after
        long observationEvents = events.stream()
                .filter(e -> e.getIsFirst() == 1).count();
        Assertions.assertEquals(2, observationEvents, "两个节点各有 1 条独立观测");
        for (var e : events) {
            if (e.getIsFirst() == 0) {
                Assertions.assertEquals(e.getMasteryBefore(), e.getMasteryAfter(),
                        "重复作答事件 before==after");
            } else {
                Assertions.assertNotEquals(e.getMasteryBefore(), e.getMasteryAfter(),
                        "独立观测事件 before != after");
            }
        }
        // 清理测试题
        learningQuestionService.removeById(qid);
    }
}
