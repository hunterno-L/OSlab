package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.mapper.LearningAttemptMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.mapper.LearningQuestionNodeMapper;
import com.yupi.springbootinit.model.entity.LearningAttempt;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.LearningQuestionNode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningQuestionVO;
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

/** 关联题目批量转换的查询数量与学生解析权限回归。 */
class LearningQuestionBatchTest {
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
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(testConfiguration, "question-batch-test");
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);
        TableInfoHelper.initTableInfo(assistant, LearningQuestionNode.class);
        TableInfoHelper.initTableInfo(assistant, LearningAttempt.class);
        user.setId(7L);
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);
        ReflectionTestUtils.setField(service, "learningQuestionNodeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "learningAttemptMapper", attemptMapper);
    }

    @Test
    void twoQuestionsUseThreeQueriesAndUnansweredQuestionKeepsAnalysisPrivate() throws Exception {
        when(questionMapper.selectList(any())).thenReturn(List.of(question(101L), question(102L)));
        when(nodeMapper.selectList(any())).thenReturn(List.of(mapping(101L, "LRU"), mapping(101L, "页面置换"), mapping(102L, "LRU")));
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt(1L, 101L, 0), attempt(2L, 101L, 1)));

        List<LearningQuestionVO> result = service.getQuestionVOs(List.of(101L, 102L), user);

        assertEquals(2, result.size());
        LearningQuestionVO answered = result.get(0);
        assertEquals(List.of("LRU", "页面置换"), answered.getNodeNames());
        assertTrue(answered.getHasAttempted());
        assertEquals(2, answered.getAttemptCount());
        assertEquals("WRONG", answered.getFirstResult());
        assertEquals("CORRECT", answered.getLatestResult());
        assertEquals("解析", answered.getAnalysis());
        LearningQuestionVO unanswered = result.get(1);
        assertFalse(unanswered.getHasAttempted());
        assertEquals(0, unanswered.getAttemptCount());
        assertNull(unanswered.getAnalysis());
        assertNull(unanswered.getMisconception());
        assertThrows(NoSuchFieldException.class, () -> LearningQuestionVO.class.getDeclaredField("answer"));
        verify(questionMapper, times(1)).selectList(any());
        verify(nodeMapper, times(1)).selectList(any());
        verify(attemptMapper, times(1)).selectList(any());
        verify(attemptMapper, never()).selectCount(any());
        verify(questionMapper, never()).selectById(any());

        ArgumentCaptor<LambdaQueryWrapper<LearningAttempt>> attempts = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(attemptMapper).selectList(attempts.capture());
        attempts.getValue().getSqlSegment();
        assertTrue(attempts.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(attempts.getValue().getParamNameValuePairs().containsValue(101L));
        assertTrue(attempts.getValue().getParamNameValuePairs().containsValue(102L));
        ArgumentCaptor<LambdaQueryWrapper<LearningQuestion>> questions = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(questionMapper).selectList(questions.capture());
        questions.getValue().getSqlSegment();
        assertTrue(questions.getValue().getParamNameValuePairs().containsValue("PUBLISHED"));
        assertTrue(questions.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void emptyIdsSkipAllQueries() {
        assertTrue(service.getQuestionVOs(List.of(), user).isEmpty());
        verifyNoInteractions(questionMapper, nodeMapper, attemptMapper);
    }

    @Test
    void noPublishedQuestionsSkipMappingAndAttemptQueries() {
        when(questionMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getQuestionVOs(List.of(101L), user).isEmpty());
        verify(questionMapper).selectList(any());
        verifyNoInteractions(nodeMapper, attemptMapper);
    }

    private LearningQuestion question(Long id) {
        LearningQuestion question = new LearningQuestion();
        question.setId(id);
        question.setType("JUDGE");
        question.setUserId(9L);
        question.setReviewStatus("PUBLISHED");
        question.setIsDelete(0);
        question.setAnalysis("解析");
        question.setMisconception("误区");
        question.setAnswer("true");
        return question;
    }

    private LearningQuestionNode mapping(Long id, String name) {
        LearningQuestionNode mapping = new LearningQuestionNode();
        mapping.setQuestionId(id);
        mapping.setNodeName(name);
        return mapping;
    }

    private LearningAttempt attempt(Long id, Long questionId, int correct) {
        LearningAttempt attempt = new LearningAttempt();
        attempt.setId(id);
        attempt.setUserId(7L);
        attempt.setQuestionId(questionId);
        attempt.setIsCorrect(correct);
        attempt.setCreateTime(new Date(id * 1000));
        return attempt;
    }
}
