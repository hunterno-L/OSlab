package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.course.EnabledCourses;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.dto.knowledgegraph.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishedCourseScopeTest {
    @Test void allowListIsExplicitAndCanEnableDatabaseForTests() {
        EnabledCourses defaults = new EnabledCourses("course.operating-systems");
        assertTrue(defaults.contains("course.operating-systems"));
        assertThrows(BusinessException.class, () -> defaults.require("course.database-systems"));
        assertTrue(new EnabledCourses("course.operating-systems, course.database-systems").contains("course.database-systems"));
    }
    @Test void graphKeepsOnlyCourseNodesAndInternalEdgesWithoutMutatingOriginal() {
        GraphDataDTO graph = new GraphDataDTO();
        NodeDTO os = new NodeDTO(); os.setId("1"); os.setNodeCode("os.x"); os.setName("old os");
        NodeDTO db = new NodeDTO(); db.setId("2"); db.setNodeCode("db.x"); db.setName("db");
        NodeDTO legacy = new NodeDTO(); legacy.setId("3"); legacy.setName("legacy db");
        LinkDTO cross = new LinkDTO(); cross.setSource("1"); cross.setTarget("2");
        LinkDTO inner = new LinkDTO(); inner.setSource("2"); inner.setTarget("3");
        graph.setNodes(Arrays.asList(os, db, legacy)); graph.setLinks(Arrays.asList(cross, inner));
        Map<String, NodeIdentityService.Identity> scope = new LinkedHashMap<>();
        scope.put("db.x", new NodeIdentityService.Identity("db.x", "db", "db"));
        scope.put("db.y", new NodeIdentityService.Identity("db.y", "db y", "legacy db"));
        GraphDataDTO filtered = new PublishedCourseScopeService().filterGraph(graph, scope);
        assertEquals(Arrays.asList(db, legacy), filtered.getNodes());
        assertEquals(Collections.singletonList(inner), filtered.getLinks());
        assertEquals(3, graph.getNodes().size());
        assertTrue(new PublishedCourseScopeService().filterGraph(graph, Collections.emptyMap()).getNodes().isEmpty());
    }
    @Test void courseNodesAreResolvedInOneBatchIncludingHistoricalKey() {
        PublishedCourseScopeService service = new PublishedCourseScopeService();
        org.springframework.jdbc.core.JdbcTemplate jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class,
                call -> {
                    if (call.getMethod().getName().equals("queryForList")) {
                        String sql = call.getArgument(0);
                        assertTrue(sql.contains("publishedJson"));
                        assertTrue(sql.contains("learning_question_node"));
                        assertTrue(sql.contains("experiment_node"));
                        return Arrays.asList(Map.of("nodeCode", "os.a", "name", "A", "legacyNodeName", "old A"),
                                Map.of("nodeCode", "os.b", "name", "B"));
                    }
                    return org.mockito.Mockito.RETURNS_DEFAULTS.answer(call);
                });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbc", jdbc);
        Map<String, NodeIdentityService.Identity> nodes = service.nodes("course.operating-systems");
        assertEquals("old A", nodes.get("os.a").storageKey);
        assertEquals("os.b", nodes.get("os.b").storageKey);
        assertEquals(1, org.mockito.Mockito.mockingDetails(jdbc).getInvocations().size());
    }

}
