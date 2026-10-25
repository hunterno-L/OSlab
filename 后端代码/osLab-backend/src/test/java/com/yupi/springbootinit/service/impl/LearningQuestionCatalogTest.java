package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogChapterVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogNodeVO;
import com.yupi.springbootinit.model.vo.learning.LearningCatalogVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 题目目录聚合（阶段 4）单元回归：
 * 节点共享题去重、未归类合并与排序、最新有效作答语义（与 studyState 过滤同口径）、
 * PUBLISHED/未删除 SQL 口径、chapter 过滤与空结构、VO 不泄露答案字段。
 */
class LearningQuestionCatalogTest {
    private final LearningQuestionMapper questionMapper = mock(LearningQuestionMapper.class);
    private final LearningQuestionNodeMapper nodeMapper = mock(LearningQuestionNodeMapper.class);
    private final LearningAttemptMapper attemptMapper = mock(LearningAttemptMapper.class);
    private final LearningQuestionServiceImpl service = new LearningQuestionServiceImpl();
    private final User user = new User();

    @BeforeEach
    void setUp() {
        // 生产 application.yml 设定 map-underscore-to-camel-case=false（列名驼峰）；
        // 手工初始化的 TableInfo 必须一致，否则蛇形列名会污染 MP 静态列缓存。
        MybatisConfiguration testConfiguration = new MybatisConfiguration();
        testConfiguration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "question-catalog-test");
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);
        user.setId(7L);
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "learningAttemptMapper", attemptMapper);
    }

    @Test
    void multiNodeSharedQuestionCountsOncePerChapterAndOncePerNode() {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, "内存管理")));
        // 同题映射两个节点（LRU 含重复映射行）：节点各计一次，章节只计一次
        when(nodeMapper.selectList(any()))
                .thenReturn(List.of(mapping(101L, "LRU"), mapping(101L, "LRU"), mapping(101L, "FIFO")));

        LearningCatalogVO vo = service.questionCatalog((String) null, user);

        assertEquals(1, vo.getTotalQuestions());
        assertEquals(1, vo.getChapters().size());
        assertEquals(2, vo.getNodes().size());
        assertEquals(0, vo.getAttempted());
        assertNotNull(vo.getSections());
        assertTrue(vo.getSections().isEmpty());
        LearningCatalogChapterVO chapter = vo.getChapters().get(0);
        assertEquals("内存管理", chapter.getChapter());
        assertEquals(1, chapter.getQuestionCount());
        assertEquals(2, chapter.getNodes().size());
        assertEquals(1, node(chapter, "LRU").getQuestionCount());
        assertEquals(1, node(chapter, "FIFO").getQuestionCount());
        assertEquals(0, node(chapter, "LRU").getAttempted());
        assertEquals(0, node(chapter, "LRU").getNeedsReview());
        assertEquals(0, node(chapter, "LRU").getCorrect());
    }

    @Test
    void blankChaptersMergeIntoUncategorizedWhichSortsLast() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(201L, null),
                question(202L, ""),
                question(203L, "  "),
                question(204L, "作业管理"),
                question(205L, "进程管理")));
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                mapping(201L, "进程调度"),
                mapping(202L, "页面置换"),
                mapping(203L, "文件系统"),
                mapping(204L, "进程调度"),
                mapping(205L, "页面置换")));

        LearningCatalogVO vo = service.questionCatalog((String) null, user);

        assertEquals(5, vo.getTotalQuestions());
        assertEquals(3, vo.getChapters().size());
        // 章节中文序：进程管理(jin) < 作业管理(zuo)；"未归类"恒排最后（拼音 wei 本应排在 zuo 前）
        assertEquals("进程管理", vo.getChapters().get(0).getChapter());
        assertEquals("作业管理", vo.getChapters().get(1).getChapter());
        LearningCatalogChapterVO uncategorized = vo.getChapters().get(2);
        assertEquals("未归类", uncategorized.getChapter());
        // null/空串/空白章节合并为同一"未归类"分桶
        assertEquals(3, uncategorized.getQuestionCount());
        // 节点按 nodeName 中文序：进程调度(jin) < 文件系统(wen) < 页面置换(ye)
        assertEquals(List.of("进程调度", "文件系统", "页面置换"),
                List.of(uncategorized.getNodes().get(0).getNodeName(),
                        uncategorized.getNodes().get(1).getNodeName(),
                        uncategorized.getNodes().get(2).getNodeName()));
    }

    @Test
    void latestAttemptSemanticsMatchesStudyState() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(301L, "内存管理"),
                question(302L, "内存管理"),
                question(303L, "内存管理")));
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                mapping(301L, "LRU"), mapping(301L, "页面置换"), mapping(302L, "LRU"), mapping(303L, "LRU")));
        // 作答按 createTime asc, id asc 返回（与 SQL 排序一致）：末位即"最新有效作答"
        when(attemptMapper.selectList(any())).thenReturn(List.of(
                attempt(1L, 301L, 0, 1000L), // 先错
                attempt(2L, 301L, 1, 2000L), // 后对 -> correct
                attempt(3L, 302L, 1, 1000L), // 先对
                attempt(4L, 302L, 0, 2000L))); // 后错 -> needsReview

        LearningCatalogVO vo = service.questionCatalog((String) null, user);

        assertEquals(3, vo.getTotalQuestions());
        LearningCatalogChapterVO chapter = vo.getChapters().get(0);
        assertEquals(3, chapter.getQuestionCount());
        // LRU：301 纠错为对、302 最新答错、303 未作答
        LearningCatalogNodeVO lru = node(chapter, "LRU");
        assertEquals(3, lru.getQuestionCount());
        assertEquals(2, lru.getAttempted());
        assertEquals(1, lru.getCorrect());
        assertEquals(1, lru.getNeedsReview());
        // 301 同时映射"页面置换"：节点局部口径各自反映作答状态，不互相挤占
        LearningCatalogNodeVO pageReplace = node(chapter, "页面置换");
        assertEquals(1, pageReplace.getQuestionCount());
        assertEquals(1, pageReplace.getAttempted());
        assertEquals(1, pageReplace.getCorrect());
        assertEquals(0, pageReplace.getNeedsReview());
    }

    @Test
    void sqlScopeMatchesStudentPublishedFilterAndUserScopedAttempts() {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L, "内存管理")));
        when(nodeMapper.selectList(any())).thenReturn(List.of(mapping(101L, "LRU")));
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt(1L, 101L, 1, 1000L)));

        service.questionCatalog((String) null, user);

        // 三次查询完成聚合，无 N+1
        verify(questionMapper, times(1)).selectList(any());
        verify(nodeMapper, times(1)).selectList(any());
        verify(attemptMapper, times(1)).selectList(any());
        verify(attemptMapper, never()).selectCount(any());
        verify(nodeMapper, never()).selectCount(any());
        verify(questionMapper, never()).selectById(any());

        // 题目查询仅取 PUBLISHED 且未逻辑删除（与 list/page 学生口径一致）
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestion>> questions =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(questions.capture());
        questions.getValue().getSqlSegment();
        assertTrue(questions.getValue().getParamNameValuePairs().containsValue("PUBLISHED"));
        assertTrue(questions.getValue().getParamNameValuePairs().containsValue(0));

        // 映射与作答查询均限定在可见题目集合内，作答按本人 userId 过滤
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestionNode>> nodes =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(nodeMapper).selectList(nodes.capture());
        nodes.getValue().getSqlSegment();
        assertTrue(nodes.getValue().getParamNameValuePairs().containsValue(101L));

        ArgumentCaptor<LambdaQueryWrapper<LearningAttempt>> attempts =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attemptMapper).selectList(attempts.capture());
        attempts.getValue().getSqlSegment();
        assertTrue(attempts.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(attempts.getValue().getParamNameValuePairs().containsValue(101L));
    }

    @Test
    void emptyCatalogReturnsCompleteZeroStructure() {
        when(questionMapper.selectList(any())).thenReturn(List.of());

        LearningCatalogVO vo = service.questionCatalog((String) null, user);

        assertEquals(0, vo.getTotalQuestions());
        assertNotNull(vo.getChapters());
        assertTrue(vo.getChapters().isEmpty());
        verifyNoInteractions(nodeMapper, attemptMapper);
    }

    @Test
    void chapterFilterAppliesIncludingUncategorizedSentinel() {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(401L, "内存管理"),
                question(402L, "进程管理"),
                question(403L, null)));
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                mapping(401L, "LRU"), mapping(402L, "进程调度"), mapping(403L, "文件系统")));

        // 指定章节：只返回该章节
        LearningCatalogVO byChapter = service.questionCatalog("内存管理", user);
        assertEquals(1, byChapter.getTotalQuestions());
        assertEquals(1, byChapter.getChapters().size());
        assertEquals("内存管理", byChapter.getChapters().get(0).getChapter());
        assertEquals(1, byChapter.getChapters().get(0).getQuestionCount());
        assertEquals(1, byChapter.getChapters().get(0).getNodes().size());
        assertEquals("LRU", byChapter.getChapters().get(0).getNodes().get(0).getNodeName());

        // "未归类"哨兵：命中章节为空的题目
        LearningCatalogVO uncategorized = service.questionCatalog("未归类", user);
        assertEquals(1, uncategorized.getTotalQuestions());
        assertEquals(1, uncategorized.getChapters().size());
        assertEquals("未归类", uncategorized.getChapters().get(0).getChapter());
        assertEquals("文件系统", uncategorized.getChapters().get(0).getNodes().get(0).getNodeName());

        // 空白入参返回全部；无命中章节返回空结构
        assertEquals(3, service.questionCatalog("  ", user).getTotalQuestions());
        LearningCatalogVO noMatch = service.questionCatalog("不存在的章节", user);
        assertEquals(0, noMatch.getTotalQuestions());
        assertTrue(noMatch.getChapters().isEmpty());
    }

    @Test
    void serializedCatalogNeverContainsAnswerOrAnalysis() throws Exception {
        when(questionMapper.selectList(any())).thenReturn(List.of(
                question(501L, "内存管理"), question(502L, null)));
        when(nodeMapper.selectList(any())).thenReturn(List.of(mapping(501L, "LRU"), mapping(502L, "LRU")));
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt(1L, 501L, 1, 1000L)));

        LearningCatalogVO vo = service.questionCatalog((String) null, user);

        // 类型层面不存在答案/解析字段（三个 VO 一致）
        for (Class<?> type : List.of(LearningCatalogVO.class, LearningCatalogChapterVO.class,
                LearningCatalogNodeVO.class)) {
            for (String field : List.of("answer", "correctAnswer", "analysis", "misconception")) {
                assertThrows(NoSuchFieldException.class, () -> type.getDeclaredField(field),
                        type.getSimpleName() + " 不得声明字段 " + field);
            }
        }
        // 序列化 JSON 不含答案/解析键
        String json = new ObjectMapper().writeValueAsString(vo);
        assertFalse(json.contains("answer"));
        assertFalse(json.contains("correctAnswer"));
        assertFalse(json.contains("analysis"));
        assertFalse(json.contains("misconception"));
    }

    private LearningQuestion question(Long id, String chapter) {
        LearningQuestion question = new LearningQuestion();
        question.setId(id);
        question.setType("JUDGE");
        question.setUserId(9L);
        question.setChapter(chapter);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);
        question.setAnswer("\"true\"");
        question.setAnalysis("解析");
        question.setMisconception("误区");
        return question;
    }

    private LearningQuestionNode mapping(Long questionId, String nodeName) {
        LearningQuestionNode mapping = new LearningQuestionNode();
        mapping.setQuestionId(questionId);
        mapping.setNodeName(nodeName);
        return mapping;
    }

    private LearningAttempt attempt(long id, long questionId, int correct, long createTimeMillis) {
        LearningAttempt attempt = new LearningAttempt();
        attempt.setId(id);
        attempt.setUserId(7L);
        attempt.setQuestionId(questionId);
        attempt.setIsCorrect(correct);
        attempt.setCreateTime(new Date(createTimeMillis));
        return attempt;
    }

    private LearningCatalogNodeVO node(LearningCatalogChapterVO chapter, String nodeName) {
        return chapter.getNodes().stream()
                .filter(n -> n.getNodeName().equals(nodeName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少节点：" + nodeName));
    }
}
