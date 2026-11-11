package com.yupi.springbootinit.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.learning.ExperimentJudgeModeService;
import com.yupi.springbootinit.model.dto.task.TaskItemRequest;
import com.yupi.springbootinit.model.entity.LearningTaskItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskContentLocalBuildTest {
    private static final long SOURCE = 1930155905954140162L;
    private final ObjectMapper json = new ObjectMapper();
    private final TaskContentService service = new TaskContentService();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExperimentJudgeModeService modes = mock(ExperimentJudgeModeService.class);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "experimentJudgeModes", modes);
        Map<String,Object> row = new HashMap<>();
        row.put("id", SOURCE); row.put("title", "source"); row.put("content", "description");
        row.put("tags", "[]"); row.put("answer", ""); row.put("judgeCase", "[{\"input\":\"\",\"output\":\"\"}]");
        row.put("judgeConfig", "{\"timeLimit\":1000,\"memoryLimit\":128}");
        row.put("originalCode", "code"); row.put("updateTime", new Timestamp(123000));
        when(jdbc.queryForList(contains("FROM question"), eq(SOURCE))).thenReturn(Collections.singletonList(row));
        when(jdbc.queryForList(contains("FROM experiment_node"), eq(String.class), eq(SOURCE))).thenReturn(Collections.emptyList());
    }
    private TaskItemRequest reference(ObjectNode body) {
        body.put("sourceQuestionId", String.valueOf(SOURCE));
        TaskItemRequest request = new TaskItemRequest(); request.setItemType("EXPERIMENT");
        request.setSourceMode("REFERENCE"); request.setContent(body); return request;
    }
    @Test void localReferenceDefaultsToManualWithoutPlaceholderCases() {
        when(modes.mode(SOURCE)).thenReturn("LOCAL_BUILD");
        ObjectNode result = service.normalizeItem(reference(json.createObjectNode()));
        assertEquals(String.valueOf(SOURCE), result.path("sourceQuestionId").asText());
        assertEquals("LOCAL_BUILD", result.path("judgeMode").asText());
        assertTrue(result.path("manualReview").asBoolean()); assertFalse(result.path("requirePass").asBoolean());
        assertTrue(result.path("judgeCase").isEmpty()); assertTrue(result.path("requireText").asBoolean());
    }
    @Test void localReferenceCannotOverrideToAutomaticAtNormalizeOrPublish() {
        when(modes.mode(SOURCE)).thenReturn("LOCAL_BUILD");
        ObjectNode override = json.createObjectNode().put("manualReview", false);
        assertThrows(BusinessException.class, () -> service.normalizeItem(reference(override)));
        ObjectNode forcePass = json.createObjectNode().put("requirePass", true);
        assertThrows(BusinessException.class, () -> service.normalizeItem(reference(forcePass)));
        ObjectNode draft = service.normalizeItem(reference(json.createObjectNode()));
        LearningTaskItem item = new LearningTaskItem(); item.setItemType("EXPERIMENT");
        assertTrue(service.validateDraftForPublish(item, draft.deepCopy()).path("manualReview").asBoolean());
        draft.put("manualReview", false);
        assertThrows(BusinessException.class, () -> service.validateDraftForPublish(item, draft));
    }
    @Test void automaticReferenceKeepsJudgeCasesAndAutomaticMode() {
        when(modes.mode(SOURCE)).thenReturn("AUTO");
        ObjectNode result = service.normalizeItem(reference(json.createObjectNode()));
        assertEquals("AUTO", result.path("judgeMode").asText());
        assertFalse(result.path("manualReview").asBoolean()); assertTrue(result.path("requirePass").asBoolean());
        assertEquals(1, result.path("judgeCase").size());
    }
}
