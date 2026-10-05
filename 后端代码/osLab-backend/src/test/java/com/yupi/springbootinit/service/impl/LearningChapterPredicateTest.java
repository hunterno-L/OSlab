package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 5 §5.1 共享章节谓词 + §5.2 候选 ID 集单元回归：
 * null/空串/空白/字面值"未归类"归一为同一章节；目录统计、题目分页、测评候选
 * 三处必须使用同一谓词语义（此处覆盖分页与候选两处，目录处见 LearningQuestionCatalogTest）。
 */
class LearningChapterPredicateTest {
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final LearningAttemptMapper attemptMapper = mock(LearningAttemptMapper.class);
    private final UserService userService = mock(UserService.class);
    private final LearningQuestionServiceImpl service = new LearningQuestionServiceImpl();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "chapter-predicate-test");
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);
        user.setId(7L);
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "learningAttemptMapper", attemptMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        when(userService.isAdmin(user)).thenReturn(false);
    }

    // region 谓词纯函数

    @Test
    void chapterKeyNormalizesBlankAndLiteralSentinel() {
        assertEquals("未归类", LearningQuestionServiceImpl.chapterKey(null));
        assertEquals("未归类", LearningQuestionServiceImpl.chapterKey(""));
        assertEquals("未归类", LearningQuestionServiceImpl.chapterKey("   "));
        assertEquals("未归类", LearningQuestionServiceImpl.chapterKey("未归类"));
        assertEquals("未归类", LearningQuestionServiceImpl.chapterKey(" 未归类 "));
        assertEquals("内存管理", LearningQuestionServiceImpl.chapterKey(" 内存管理 "));
        assertEquals("内存管理", LearningQuestionServiceImpl.chapterKey("内存管理"));
    }

    @Test
    void matchesChapterSharesSemanticsAcrossCallers() {
        // 未归类哨兵命中 null/空白/字面值三种章节
        assertTrue(LearningQuestionServiceImpl.matchesChapter(null, "未归类"));
        assertTrue(LearningQuestionServiceImpl.matchesChapter("", "未归类"));
        assertTrue(LearningQuestionServiceImpl.matchesChapter("   ", "未归类"));
        assertTrue(LearningQuestionServiceImpl.matchesChapter("未归类", "未归类"));
        // 过滤条件空白 = 不过滤（全部命中）
        assertTrue(LearningQuestionServiceImpl.matchesChapter(null, null));
        assertTrue(LearningQuestionServiceImpl.matchesChapter("内存管理", "  "));
        // 普通章节：归一化后精确匹配，未归类不与其他章节混淆
        assertTrue(LearningQuestionServiceImpl.matchesChapter(" 内存管理 ", "内存管理"));
        assertFalse(LearningQuestionServiceImpl.matchesChapter(null, "内存管理"));
        assertFalse(LearningQuestionServiceImpl.matchesChapter("未归类", "内存管理"));
        assertFalse(LearningQuestionServiceImpl.matchesChapter("内存管理", "进程管理"));
    }

    // endregion

    // region 题目分页：共享谓词 + 内存分页边界

    @Test
    void pageVoChapterFilterUsesSharedPredicateWithMemoryPaginationBoundary() {
        when(questionMapper.selectList(any())).thenReturn(uncategorizedNinePlusThreeQuestions());
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        // 第 1 页：9 道"未归类"（含 null/空白/字面值），每页 5 条
        LearningQuestionQueryRequest pageOne = request("未归类", 1, 5);
        var first = service.pageVO(pageOne, user);
        assertEquals(9, first.getTotal());
        assertEquals(5, first.getRecords().size());
        assertTrue(first.getRecords().stream().allMatch(vo -> vo.getId() <= 5));

        // 第 2 页边界：余量 4 条，且全部来自"未归类"家族（含字面值 id=9）
        LearningQuestionQueryRequest pageTwo = request("未归类", 2, 5);
        var second = service.pageVO(pageTwo, user);
        assertEquals(9, second.getTotal());
        assertEquals(4, second.getRecords().size());
        assertTrue(second.getRecords().stream().allMatch(vo -> vo.getId() >= 6 && vo.getId() <= 9));
        assertTrue(second.getRecords().stream().noneMatch(vo -> "内存管理".equals(vo.getChapter())));

        // 第 3 页越界：空 records，total 不变
        LearningQuestionQueryRequest pageThree = request("未归类", 3, 5);
        var third = service.pageVO(pageThree, user);
        assertEquals(9, third.getTotal());
        assertTrue(third.getRecords().isEmpty());

        // 学生口径 SQL：仅 PUBLISHED（章节过滤在内存谓词完成，不另写一套 SQL）
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestion>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper, times(3)).selectList(wrapper.capture());
        wrapper.getAllValues().forEach(w -> {
            w.getSqlSegment();
            assertTrue(w.getParamNameValuePairs().containsValue("PUBLISHED"));
        });
    }

    @Test
    void pageVoNormalChapterDoesNotMatchUncategorized() {
        when(questionMapper.selectList(any())).thenReturn(uncategorizedNinePlusThreeQuestions());
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        var result = service.pageVO(request("内存管理", 1, 5), user);

        assertEquals(3, result.getTotal());
        assertTrue(result.getRecords().stream().allMatch(vo -> vo.getId() >= 10));
    }

    // endregion

    // region 测评候选 ID 集（§5.2）：与目录/分页同一谓词，无 50 上限

    @Test
    void listEligibleQuestionIdsUncategorizedCoversAllSixtyOneWithoutCap() {
        List<LearningQuestion> questions = new ArrayList<>();
        for (long id = 1; id <= 61; id++) {
            questions.add(publishedQuestion(id, null));
        }
        // 第 55 条是字面值"未归类"章节，同样属于该章节家族
        questions.set(54, publishedQuestion(55L, "未归类"));
        when(questionMapper.selectList(any())).thenReturn(questions);

        List<Long> ids = service.listEligibleQuestionIds(request("未归类", 1, 20), user);

        assertEquals(61, ids.size(), "候选必须是完整 eligible ID 集，不存在 50 条隐含上限");
        assertTrue(ids.contains(55L), "第 55 条（>50）必须保留在候选集中");
        assertEquals(Long.valueOf(1), ids.get(0));
        assertEquals(Long.valueOf(61), ids.get(60));
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1) < ids.get(i), "id 升序，保证服务端稳定顺序");
        }
        // 学生口径：仅 PUBLISHED 且未逻辑删除
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestion>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(wrapper.capture());
        wrapper.getValue().getSqlSegment();
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("PUBLISHED"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void listEligibleQuestionIdsNormalChapterAndStudyStateFilter() {
        List<LearningQuestion> questions = new ArrayList<>();
        for (long id = 1; id <= 61; id++) {
            questions.add(publishedQuestion(id, id <= 58 ? "内存管理" : null));
        }
        when(questionMapper.selectList(any())).thenReturn(questions);
        // 本人作答：1、2 已作答（作答只看当前用户，不串他人）
        when(attemptMapper.selectList(any())).thenReturn(List.of(
                attempt(1L, 1L), attempt(2L, 2L)));

        // 普通章节过滤：只命中 58 道内存管理（未归类家族不混入普通章节）
        assertEquals(58, service.listEligibleQuestionIds(chapterRequest("内存管理"), user).size());

        // 学习状态 UNANSWERED（不设章节过滤）：全集 61 排除本人已作答的 1、2
        LearningQuestionQueryRequest filtered = chapterRequest(null);
        filtered.setStudyState("UNANSWERED");
        List<Long> unanswered = service.listEligibleQuestionIds(filtered, user);
        assertEquals(61, service.listEligibleQuestionIds(chapterRequest(null), user).size(),
                "未过滤学习状态时全集完整，不存在 50 上限");
        assertEquals(59, unanswered.size());
        assertFalse(unanswered.contains(1L));
        assertFalse(unanswered.contains(2L));
    }

    // endregion

    // region fixtures

    private LearningQuestionQueryRequest request(String chapter, int current, int pageSize) {
        LearningQuestionQueryRequest request = new LearningQuestionQueryRequest();
        request.setChapter(chapter);
        request.setCurrent(current);
        request.setPageSize(pageSize);
        return request;
    }

    /** 仅章节条件（current/pageSize 对候选 ID 集无意义，仍填默认值） */
    private LearningQuestionQueryRequest chapterRequest(String chapter) {
        return request(chapter, 1, 20);
    }

    /** 9 道"未归类"家族（null/空串/空白/字面值）+ 3 道内存管理 */
    private List<LearningQuestion> uncategorizedNinePlusThreeQuestions() {
        List<LearningQuestion> questions = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            questions.add(publishedQuestion(id, null));
        }
        for (long id = 4; id <= 6; id++) {
            questions.add(publishedQuestion(id, ""));
        }
        for (long id = 7; id <= 8; id++) {
            questions.add(publishedQuestion(id, "   "));
        }
        questions.add(publishedQuestion(9L, "未归类"));
        for (long id = 10; id <= 12; id++) {
            questions.add(publishedQuestion(id, "内存管理"));
        }
        return questions;
    }

    private LearningQuestion publishedQuestion(long id, String chapter) {
        LearningQuestion question = new LearningQuestion();
        question.setId(id);
        question.setType("SINGLE");
        question.setUserId(9L);
        question.setChapter(chapter);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);
        question.setAnswer("\"A\"");
        question.setTitle("题目" + id);
        return question;
    }

    private LearningAttempt attempt(long id, long questionId) {
        LearningAttempt attempt = new LearningAttempt();
        attempt.setId(id);
        attempt.setUserId(7L);
        attempt.setQuestionId(questionId);
        attempt.setIsCorrect(0);
        return attempt;
    }

    // endregion
}
