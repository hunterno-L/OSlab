package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionAddRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningAnswerResultVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionManageVO;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

/**
 * Phase 2 §6.2 契约测试：studyState 服务端跨页过滤、用户隔离、
 * 学生/管理 VO 分离（学生无答案，管理含答案与审核历史）。
 */
@SpringBootTest
class LearningQuestionProgressTest {

    @Resource
    private LearningQuestionService learningQuestionService;
    @Resource
    private UserService userService;

    private User student() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo1"));
    }

    private User teacher() {
        return userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "teacher_demo"));
    }

    /** 建一道封闭题并发布，返回 questionId */
    private Long createPublished(String title, String answer, String nodeName) {
        User t = teacher();
        LearningQuestionAddRequest addReq = new LearningQuestionAddRequest();
        addReq.setTitle(title + " " + System.currentTimeMillis());
        addReq.setContent("进度契约测试题。");
        addReq.setType("JUDGE");
        addReq.setAnswer(answer);
        addReq.setChapter("内存管理");
        addReq.setNodeNames(List.of(nodeName));
        Long qid = learningQuestionService.addQuestion(addReq, t);
        for (String action : List.of("SUBMIT", "APPROVE", "PUBLISH")) {
            LearningQuestionReviewRequest review = new LearningQuestionReviewRequest();
            review.setQuestionId(qid);
            review.setAction(action);
            Assertions.assertTrue(learningQuestionService.reviewQuestion(review, t),
                    "审核 " + action + " 应成功");
        }
        return qid;
    }

    private void answer(Long questionId, String answer) {
        LearningAnswerRequest req = new LearningAnswerRequest();
        req.setQuestionId(questionId);
        req.setAnswer(answer);
        learningQuestionService.answerQuestion(req, student());
    }

    private void answerAs(Long questionId, String answer, User user) {
        LearningAnswerRequest req = new LearningAnswerRequest();
        req.setQuestionId(questionId);
        req.setAnswer(answer);
        learningQuestionService.answerQuestion(req, user);
    }

    @Test
    void studyState_filtersServerSide() {
        User user = student();
        Long wrongQ = createPublished("进度测试-答错题", "false", "LRU 页面置换算法");
        Long correctQ = createPublished("进度测试-答对题", "true", "LRU 页面置换算法");
        answer(wrongQ, "true");   // 故意答错（正确答案 false）
        answer(correctQ, "true"); // 答对（正确答案 true）

        LearningQuestionQueryRequest wrongReq = new LearningQuestionQueryRequest();
        wrongReq.setCurrent(1);
        wrongReq.setPageSize(50);
        wrongReq.setStudyState("NEEDS_REVIEW");
        Page<LearningQuestionVO> wrongPage =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(wrongReq, user);
        boolean wrongFound = wrongPage.getRecords().stream()
                .anyMatch(q -> q.getId().equals(wrongQ));
        Assertions.assertTrue(wrongFound, "NEEDS_REVIEW 应包含最新答错的题");

        LearningQuestionQueryRequest correctReq = new LearningQuestionQueryRequest();
        correctReq.setCurrent(1);
        correctReq.setPageSize(50);
        correctReq.setStudyState("CORRECT");
        Page<LearningQuestionVO> correctPage =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(correctReq, user);
        boolean correctFound = correctPage.getRecords().stream()
                .anyMatch(q -> q.getId().equals(correctQ));
        Assertions.assertTrue(correctFound, "CORRECT 应包含最新答对的题");
        boolean wrongNotInCorrect = correctPage.getRecords().stream()
                .noneMatch(q -> q.getId().equals(wrongQ));
        Assertions.assertTrue(wrongNotInCorrect, "答错的题不应出现在 CORRECT（除非已纠错）");

        LearningQuestionQueryRequest unansReq = new LearningQuestionQueryRequest();
        unansReq.setCurrent(1);
        unansReq.setPageSize(50);
        unansReq.setStudyState("UNANSWERED");
        Page<LearningQuestionVO> unansPage =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(unansReq, user);
        boolean neitherFound = unansPage.getRecords().stream()
                .noneMatch(q -> q.getId().equals(wrongQ) || q.getId().equals(correctQ));
        Assertions.assertTrue(neitherFound, "UNANSWERED 不包含已作答题");
    }

    @Test
    void correction_flipsStateToCorrect() {
        User user = student();
        Long qid = createPublished("进度测试-纠错题", "false", "LRU 页面置换算法");
        answer(qid, "true");  // 先答错

        LearningQuestionQueryRequest req = new LearningQuestionQueryRequest();
        req.setCurrent(1);
        req.setPageSize(50);
        req.setStudyState("NEEDS_REVIEW");
        Page<LearningQuestionVO> page1 =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(req, user);
        Assertions.assertTrue(page1.getRecords().stream().anyMatch(q -> q.getId().equals(qid)),
                "答错后应在 NEEDS_REVIEW");

        answer(qid, "false"); // 纠错（答对）

        Page<LearningQuestionVO> page2 =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(req, user);
        Assertions.assertFalse(page2.getRecords().stream().anyMatch(q -> q.getId().equals(qid)),
                "纠错后应离开 NEEDS_REVIEW");
        LearningQuestionQueryRequest correctReq = new LearningQuestionQueryRequest();
        correctReq.setCurrent(1);
        correctReq.setPageSize(50);
        correctReq.setStudyState("CORRECT");
        Page<LearningQuestionVO> page3 =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(correctReq, user);
        Assertions.assertTrue(page3.getRecords().stream().anyMatch(q -> q.getId().equals(qid)),
                "纠错后进入 CORRECT");
    }

    @Test
    void users_areIsolatedByState() {
        User userA = student();
        User userB = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, "student_demo2"));
        Long qid = createPublished("进度测试-隔离题", "true", "LRU 页面置换算法");
        answerAs(qid, "true", userA); // A 答对

        LearningQuestionQueryRequest req = new LearningQuestionQueryRequest();
        req.setCurrent(1);
        req.setPageSize(50);
        req.setStudyState("NEEDS_REVIEW");
        Page<LearningQuestionVO> pageA =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(req, userA);
        Assertions.assertFalse(pageA.getRecords().stream().anyMatch(q -> q.getId().equals(qid)),
                "A 已答对应不在 NEEDS_REVIEW");
        Page<LearningQuestionVO> pageB =
                (Page<LearningQuestionVO>) learningQuestionService.pageVO(req, userB);
        boolean bUnanswered = pageB.getRecords().stream().noneMatch(q -> q.getId().equals(qid));
        Assertions.assertTrue(bUnanswered, "B 未作答，不应出现在 NEEDS_REVIEW");
    }

    @Test
    void manageVo_containsAnswerAndStudentVoDoesNot() {
        User t = teacher();
        User s = student();
        Long qid = createPublished("进度测试-VO 分离", "false", "LRU 页面置换算法");

        // 学生端点：类型层面不存在 correctAnswer 字段（U11 契约）
        Assertions.assertThrows(NoSuchFieldException.class, () ->
                LearningQuestionVO.class.getDeclaredField("correctAnswer"),
                "学生 VO 类型不得声明 correctAnswer 字段");
        LearningQuestionVO studentVo = learningQuestionService.getQuestionVO(qid, s);
        Assertions.assertNotNull(studentVo);
        // 管理端点：含答案与审核历史
        LearningQuestionManageVO manageVo = learningQuestionService.getManageQuestionVO(qid, t);
        Assertions.assertEquals("false", manageVo.getCorrectAnswer(), "管理 VO 应含标准答案");
        Assertions.assertNotNull(manageVo.getReviewHistory());
        Assertions.assertTrue(manageVo.getReviewHistory().size() >= 3, "审核历史应含 SUBMIT/APPROVE/PUBLISH");
    }
}
