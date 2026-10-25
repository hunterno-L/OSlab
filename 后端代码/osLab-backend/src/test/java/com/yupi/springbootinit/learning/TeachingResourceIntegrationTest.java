package com.yupi.springbootinit.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.model.dto.learning.LearningAnswerRequest;
import com.yupi.springbootinit.model.dto.learning.LearningQuestionReviewRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.LearningQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import javax.annotation.Resource;
import static org.junit.jupiter.api.Assertions.*;

/** Rollback-only real SQL check, without adding textbook content to the running catalog. */
@SpringBootTest
@Transactional(transactionManager="learningSqlTransactionManager")
class TeachingResourceIntegrationTest {
 @Resource JdbcTemplate jdbc;
 @Resource TeachingAssessmentService service;
 @Resource LearningQuestionService questions;
 @Resource ExperimentMaintenanceService experiments;
 @Resource(name="learningSqlTransactionManager") PlatformTransactionManager sqlTx;
 final ObjectMapper json=new ObjectMapper();
 @Test void experimentMaintainsOriginalIdAndReusesDraftActivityOnRepeatedImport()throws Exception {
  String unit="unit.test.experiment",node="os.test.experiment";long questionId=jdbc.queryForObject("SELECT id FROM question WHERE isDelete=0 ORDER BY id LIMIT 1",Long.class);String course=jdbc.queryForObject("SELECT courseCode FROM course_catalog ORDER BY courseCode LIMIT 1",String.class),section=jdbc.queryForObject("SELECT sectionCode FROM course_section WHERE courseCode=? ORDER BY sectionCode LIMIT 1",String.class,course);
  jdbc.update("INSERT INTO knowledge_node_registry(id,nodeCode,name,nodeType) VALUES(-702,?,'Experiment integration','CONCEPT')",node);
  ObjectNode draft=json.createObjectNode();draft.put("unitCode",unit);draft.put("courseCode",course);draft.put("sectionCode",section);draft.put("title","Test unit");draft.put("estimatedMinutes",5);draft.put("sortOrder",1);draft.putArray("objectives").add("Verify maintenance");draft.putArray("nodeMappings").addObject().put("nodeCode",node).put("role","TEACHES");draft.putArray("activities");
  jdbc.update("INSERT INTO learning_unit(id,unitCode,courseCode,sectionCode,title,draftJson,userId) VALUES(-702,?,?,?,'Test unit',?,7)",unit,course,section,draft.toString());
  ObjectNode e=json.createObjectNode();e.put("questionId",String.valueOf(questionId));e.put("resourceCode","exp.test.integration");e.put("title","Experiment integration");e.putArray("contentChanges");e.putArray("nodeMappings").addObject().put("nodeCode",node).put("role","PRIMARY").put("weight",0.7);e.putArray("unitMappings").addObject().put("unitCode",unit).put("required",true).put("completionRule","PASS");
  ObjectNode pack=json.createObjectNode();pack.put("schemaVersion","oslab-experiment-maintenance/1.0");pack.put("batchCode","batch.test.experiment");pack.put("contributor","A");pack.putArray("experiments").add(e);
  String originalTitle=jdbc.queryForObject("SELECT title FROM question WHERE id=?",String.class,questionId);
  assertEquals(true,experiments.preview(pack).get("valid"));experiments.commit(pack,7);
  JsonNode before=json.readTree(jdbc.queryForObject("SELECT beforeJson FROM experiment_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1",String.class,questionId));
  JsonNode after=json.readTree(jdbc.queryForObject("SELECT afterJson FROM experiment_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1",String.class,questionId));
  assertTrue(before.path("createTime").isTextual(),"JDBC LocalDateTime must be preserved as an ISO snapshot value");
  assertDoesNotThrow(()->java.time.LocalDateTime.parse(before.path("createTime").asText()));
  assertEquals(before.path("createTime"),after.path("createTime"));assertEquals(before.path("updateTime"),after.path("updateTime"));
  assertEquals(originalTitle,jdbc.queryForObject("SELECT title FROM question WHERE id=?",String.class,questionId));
  assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM course_resource WHERE resourceCode='exp.test.integration'",String.class));
  assertEquals(1L,experiments.preview(pack).get("unchanged"));experiments.commit(pack,7);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM experiment_revision WHERE questionId=?",Integer.class,questionId));
  for(String action:new String[]{"SUBMIT","APPROVE","APPLY"})experiments.review(String.valueOf(questionId),json.createObjectNode().put("action",action).put("confirmed",true),7);
  String activity=jdbc.queryForObject("SELECT activityCode FROM learning_unit_activity WHERE unitCode=? AND isDelete=0",String.class,unit);
  experiments.commit(pack,7);
  assertEquals(activity,jdbc.queryForObject("SELECT activityCode FROM learning_unit_activity WHERE unitCode=? AND isDelete=0",String.class,unit));
  assertEquals(questionId,jdbc.queryForObject("SELECT questionId FROM course_resource WHERE resourceCode='exp.test.integration'",Long.class));
  assertEquals(0.7,jdbc.queryForObject("SELECT weight FROM experiment_node WHERE questionId=? AND nodeCode=?",Double.class,questionId,node),1e-9);
  assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM course_resource WHERE resourceCode='exp.test.integration'",String.class));
 }
 @Test @Transactional(transactionManager="learningSqlTransactionManager",propagation=Propagation.NOT_SUPPORTED)
 void failedSqlTransactionActuallyRollsBackJdbcWrites(){TransactionTemplate tx=new TransactionTemplate(sqlTx);assertThrows(IllegalStateException.class,()->tx.execute(s->{jdbc.update("INSERT INTO knowledge_node_registry(id,nodeCode,name,nodeType) VALUES(-701,'os.test.rollback','rollback','CONCEPT')");throw new IllegalStateException("expected rollback");}));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_node_registry WHERE nodeCode='os.test.rollback'",Integer.class));}
 @Test void repeatedImportAndPublishedRevisionPreserveIdAnswerAndAttemptSnapshot()throws Exception {
  jdbc.update("INSERT INTO knowledge_node_registry(id,nodeCode,name,nodeType) VALUES(-700,'os.test.integration','Integration identity','CONCEPT')");
  String sectionCode=jdbc.queryForObject("SELECT sectionCode FROM course_section ORDER BY sectionCode LIMIT 1",String.class);
  String sourceCode=jdbc.queryForObject("SELECT sourceCode FROM course_source ORDER BY sourceCode LIMIT 1",String.class);
  ObjectNode q=(ObjectNode)json.readTree("{\"questionCode\":\"q.test.integration\",\"title\":\"Integration test\",\"content\":\"Test\",\"type\":\"JUDGE\",\"options\":[],\"answer\":\"false\",\"analysis\":\"original explanation\",\"difficulty\":2,\"nodeMappings\":[{\"nodeCode\":\"os.test.integration\",\"role\":\"PRIMARY\"}]}");q.put("sectionCode",sectionCode);q.putObject("sourceRef").put("sourceCode",sourceCode).put("origin","SELF_AUTHORED_SAMPLE");
  ObjectNode pack=json.createObjectNode();pack.put("schemaVersion","oslab-authoring/1.0");pack.put("packageType","QUESTIONS");pack.put("batchCode","batch.integration");pack.put("contributor","L");pack.putArray("questions").add(q);
  assertEquals(true,service.preview(pack).get("valid"));service.commit(pack,7);long id=Long.parseLong(service.manage("q.test.integration").get("questionId").toString());
  assertEquals(1L,service.preview(pack).get("unchanged"));service.commit(pack,7);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_question_revision WHERE questionId=?",Integer.class,id));
  for(String action:new String[]{"SUBMIT","APPROVE","PUBLISH"})assertTrue(service.review(id,action,null,7));
  User student=new User();student.setId(700L);LearningAnswerRequest answer=new LearningAnswerRequest();answer.setQuestionId(id);answer.setAnswer("false");answer.setSubmissionKey("integration-idempotent");assertTrue(questions.answerQuestion(answer,student).getCorrect());
  q.put("answer","true");q.put("analysis","new explanation");service.commit(pack,7);
  assertEquals("\"false\"",jdbc.queryForObject("SELECT answer FROM learning_question WHERE id=?",String.class,id));
  User author=new User();author.setId(7L);author.setUserRole("user");
  LearningQuestionReviewRequest submit=new LearningQuestionReviewRequest();submit.setQuestionId(id);submit.setAction("SUBMIT");
  assertTrue(questions.reviewQuestion(submit,author));
  assertEquals("SUBMITTED",jdbc.queryForObject("SELECT reviewStatus FROM learning_question_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1",String.class,id));
  for(String action:new String[]{"APPROVE","PUBLISH"})assertTrue(service.review(id,action,null,7));
  assertTrue(service.review(id,"OFFLINE",null,7));
  assertEquals("OFFLINE",jdbc.queryForObject("SELECT reviewStatus FROM learning_question WHERE id=?",String.class,id));
  assertEquals("OFFLINE",jdbc.queryForObject("SELECT reviewStatus FROM learning_question_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1",String.class,id));
  assertTrue(service.review(id,"PUBLISH",null,7));
  assertEquals("PUBLISHED",jdbc.queryForObject("SELECT reviewStatus FROM learning_question WHERE id=?",String.class,id));
  assertEquals("PUBLISHED",jdbc.queryForObject("SELECT reviewStatus FROM learning_question_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1",String.class,id));
  assertEquals("\"true\"",jdbc.queryForObject("SELECT answer FROM learning_question WHERE id=?",String.class,id));assertEquals(id,Long.parseLong(service.manage("q.test.integration").get("questionId").toString()));
  assertEquals("original explanation",questions.answerQuestion(answer,student).getAnalysis());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_attempt WHERE questionId=? AND userId=700",Integer.class,id));
  assertFalse(json.valueToTree(questions.getQuestionVO(id,student)).has("answer"));
 }
}
