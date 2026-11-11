package com.yupi.springbootinit.task;

import com.yupi.springbootinit.model.entity.LearningTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherTaskSummaryTest {
    private final TeacherTaskService service = new TeacherTaskService();
    private Function<InvocationOnMock, List<Map<String, Object>>> query = call -> Collections.emptyList();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class, call ->
            call.getMethod().getName().equals("queryForList") ? query.apply(call) : RETURNS_DEFAULTS.answer(call));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    @Test
    void fullPageKeepsTaskCountsSeparateWithBoundedQueriesAndClassScope() {
        List<LearningTask> tasks = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> recipients = new ArrayList<>();
        for (long id = 1; id <= 50; id++) {
            tasks.add(task(id));
            items.add(Map.of("taskId", id, "itemCount", id, "scoredCount", 1L,
                    "totalMaxScore", BigDecimal.valueOf(id * 10)));
            recipients.add(Map.of("taskId", id, "recipientCount", id + 1,
                    "submittedCount", id, "pendingReviewCount", id - 1));
        }
        query = call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM learning_task_item ")) return items;
            assertTrue(sql.contains("AND classNameSnapshot=?"));
            Object[] args = call.getArguments();
            Object[] params = args[args.length - 1] instanceof Object[]
                    ? (Object[]) args[args.length - 1] : args;
            assertEquals("验收一班", params[params.length - 1]);
            return recipients;
        };

        List<Map<String, Object>> result = summarize(tasks, " 验收一班 ");

        assertEquals(50, result.size());
        for (int index = 0; index < result.size(); index++) {
            long id = index + 1;
            Map<String, Object> row = result.get(index);
            assertEquals("task-" + id, row.get("taskCode"));
            assertEquals(id, row.get("itemCount"));
            assertEquals(id + 1, row.get("recipientCount"));
            assertEquals(id, row.get("submittedCount"));
            assertEquals(id - 1, row.get("pendingReviewCount"));
            assertEquals(BigDecimal.valueOf(id * 10), row.get("totalMaxScore"));
        }
        assertEquals(2, mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("queryForList")).count());
    }

    @Test
    void publishedPolicyKeepsFrozenScoringAndMissingGroupsAreZero() {
        LearningTask published = task(7L);
        published.setPublishedPolicyJson("{\"scored\":true,\"totalMaxScore\":20,\"manualRequired\":true}");

        Map<String, Object> result = summarize(List.of(published), null).get(0);

        assertEquals(0L, result.get("itemCount"));
        assertEquals(0L, result.get("recipientCount"));
        assertEquals(0L, result.get("submittedCount"));
        assertEquals(0L, result.get("pendingReviewCount"));
        assertEquals(true, result.get("scored"));
        assertEquals(new BigDecimal("20"), result.get("totalMaxScore"));
        assertEquals(true, result.get("manualRequired"));
    }

    @Test
    void emptyPageDoesNotQueryStatistics() {
        assertTrue(summarize(Collections.emptyList(), null).isEmpty());
        verifyNoInteractions(jdbc);
    }

    private LearningTask task(long id) {
        LearningTask task = new LearningTask();
        task.setId(id);
        task.setTaskCode("task-" + id);
        task.setStatus("DRAFT");
        return task;
    }

    private List<Map<String, Object>> summarize(List<LearningTask> tasks, String className) {
        return ReflectionTestUtils.invokeMethod(service, "summaries", tasks, className);
    }
}
