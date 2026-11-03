package com.yupi.springbootinit.ai.plan;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.LearningQuestionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 题目 code → 学生可作答题目 id 的解析回归。
 *
 * <p>解析必须同时满足：code 存在、未删除、已发布、同课程、落在学生可见谓词内。
 * 只满足其中一部分时必须返回 null——区分任务宁可少一个，也不能引用不可作答的题目。
 */
class AiContentScopeServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LearningQuestionService questionService = mock(LearningQuestionService.class);
    private final AiContentScopeService service = new AiContentScopeService();

    private final User user = new User();
    private LearningQuestion question;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "content-scope-test");
        TableInfoHelper.initTableInfo(assistant, LearningQuestion.class);

        user.setId(7L);
        question = new LearningQuestion();
        question.setId(2117582691588841476L);
        question.setQuestionCode("q.db.fd.closure-composite");
        question.setIsDelete(0);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "learningQuestionService", questionService);
    }

    @Test
    void publishedAndEligibleQuestionResolvesToItsDatabaseId() {
        when(questionService.list(any())).thenReturn(List.of(question));
        when(questionService.listEligibleQuestionIds(any(), eq(user)))
                .thenReturn(List.of(2117582691588841476L));

        Long id = service.eligibleQuestionIdByCode(user, "course.database-systems",
                "q.db.fd.closure-composite");

        assertEquals(2117582691588841476L, id);
        verify(questionService).listEligibleQuestionIds(
                argThat((LearningQuestionQueryRequest request) ->
                        "course.database-systems".equals(request.getCourseCode())), eq(user));
    }

    @Test
    void questionOutsideTheEligiblePredicateIsRejected() {
        when(questionService.list(any())).thenReturn(List.of(question));
        // 例如仍是草稿、属于别的课程，或被任务作用域过滤掉
        when(questionService.listEligibleQuestionIds(any(), eq(user)))
                .thenReturn(Collections.emptyList());

        assertNull(service.eligibleQuestionIdByCode(user, "course.database-systems",
                "q.db.fd.closure-composite"));
    }

    @Test
    void unknownOrDeletedCodeIsRejected() {
        when(questionService.list(any())).thenReturn(Collections.emptyList());
        assertNull(service.eligibleQuestionIdByCode(user, "course.database-systems", "q.db.fd.nope"));
        verify(questionService, never()).listEligibleQuestionIds(any(), any());
    }

    @Test
    void blankInputsAreRejectedWithoutQuerying() {
        assertNull(service.eligibleQuestionIdByCode(null, "course.database-systems", "q.db.fd.x"));
        assertNull(service.eligibleQuestionIdByCode(user, "course.database-systems", "  "));
        verifyNoInteractions(questionService);
    }

    @Test
    void databaseFailureDegradesToNoQuestionInsteadOfThrowing() {
        when(questionService.list(any())).thenThrow(new IllegalStateException("db down"));
        assertNull(service.eligibleQuestionIdByCode(user, "course.database-systems",
                "q.db.fd.closure-composite"), "解析失败不得让整个规划失败");
    }
}
