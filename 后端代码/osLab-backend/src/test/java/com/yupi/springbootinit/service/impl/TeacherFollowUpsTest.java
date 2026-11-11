package com.yupi.springbootinit.service.impl;

import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.mapper.StudentsMapper;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.Students;
import com.yupi.springbootinit.model.vo.learning.TeacherFollowUpsVO;
import com.yupi.springbootinit.service.ClassManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherFollowUpsTest {
    private final LearningTeacherServiceImpl service = new LearningTeacherServiceImpl();
    private final StudentsMapper students = mock(StudentsMapper.class);
    private final ClassManagementService classes = mock(ClassManagementService.class);
    private final LearningMasteryMapper mastery = mock(LearningMasteryMapper.class);
    private final List<Object[]> nodeArgs = new ArrayList<>(), dueArgs = new ArrayList<>();
    private boolean emptyCourse;
    private final com.yupi.springbootinit.learning.PublishedCourseScopeService scope = mock(com.yupi.springbootinit.learning.PublishedCourseScopeService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
        if (!invocation.getMethod().getName().equals("queryForList")) return RETURNS_DEFAULTS.answer(invocation);
        Object[] args = invocation.getArguments();
        String sql = (String) args[0];
        if (sql.contains("FROM `user`")) return Arrays.asList(Map.of("id", 1L), Map.of("id", 2L));
        if (sql.contains("SELECT DISTINCT codes.nodeCode")) {
            nodeArgs.add(args);
            return emptyCourse ? Collections.emptyList() : Arrays.asList("node.a", "node.b");
        }
        if (sql.contains("FROM learning_review_item")) {
            dueArgs.add(args);
            return Arrays.asList(Map.of("userId", 1L, "nodeCode", "node.a"), Map.of("userId", 1L, "nodeCode", "node.a"), Map.of("userId", 99L, "nodeCode", "node.a"), Map.of("userId", 2L, "nodeCode", "other"));
        }
        return RETURNS_DEFAULTS.answer(invocation);
    });
    private final NodeIdentityService identities = mock(NodeIdentityService.class);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"studentsMapper",students); ReflectionTestUtils.setField(service,"classManagementService",classes);
        ReflectionTestUtils.setField(service,"identityJdbc",jdbc); ReflectionTestUtils.setField(service,"learningMasteryMapper",mastery);
        ReflectionTestUtils.setField(service,"nodeIdentities",identities);
        ReflectionTestUtils.setField(service,"courseScope",scope);
        when(scope.nodes(anyString())).thenAnswer(i -> {
            nodeArgs.add(new Object[] {i.getArgument(0)});
            if (emptyCourse) return Collections.emptyMap();
            Map<String, NodeIdentityService.Identity> map = new LinkedHashMap<>();
            map.put("node.a", new NodeIdentityService.Identity("node.a", "A", "legacy.a"));
            map.put("node.b", new NodeIdentityService.Identity("node.b", "B", "node.b"));
            return map;
        });
        Students a=new Students(); a.setUserId(1L); Students duplicate=new Students(); duplicate.setUserId(1L);
        when(students.selectList(any())).thenReturn(Arrays.asList(a,duplicate));
        when(classes.memberIdsOfClass("class-a")).thenReturn(Arrays.asList(Map.of("userId",1L),Map.of("userId",2L),Map.of("userId",3L)));
        when(identities.requireCode("node.a")).thenReturn(new NodeIdentityService.Identity("node.a","A","legacy.a"));
        when(identities.requireCode("node.b")).thenReturn(new NodeIdentityService.Identity("node.b","B","node.b"));
        LearningMastery red=new LearningMastery();red.setUserId(1L);red.setNodeName("legacy.a");red.setStatus("RED");red.setConceptEvidenceCount(1);
        LearningMastery noEvidence=new LearningMastery();noEvidence.setUserId(2L);noEvidence.setNodeCode("node.a");noEvidence.setStatus("RED");noEvidence.setConceptEvidenceCount(0);noEvidence.setPracticeEvidenceCount(0);
        when(mastery.selectList(any())).thenReturn(Arrays.asList(red,noEvidence));
    }
    @Test void deduplicatesMembersAndSeparatesDueWeakAndMissingEvidence() {
        TeacherFollowUpsVO v=service.followUps("class-a","course.a");
        assertEquals(2,v.getStudentCount()); assertEquals(1,v.getDueReviewStudentCount()); assertEquals(1,v.getWeakStudentCount()); assertEquals(2,v.getMissingEvidenceStudentCount());
        Map<String,Integer> counts=new HashMap<>();for(TeacherFollowUpsVO.Item i:v.getItems())counts.put(i.getKind()+"/"+i.getNodeCode(),i.getStudentCount());
        assertEquals(1,counts.get("DUE_REVIEW/node.a"));assertEquals(1,counts.get("WEAK/node.a"));
        assertEquals(1,counts.get("MISSING_EVIDENCE/node.a"));assertEquals(2,counts.get("MISSING_EVIDENCE/node.b"));
        assertTrue(Arrays.asList(nodeArgs.get(0)).contains("course.a"));assertTrue(Arrays.asList(dueArgs.get(0)).contains("course.a"));
        verify(classes).memberIdsOfClass("class-a");
    }
    @Test void noMembersReturnsZeroAndDoesNotReadEvidence() {
        when(students.selectList(any())).thenReturn(Collections.emptyList());
        when(classes.memberIdsOfClass("empty")).thenReturn(Collections.emptyList());
        TeacherFollowUpsVO v=service.followUps("empty","course.a");
        assertEquals(0,v.getStudentCount());assertEquals(0,v.getMissingEvidenceStudentCount());assertTrue(v.getItems().isEmpty());
        verifyNoInteractions(jdbc,mastery);
    }
    @Test void noCourseNodesDoesNotInventStudentEvidence() {
        emptyCourse = true;
        TeacherFollowUpsVO v=service.followUps("class-a","course.empty");
        assertEquals(2,v.getStudentCount());assertEquals(0,v.getMissingEvidenceStudentCount());assertTrue(v.getItems().isEmpty());verifyNoInteractions(mastery);
    }
    @Test void classesBatchDeduplicatesAndKeepsEmptyClasses() {
        JdbcTemplate rosterJdbc = mock(JdbcTemplate.class, call -> {
            if (call.getMethod().getName().equals("queryForList")) {
                String sql = call.getArgument(0);
                if (sql.contains("FROM students")) return Arrays.asList(
                        Map.of("className", "a", "userId", 1L), Map.of("className", "a", "userId", 1L));
                if (sql.contains("FROM teaching_class")) {
                    Map<String, Object> empty = new HashMap<>(); empty.put("className", "b"); empty.put("userId", null);
                    return Arrays.asList(Map.of("className", "a", "userId", 1L), Map.of("className", "a", "userId", 2L), empty);
                }
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        ReflectionTestUtils.setField(service, "identityJdbc", rosterJdbc);
        java.util.List<com.yupi.springbootinit.model.vo.learning.ClassOptionVO> result = service.classes();
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getClassName());
        assertEquals(2, result.get(0).getStudentCount());
        assertEquals("b", result.get(1).getClassName());
        assertEquals(0, result.get(1).getStudentCount());
        assertEquals(2, mockingDetails(rosterJdbc).getInvocations().size());
    }

}

