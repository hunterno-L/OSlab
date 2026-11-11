package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExperimentJudgeModeServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExperimentJudgeModeService service = new ExperimentJudgeModeService();

    ExperimentJudgeModeServiceTest() {
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    @Test void localBuildIsRejectedForBothStoredMetadataForms() {
        for (String json : new String[]{"{\"judgeMode\":\"LOCAL_BUILD\"}",
                "{\"metadataJson\":{\"judgeMode\":\"LOCAL_BUILD\"}}"}) {
            when(jdbc.queryForList(anyString(), eq(String.class), eq(42L)))
                    .thenReturn(Collections.singletonList(json));
            assertEquals("LOCAL_BUILD", service.mode(42L));
            assertThrows(BusinessException.class, () -> service.requireAutomaticJudge(42L));
        }
    }

    @Test void existingQuestionsAndConceptHarnessKeepAutomaticJudging() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L)))
                .thenReturn(Collections.emptyList());
        assertEquals("AUTO", service.mode(42L));
        assertDoesNotThrow(() -> service.requireAutomaticJudge(42L));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L)))
                .thenReturn(Collections.singletonList("{\"metadataJson\":{\"judgeMode\":\"CONCEPT_HARNESS\"}}"));
        assertEquals("CONCEPT_HARNESS", service.mode(42L));
        assertDoesNotThrow(() -> service.requireAutomaticJudge(42L));
    }

    @Test void invalidMetadataCannotSilentlyEnableJudging() {
        for (String invalid : new String[]{"invalid-json", "[]", "null", "{\"judgeMode\":\"local_build\"}",
                "{\"judgeMode\":null}", "{\"judgeMode\":0}", "{\"judgeMode\":\"\"}"}) {
            when(jdbc.queryForList(anyString(), eq(String.class), eq(42L)))
                    .thenReturn(Collections.singletonList(invalid));
            assertThrows(BusinessException.class, () -> service.requireAutomaticJudge(42L));
        }
    }
}
