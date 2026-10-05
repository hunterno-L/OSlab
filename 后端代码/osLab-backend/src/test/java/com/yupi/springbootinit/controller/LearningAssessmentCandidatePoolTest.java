package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.LearningQuestionService;
import com.yupi.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 5 §5.2 测评候选池单元回归：候选来自完整 eligible ID 集合（无 pageSize=50
 * 隐含上限），抽样为确定性等距采样——第 51 条之后的题目同样有机会进入本轮，
 * 章节等范围过滤由候选服务完成且不放松。
 */
class LearningAssessmentCandidatePoolTest {

    private final LearningQuestionService questionService = mock(LearningQuestionService.class);
    private final LearningAssessmentRunMapper runMapper = mock(LearningAssessmentRunMapper.class);
    private final UserService userService = mock(UserService.class);
    private final LearningAssessmentRunController controller = new LearningAssessmentRunController();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(7L);
        ReflectionTestUtils.setField(controller, "learningQuestionService", questionService);
        ReflectionTestUtils.setField(controller, "assessmentRunMapper", runMapper);
        ReflectionTestUtils.setField(controller, "userService", userService);
        when(userService.getLoginUser(any())).thenReturn(user);
        when(runMapper.insert(any(LearningAssessmentRun.class))).thenReturn(1);
    }

    // region 抽样纯函数

    @Test
    void sampleStableReachesBeyondFiftiethCandidateInPoolOfSixtyOne() {
        List<Long> candidates = new ArrayList<>();
        for (long id = 1; id <= 61; id++) {
            candidates.add(id);
        }

        List<Long> queue = LearningAssessmentRunController.sampleStable(candidates, 20);

        assertEquals(20, queue.size());
        assertEquals(20, queue.stream().distinct().count(), "队列内无重复");
        assertTrue(queue.contains(55L), "第 55 条（旧实现的前 50 上限之外）必须有机会进入候选");
        for (int i = 1; i < queue.size(); i++) {
            assertTrue(queue.get(i - 1) < queue.get(i), "等距采样保持 id 升序稳定顺序");
        }
        // 确定性：同集合同数量结果恒定，可复算
        assertEquals(queue, LearningAssessmentRunController.sampleStable(candidates, 20));
        assertEquals(Long.valueOf(1), queue.get(0), "等距采样覆盖候选池起点");
    }

    @Test
    void sampleStableReturnsWholePoolWhenSmallerThanCount() {
        List<Long> candidates = List.of(3L, 7L, 11L);
        assertEquals(candidates, LearningAssessmentRunController.sampleStable(candidates, 20));
        assertEquals(candidates, LearningAssessmentRunController.sampleStable(candidates, 3));
    }

    // endregion

    // region start 端到端（mock 候选服务）

    @Test
    void startBuildsQueueFromFullEligibleIdSetWithoutFiftyCap() {
        List<Long> eligible = new ArrayList<>();
        for (long id = 1; id <= 61; id++) {
            eligible.add(id);
        }
        when(questionService.listEligibleQuestionIds(any(LearningQuestionQueryRequest.class), eq(user)))
                .thenReturn(eligible);

        LearningAssessmentRunController.StartRequest request = new LearningAssessmentRunController.StartRequest();
        request.setCount(20);
        BaseResponse<LearningAssessmentRunController.StartResult> response = controller.start(request, null);

        LearningAssessmentRunController.StartResult result = response.getData();
        assertNotNull(result);
        assertEquals(20, result.getQuestionIds().size());
        assertTrue(result.getQuestionIds().contains(55L), "第 51 条之后的题目仍可进候选");

        // 会话落库：队列固定、归属当前用户
        ArgumentCaptor<LearningAssessmentRun> runCaptor = ArgumentCaptor.forClass(LearningAssessmentRun.class);
        verify(runMapper).insert(runCaptor.capture());
        LearningAssessmentRun run = runCaptor.getValue();
        assertEquals(7L, run.getUserId());
        assertEquals("ACTIVE", run.getStatus());
        assertTrue(run.getQuestionIdsJson().contains("55"));

        // 候选请求不要求前端传 questionIds，范围条件原值透传
        ArgumentCaptor<LearningQuestionQueryRequest> queryCaptor =
                ArgumentCaptor.forClass(LearningQuestionQueryRequest.class);
        verify(questionService).listEligibleQuestionIds(queryCaptor.capture(), eq(user));
        assertNotNull(queryCaptor.getValue());
    }

    @Test
    void startRejectsEmptyEligiblePool() {
        when(questionService.listEligibleQuestionIds(any(LearningQuestionQueryRequest.class), eq(user)))
                .thenReturn(List.of());

        LearningAssessmentRunController.StartRequest request = new LearningAssessmentRunController.StartRequest();
        request.setCount(5);
        assertThrows(BusinessException.class, () -> controller.start(request, null));
    }

    @Test
    void startPassesChapterFilterThroughToEligibleQuery() {
        when(questionService.listEligibleQuestionIds(any(LearningQuestionQueryRequest.class), eq(user)))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L));

        LearningAssessmentRunController.StartRequest request = new LearningAssessmentRunController.StartRequest();
        request.setCount(3);
        request.setChapter("未归类");
        BaseResponse<LearningAssessmentRunController.StartResult> response = controller.start(request, null);

        assertEquals(3, response.getData().getQuestionIds().size());
        // 章节条件原值透传（"未归类"哨兵语义由候选服务的共享谓词处理）
        ArgumentCaptor<LearningQuestionQueryRequest> queryCaptor =
                ArgumentCaptor.forClass(LearningQuestionQueryRequest.class);
        verify(questionService).listEligibleQuestionIds(queryCaptor.capture(), eq(user));
        assertEquals("未归类", queryCaptor.getValue().getChapter());
    }

    // endregion
}
