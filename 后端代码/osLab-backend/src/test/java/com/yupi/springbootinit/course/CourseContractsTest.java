package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.yupi.springbootinit.course.CourseJson.*;

/** Pure service boundary tests: no production database and no second grader. */
class CourseContractsTest {
    static final String UNIT="{\"unitCode\":\"unit.test\",\"courseCode\":\"course.test\",\"sectionCode\":\"sec.test\",\"title\":\"Unit\",\"sortOrder\":1,\"estimatedMinutes\":5,\"objectives\":[\"explain\"],\"nodeMappings\":[],\"activities\":[{\"activityCode\":\"act.test\",\"title\":\"check\",\"activityType\":\"ASSESSMENT\",\"sortOrder\":1,\"required\":true,\"reference\":{\"kind\":\"QUESTION\",\"code\":\"q.test\"},\"completionRule\":\"SUBMIT\",\"config\":{}}]}";

    static class MemoryRepository extends CourseRepository {
        Map<String,Map<String,Object>> entities=new HashMap<>();
        List<Map<String,Object>> published=new ArrayList<>(),progress=new ArrayList<>(),sections=new ArrayList<>();
        Map<String,Object> evidence,coursePosition,unitPosition;Map<String,String> hashes=new HashMap<>();
        List<String> mutations=new ArrayList<>();List<Object[]> mutationArgs=new ArrayList<>();Object[] evidenceArgs;
        long sequence=100;
        @Override public long id(){return ++sequence;}
        @Override public Map<String,Object> entity(String table,String key,String code){return entities.get(table+":"+code);}
        @Override public List<Map<String,Object>> rows(String sql,Object...args){
            if(sql.contains("learning_activity_progress"))return progress;
            if(sql.contains("publishedJson FROM learning_unit"))return published;
            if(sql.contains("course_catalog"))return Collections.singletonList(map("courseCode","course.test","title","Course"));
            if(sql.contains("course_section"))return sections;
            return Collections.emptyList();
        }
        @Override public Map<String,Object> one(String sql,Object...args){
            if(sql.contains("learning_attempt a")){evidenceArgs=args;return evidence;}
            if(sql.contains("question_submit s")){evidenceArgs=args;return evidence;}
            if(sql.contains("learning_course_position"))return coursePosition;
            if(sql.contains("learning_unit_position"))return unitPosition;
            if(sql.contains("contentHash FROM course_asset"))return hashes.containsKey(args[0])?map("contentHash",hashes.get(args[0])):null;
            if(sql.contains("pendingCount"))return map("pendingCount",1);
            return null;
        }
        @Override public int update(String sql,Object...args){mutations.add(sql);mutationArgs.add(args);return 1;}
    }
    @Test void unchangedReimportDoesNotWriteOrIncrementRevision() {
        MemoryRepository db=new MemoryRepository();CourseContentService s=new CourseContentService();s.db=db;
        ObjectNode original=object(parse(UNIT));original.put("revision",3);
        db.entities.put("learning_unit:unit.test",map("draftJson",json(original),"revision",3,"courseCode","course.test"));
        s.saveDraft("units",parse(UNIT),"course.test","L",42);
        assertTrue(db.mutations.isEmpty(),"unchanged stable identity must preserve revision and row ID");
        ObjectNode revised=object(parse(UNIT));revised.put("title","Changed");s.saveDraft("units",revised,"course.test","L",42);
        assertTrue(db.mutations.stream().anyMatch(x->x.contains("course_content_revision")));
        assertTrue(db.mutations.stream().noneMatch(x->x.contains("publishedJson=")),"draft updates cannot replace publication");
    }
    @Test void otherUserOrDifferentActivityAttemptCannotComplete() {
        MemoryRepository db=new MemoryRepository();db.published.add(map("publishedJson",UNIT));CourseService s=new CourseService();s.db=db;
        assertThrows(RuntimeException.class,()->s.complete("act.test",parse("{\"sourceUnitCode\":\"unit.test\",\"attemptId\":\"19\",\"correct\":true}"),77));
        assertArrayEquals(new Object[]{"19",77L,"q.test","unit.test","act.test"},db.evidenceArgs);
        assertTrue(db.mutations.isEmpty(),"rejected attempts must not write progress or mastery");
    }
    @Test void submissionCompletesFlowButFailureStillRecommendsReview() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
        db.progress.add(map("activityCode","act.test","completed",1,"passed",0));
        Map<String,Object> p=s.progress(parse(UNIT),42);
        assertEquals("COMPLETED",p.get("status"));assertEquals("REVIEW_RECOMMENDED",p.get("goalStatus"));
    }
    @Test void optionalActivitiesAreExcludedAndNoEvidenceIsNotChecked() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
        ObjectNode unit=object(parse(UNIT));ObjectNode optional=object(unit.path("activities").get(0));optional.put("activityCode","act.optional");optional.put("required",false);((com.fasterxml.jackson.databind.node.ArrayNode)unit.path("activities")).add(optional);
        Map<String,Object> p=s.progress(unit,42);assertEquals(1,p.get("requiredCount"));assertEquals("NOT_CHECKED",p.get("goalStatus"));assertEquals("NOT_STARTED",p.get("status"));
    }
    @Test void draftOnlyCatalogHasHonestAwaitingContentState() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
        assertEquals("AWAITING_CONTENT",s.courses(42).get(0).get("status"));
        assertThrows(RuntimeException.class,()->s.publishedUnit("unit.test"));
    }
    @Test void questionMappingsAreOptionalButStructuralUsageIsValidated() {
        CourseContentService s=new CourseContentService();s.db=new MemoryRepository();
        ((MemoryRepository)s.db).entities.put("course_section:sec.test",map("sectionCode","sec.test","courseCode","course.test"));
        ObjectNode unit=object(parse(UNIT));
        ((com.fasterxml.jackson.databind.node.ArrayNode)unit.path("nodeMappings")).addObject().put("nodeCode","os.a").put("role","TEACHES");
        ObjectNode mapping=((com.fasterxml.jackson.databind.node.ArrayNode)unit.putArray("questionMappings")).addObject();
        mapping.put("questionCode","q.test").put("usage","PRACTICE");
        assertDoesNotThrow(()->s.validateUnit(unit,"course.test",Collections.singleton("os.a")));
        mapping.put("usage","BOGUS");
        assertThrows(RuntimeException.class,()->s.validateUnit(unit,"course.test",Collections.singleton("os.a")));
    }

    @Test void publishingUnitRequiresMappedQuestionsToExistAndBePublished() {
        CourseContentService s=new CourseContentService();s.db=new MemoryRepository();
        ObjectNode unit=object(parse(UNIT));
        ((com.fasterxml.jackson.databind.node.ArrayNode)unit.path("nodeMappings")).addObject().put("nodeCode","os.a").put("role","TEACHES");
        ((com.fasterxml.jackson.databind.node.ArrayNode)unit.putArray("questionMappings")).addObject().put("questionCode","q.missing").put("usage","PRACTICE");
        assertThrows(RuntimeException.class,()->s.validatePublishedUnit(unit));
    }

    @Test void prerequisiteCycleAndUnsafeAttachmentsAreRejected() {
        CourseContentService s=new CourseContentService();s.db=new MemoryRepository();
        Map<String,Set<String>> edges=new HashMap<>();edges.put("os.a",Collections.singleton("os.b"));edges.put("os.b",Collections.singleton("os.a"));
        assertThrows(RuntimeException.class,()->s.visit("os.a",edges,new HashSet<>(),new HashSet<>()));
        assertThrows(RuntimeException.class,()->s.assetPath("assets/../secret.png"));
        assertThrows(RuntimeException.class,()->s.assetBytes(parse("{\"contentType\":\"image/svg+xml\",\"contentBase64\":\"PHN2Zz4=\"}")));
    }
    @Test void unitsUseCompleteSectionAncestryBeforeLocalOrder() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
        db.sections.add(map("sectionCode","sec.one","parentSectionCode",null,"sortOrder",1));
        db.sections.add(map("sectionCode","sec.one.last","parentSectionCode","sec.one","sortOrder",7));
        db.sections.add(map("sectionCode","sec.two","parentSectionCode",null,"sortOrder",2));
        db.sections.add(map("sectionCode","sec.two.first","parentSectionCode","sec.two","sortOrder",1));
        ObjectNode one=object(parse(UNIT));one.put("unitCode","unit.one.last");one.put("sectionCode","sec.one.last");one.put("sortOrder",99);
        ObjectNode two=object(parse(UNIT));two.put("unitCode","unit.two.first");two.put("sectionCode","sec.two.first");two.put("sortOrder",1);
        db.published.add(map("publishedJson",json(two)));db.published.add(map("publishedJson",json(one)));
        assertEquals("unit.one.last",text(s.publishedUnits("course.test").get(0),"unitCode"));
    }
    @Test void selectedActivityIsSavedBeforeCompletionAndReviewDoesNotReplaceMainline() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
        db.entities.put("learning_unit:unit.test",map("publishedJson",UNIT));
        assertEquals(false,s.selectActivity("unit.test",parse("{\"activityCode\":\"act.test\",\"mode\":\"REVIEW\"}"),42).get("mainlineUpdated"));
        assertTrue(db.mutations.stream().anyMatch(x->x.contains("learning_unit_position")));
        assertFalse(db.mutations.stream().anyMatch(x->x.contains("learning_course_position")));
        db.mutations.clear();s.selectActivity("unit.test",parse("{\"activityCode\":\"act.test\"}"),42);
        assertTrue(db.mutations.stream().anyMatch(x->x.contains("learning_course_position")));
        db.coursePosition=map("unitCode","unit.test","activityCode","act.test");db.published.add(map("publishedJson",UNIT));db.entities.put("course_catalog:course.test",map("title","Course"));
        assertEquals("act.test",s.resume("course.test",42).get("activityCode"));
    }
    @Test void wrongSourceUnitRejectedBeforeAnyAttemptLookupOrProgressWrite() {
        MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;db.published.add(map("publishedJson",UNIT));
        assertThrows(RuntimeException.class,()->s.complete("act.test",parse("{\"sourceUnitCode\":\"unit.other\",\"attemptId\":\"19\"}"),42));
        assertNull(db.evidenceArgs);assertTrue(db.mutations.isEmpty());
    }
    @Test void failedSubmitCompletesButFailedPassDoesNotAndBothReturnActualResult() {
        for(String rule:Arrays.asList("SUBMIT","PASS")) {
            MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;db.evidence=map("isCorrect",0);
            db.published.add(map("publishedJson",UNIT.replace("SUBMIT",rule)));
            Map<String,Object> result=s.complete("act.test",parse("{\"sourceUnitCode\":\"unit.test\",\"attemptId\":\"19\"}"),42);
            assertEquals("SUBMIT".equals(rule),result.get("completed"));assertEquals(false,result.get("passed"));assertEquals(rule,result.get("completionRule"));
        }
    }
    @Test void changedAssetBatchCreatesDraftRevisionWithoutReplacingPublishedSnapshot() {
        MemoryRepository db=new MemoryRepository();CourseContentService s=new CourseContentService();s.db=db;
        ObjectNode chunk=object(parse("{\"chunkCode\":\"chunk.test\",\"title\":\"Figure\",\"sectionCode\":\"sec.test\",\"chunkType\":\"FIGURE\",\"contentMd\":\"![figure](assets/a.png)\",\"nodeCodes\":[],\"assets\":[{\"path\":\"assets/a.png\"}],\"assetBatchCode\":\"batch.old\"}"));
        db.entities.put("learning_content_chunk:chunk.test",map("draftJson",json(chunk),"publishedJson",json(chunk),"courseCode","course.test","revision",1));
        ObjectNode next=object(chunk);next.put("assetBatchCode","batch.new");db.hashes.put("batch.old/assets/a.png","old");db.hashes.put("batch.new/assets/a.png","old");
        s.saveDraft("chunks",next,"course.test","J",42);assertTrue(db.mutations.isEmpty(),"same bytes keep immutable old batch without a revision");
        db.hashes.put("batch.new/assets/a.png","new");s.saveDraft("chunks",next,"course.test","J",42);
        int update=db.mutations.indexOf("UPDATE learning_content_chunk SET draftJson=?,revision=?,reviewStatus='DRAFT',userId=?,contributor=?,title=?,sectionCode=? WHERE chunkCode=?");
        assertTrue(update>=0);assertEquals("batch.new",text(parse(String.valueOf(db.mutationArgs.get(update)[0])),"assetBatchCode"));
        assertFalse(db.mutations.stream().anyMatch(x->x.contains("publishedJson=")));
    }
    @Test void pendingGraphBlocksPublicationAndGraphLabelsUseDisplayChapter() {
        MemoryRepository db=new MemoryRepository();CourseGraphSyncService graph=new CourseGraphSyncService();graph.db=db;
        assertThrows(RuntimeException.class,()->graph.requireSynced(Collections.singleton("os.test")));
        Map<String,Map<String,Object>> sections=new HashMap<>();sections.put("sec.chapter",map("title","计算机系统概述","sectionType","CHAPTER"));sections.put("sec.topic",map("title","概念","parentSectionCode","sec.chapter","sectionType","CONTENT"));
        assertEquals("计算机系统概述",graph.chapterTitle("sec.topic",sections));assertEquals("基础",graph.category(map("nodeType","CONCEPT","layer","CORE")));
        assertEquals("应用",graph.category(map("nodeType","IMPLEMENTATION","layer","IMPLEMENTATION")));
    }
    @Test void resourceConfigurationExposesRuntimeParametersWithoutMaintenanceAnswers() {
        CourseService s=new CourseService();s.db=new MemoryRepository();
        JsonNode config=s.resourceConfig(map("metadataJson","{\"resourceConfig\":{\"frames\":3,\"pageSequence\":[1,2,3],\"correctAnswer\":\"secret\"},\"answer\":\"secret\",\"contentChanges\":[]}"));
        assertEquals(3,config.path("frames").asInt());assertFalse(config.has("answer"));
        assertFalse(config.has("correctAnswer"));
        config=s.resourceConfig(map("metadataJson","{\"frames\":4,\"answer\":\"secret\"}"));assertEquals(4,config.path("frames").asInt());assertFalse(config.has("answer"));
    }
    @Test void experimentInfrastructureFailuresCannotCompleteSubmitOrPass() {
        for(String rule:Arrays.asList("SUBMIT","PASS"))for(int status:Arrays.asList(2,3)) {
            List<String> messages=status==3?Arrays.asList("Accepted","Wrong Answer","Compile Error","System Error","系统错误")
                    :Arrays.asList("System Error","系统错误"," system error ","sandbox unavailable");
            for(String message:messages) {
                MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
                db.published.add(map("publishedJson",UNIT.replace("ASSESSMENT","EXPERIMENT").replace("QUESTION","RESOURCE").replace("SUBMIT",rule)));
                db.evidence=map("status",status,"judgeInfo",json(map("message",message)));
                assertThrows(RuntimeException.class,()->s.complete("act.test",parse("{\"sourceUnitCode\":\"unit.test\",\"submissionId\":\"19\"}"),42),rule+" status="+status+" "+message);
                assertTrue(db.mutations.isEmpty(),"system failure must not mutate progress or resume position");
                assertArrayEquals(new Object[]{"19",42L,"q.test","unit.test","act.test"},db.evidenceArgs);
            }
        }
    }
    @Test void completedExperimentJudgementsKeepSubmitAndPassRules() {
        for(String rule:Arrays.asList("SUBMIT","PASS"))for(String message:Arrays.asList("Accepted","成功","Wrong Answer","Compile Error","编译错误")) {
            MemoryRepository db=new MemoryRepository();CourseService s=new CourseService();s.db=db;
            db.published.add(map("publishedJson",UNIT.replace("ASSESSMENT","EXPERIMENT").replace("QUESTION","RESOURCE").replace("SUBMIT",rule)));
            db.evidence=map("status",2,"judgeInfo",json(map("message",message)));
            Map<String,Object> result=s.complete("act.test",parse("{\"sourceUnitCode\":\"unit.test\",\"submissionId\":\"19\"}"),42);
            boolean accepted=Arrays.asList("Accepted","成功").contains(message);
            assertEquals(accepted,result.get("passed"));assertEquals("SUBMIT".equals(rule)||accepted,result.get("completed"));
        }
    }
}
