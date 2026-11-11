package com.yupi.springbootinit.ai.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.springbootinit.ai.evidence.EvidenceReadService;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.learning.PublishedCourseScopeService;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TutorEvidenceScopeTest {
    private final TutorToolkit toolkit = new TutorToolkit();
    private final PublishedCourseScopeService scopes = mock(PublishedCourseScopeService.class);
    private final EvidenceReadService reader = mock(EvidenceReadService.class);
    private final ObjectMapper json = new ObjectMapper();
    private TutorTool.Context context;
    private Map<String, NodeIdentityService.Identity> nodes;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(toolkit, "publishedCourseScope", scopes);
        ReflectionTestUtils.setField(toolkit, "evidenceReader", reader);
        toolkit.init();
        User user = new User(); user.setId(7L);
        AiLearningSession session = new AiLearningSession(); session.setCourseCode("course.database-systems");
        context = new TutorTool.Context(); context.setUser(user); context.setSession(session);
        nodes = new LinkedHashMap<>();
        nodes.put("db.a", new NodeIdentityService.Identity("db.a", "A", "old A"));
        nodes.put("db.b", new NodeIdentityService.Identity("db.b", "B", "db.b"));
        when(scopes.nodes("course.database-systems")).thenReturn(nodes);
        EvidenceReadService.EvidenceSummary summary = new EvidenceReadService.EvidenceSummary();
        summary.setNarrative("no records");
        when(reader.read(eq(7L), anyList(), eq(12))).thenReturn(summary);
    }

    private TutorTool.Outcome execute(String args) throws Exception {
        return toolkit.find(TutorToolkit.TOOL_EVIDENCE).execute(context, json.readTree(args));
    }

    @SuppressWarnings("unchecked")
    private List<String> capturedKeys() {
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(reader).read(eq(7L), keys.capture(), eq(12));
        return keys.getValue();
    }

    @Test void emptyPublishedCourseNeverReadsAllHistoryAndClearsOldEvidence() throws Exception {
        when(scopes.nodes("course.database-systems")).thenReturn(Collections.emptyMap());
        context.setEvidence(new EvidenceReadService.EvidenceSummary());
        assertEquals("EMPTY", execute("{}").getStatus());
        verifyNoInteractions(reader);
        assertNotNull(context.getEvidence());
        assertTrue(context.getEvidence().getRecords().isEmpty());
        assertNotNull(context.getEvidence().getNarrative());
    }

    @Test void modelCannotSelectOnlyForeignNodes() throws Exception {
        assertEquals("EMPTY", execute("{\"nodeCodes\":[\"os.runtime.system_call\"]}").getStatus());
        verifyNoInteractions(reader);
    }

    @Test void modelMixedNodesAreIntersectedAndLegacyKeyIncluded() throws Exception {
        execute("{\"nodeCodes\":[\"os.outside\",\"db.a\"]}");
        assertEquals(Arrays.asList("db.a", "old A"), capturedKeys());
    }

    @Test void explicitHistoricalKeyResolvesToStableCodeAndHistoryKey() throws Exception {
        execute("{\"nodeCodes\":[\"old A\"]}");
        assertEquals(Arrays.asList("db.a", "old A"), capturedKeys());
    }

    @Test void historicalEventIsReadThroughRealEvidenceServiceWithBoundStorageKey() throws Exception {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "tutor-evidence-scope"),
                com.yupi.springbootinit.model.entity.LearningEvent.class);
        com.yupi.springbootinit.mapper.LearningEventMapper events = mock(com.yupi.springbootinit.mapper.LearningEventMapper.class);
        com.yupi.springbootinit.model.entity.LearningEvent event = new com.yupi.springbootinit.model.entity.LearningEvent();
        event.setId(12L); event.setUserId(7L); event.setNodeName("old A"); event.setResult("PASS");
        when(events.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> query = call.getArgument(0);
            query.getSqlSegment();
            assertTrue(query.getParamNameValuePairs().values().contains("old A"));
            assertTrue(query.getParamNameValuePairs().values().contains("db.a"));
            assertTrue(query.getParamNameValuePairs().values().contains(7L));
            assertFalse(query.getParamNameValuePairs().values().contains("os.outside"));
            return Collections.singletonList(event);
        });
        EvidenceReadService actualReader = new EvidenceReadService();
        ReflectionTestUtils.setField(actualReader, "learningEventMapper", events);
        ReflectionTestUtils.setField(toolkit, "evidenceReader", actualReader);
        assertEquals("OK", execute("{\"nodeCodes\":[\"db.a\",\"os.outside\"]}").getStatus());
        assertEquals("old A", context.getEvidence().getRecords().get(0).getNodeName());
        verify(events).selectList(any());
    }

    @Test void noNodeUsesAllAndOnlyPublishedCourseNodes() throws Exception {
        execute("{}");
        assertEquals(Arrays.asList("db.a", "old A", "db.b"), capturedKeys());
    }

    @Test void pageNodesAreAlsoRestrictedToCourse() throws Exception {
        AiCourseContext page = new AiCourseContext();
        page.setNodeCodes(Arrays.asList("os.outside", "db.b"));
        context.setCourseContext(page);
        execute("{}");
        assertEquals(Collections.singletonList("db.b"), capturedKeys());
    }

    @Test void missingCourseAndFailedScopeBothFailClosed() throws Exception {
        context.getSession().setCourseCode(null);
        assertEquals("FAILED", execute("{}").getStatus());
        verifyNoInteractions(scopes, reader);
        context.getSession().setCourseCode("course.database-systems");
        when(scopes.nodes("course.database-systems")).thenThrow(new IllegalStateException("unavailable"));
        assertEquals("FAILED", execute("{}").getStatus());
        verifyNoInteractions(reader);
    }
}
