package com.yupi.springbootinit.learning;
import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class NodeIdentityServiceTest {
 @Test void renamedNodeKeepsExplicitHistoricalKeyAndAmbiguousNamesFail(){JdbcTemplate db=mock(JdbcTemplate.class);NodeIdentityService s=new NodeIdentityService();ReflectionTestUtils.setField(s,"jdbc",db);when(db.queryForList(contains("SELECT r.nodeCode"),any(Object.class))).thenReturn(List.of(TeachingAssessmentService.map("nodeCode","os.memory.lru","name","New display name","legacyNodeName","LRU旧名称")));NodeIdentityService.Identity identity=s.requireCode("os.memory.lru");assertEquals("LRU旧名称",identity.storageKey);assertEquals("New display name",identity.name);when(db.queryForList(contains("SELECT nodeCode FROM knowledge_node_registry"),eq(String.class),any())).thenReturn(Collections.emptyList());when(db.queryForList(contains("SELECT DISTINCT r.nodeCode"),eq(String.class),any(),any(),any())).thenReturn(List.of("os.cpu.fcfs","os.disk.fcfs"));assertThrows(BusinessException.class,()->s.resolve("FCFS"));verify(db,never()).update(anyString(),any(Object.class));}
}
