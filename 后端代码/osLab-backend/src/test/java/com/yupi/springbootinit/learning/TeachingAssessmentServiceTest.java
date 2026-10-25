package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeachingAssessmentServiceTest {
    final JdbcTemplate db=mock(JdbcTemplate.class);final NodeIdentityService identities=mock(NodeIdentityService.class);
    final TeachingAssessmentService service=new TeachingAssessmentService();final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup(){ReflectionTestUtils.setField(service,"jdbc",db);ReflectionTestUtils.setField(service,"identities",identities);when(identities.requireCode("os.test")).thenReturn(new NodeIdentityService.Identity("os.test","renamed","original"));when(db.queryForObject(anyString(),eq(Integer.class),any())).thenReturn(1);}
    ObjectNode question()throws Exception{return (ObjectNode)json.readTree("{\"questionCode\":\"q.test\",\"title\":\"T\",\"content\":\"C\",\"type\":\"JUDGE\",\"options\":[],\"answer\":\"false\",\"analysis\":\"because\",\"difficulty\":2,\"sectionCode\":\"sec.test\",\"sourceRef\":{\"sourceCode\":\"source.test\",\"origin\":\"SELF_AUTHORED\"},\"nodeMappings\":[{\"nodeCode\":\"os.test\",\"role\":\"PRIMARY\"}]}");}
    @Test void strictFourAnswerShapesRejectBooleanAndStringifiedArrays()throws Exception{ObjectNode q=question();service.validate(q);q.put("answer",false);assertThrows(BusinessException.class,()->service.validate(q));q.put("type","MULTIPLE");q.put("answer","[\"A\"]");assertThrows(BusinessException.class,()->service.validate(q));q.put("type","FILL_BLANK");q.putArray("answer").add("LRU").add("最近最久未使用");assertDoesNotThrow(()->service.validate(q));q.putArray("answer").add(1);assertThrows(BusinessException.class,()->service.validate(q));}
    @Test void identicalReimportDoesNotCreateRevisionOrChangePublishedRow()throws Exception{ObjectNode q=question();ObjectNode stored=q.deepCopy();((ObjectNode)stored.path("nodeMappings").get(0)).put("weight",1.0);when(db.queryForList(contains("FROM learning_question WHERE"),any(Object.class))).thenReturn(List.of(row("id",42L,"revision",3,"reviewStatus","PUBLISHED")));when(db.queryForList(contains("FROM learning_question_revision"),any(Object.class))).thenReturn(List.of(row("contentJson",stored.toString(),"revision",3)));assertEquals(42L,service.save(q,7));assertTrue(writes().isEmpty());}
    @Test void publishedEditCreatesDraftWithoutChangingActiveAnswerOrId()throws Exception{ObjectNode q=question();ObjectNode previous=q.deepCopy();previous.put("answer","true");when(db.queryForList(contains("FROM learning_question WHERE"),any(Object.class))).thenReturn(List.of(row("id",42L,"revision",3,"reviewStatus","PUBLISHED")));when(db.queryForList(contains("FROM learning_question_revision"),any(Object.class))).thenReturn(List.of(row("contentJson",previous.toString(),"revision",3)));assertEquals(42L,service.save(q,7));assertEquals(1,writes().size());assertTrue(writes().get(0).getArgument(0,String.class).startsWith("INSERT INTO learning_question_revision"));assertEquals(42L,((Number)writes().get(0).getArgument(2)).longValue());assertEquals(4,((Number)writes().get(0).getArgument(3)).intValue());}
    @Test void publishRequiresApprovedRevision()throws Exception{when(db.queryForList(contains("FROM learning_question_revision"),any(Object.class))).thenReturn(List.of(row("id",77L,"revision",4,"reviewStatus","DRAFT","contentJson",question().toString())));assertThrows(BusinessException.class,()->service.review(42,"PUBLISH",null,7));assertTrue(writes().isEmpty());}
    @Test void staleChangedRevisionIsRejectedBeforeWritingButUnchangedReimportIsAllowed()throws Exception {
        ObjectNode current=question();current.put("answer","true");
        when(db.queryForList(contains("FROM learning_question WHERE"),any(Object.class))).thenReturn(List.of(row("id",42L,"revision",3,"reviewStatus","PUBLISHED")));
        when(db.queryForList(contains("FROM learning_question_revision"),any(Object.class))).thenReturn(List.of(row("contentJson",current.toString(),"revision",3)));
        ObjectNode stale=question();stale.put("revision",1);
        assertThrows(BusinessException.class,()->service.save(stale,7));assertTrue(writes().isEmpty());
        current.put("revision",1);assertEquals(42L,service.save(current,7));assertTrue(writes().isEmpty());
    }
    List<org.mockito.invocation.Invocation> writes(){List<org.mockito.invocation.Invocation> out=new ArrayList<>();for(var i:mockingDetails(db).getInvocations())if(i.getMethod().getName().equals("update"))out.add(i);return out;}
    static Map<String,Object> row(Object...pairs){return TeachingAssessmentService.map(pairs);}
}
