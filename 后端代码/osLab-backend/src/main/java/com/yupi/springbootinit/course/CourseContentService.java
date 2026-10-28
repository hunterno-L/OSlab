package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import java.util.*;
import java.security.MessageDigest;
import static com.yupi.springbootinit.course.CourseJson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Member packages enter drafts. A published snapshot is never overwritten by import. */
@Service
public class CourseContentService {
    private static final Logger LOG=LoggerFactory.getLogger(CourseContentService.class);
    @Resource CourseRepository db;
    @Resource CourseGraphSyncService graph;

    public Map<String,Object> preview(JsonNode request) {
        JsonNode pack = packageOf(request);
        List<Map<String,Object>> items = new ArrayList<>();
        List<Map<String,Object>> errors = new ArrayList<>();
        try { validate(pack, request.path("assets")); }
        catch (RuntimeException e) { errors.add(map("path","package","message",e.getMessage())); }
        String kind = text(pack,"packageType");
        String array = "KNOWLEDGE".equals(kind) ? "chunks" : "units";
        String table = "KNOWLEDGE".equals(kind) ? "learning_content_chunk" : "learning_unit";
        String codeKey = "KNOWLEDGE".equals(kind) ? "chunkCode" : "unitCode";
        for (JsonNode item : pack.path(array)) {
            String stableCode = text(item,codeKey);
            Map<String,Object> old = db.entity(table,codeKey,stableCode);
            String action = old == null ? "CREATE" : sameDraft(old,item)&&!("chunks".equals(array)&&assetChangesInPreview(old,item,pack,request.path("assets"))) ? "UNCHANGED" : "UPDATE";
            items.add(map("type",array,"code",stableCode,"action",action,"id",old==null?null:db.value(old,"id")));
        }
        if ("KNOWLEDGE".equals(kind)) for (JsonNode node : pack.path("nodes")) {
            Map<String,Object> old=db.entity("knowledge_node_registry","nodeCode",text(node,"nodeCode"));
            boolean same=old!=null&&text(node,"name").equals(db.value(old,"name"))&&text(node,"nodeType").equals(db.value(old,"nodeType"))&&text(node,"description").equals(db.value(old,"description"))&&text(node,"homeSectionCode").equals(db.value(old,"homeSectionCode"))&&text(node,"layer").equals(db.value(old,"layer"))&&node.path("aliases").equals(parse(db.value(old,"aliasesJson")))&&node.path("sourceRefs").equals(parse(db.value(old,"sourceRefsJson")));
            items.add(map("type","nodes","code",text(node,"nodeCode"),"action",old==null?"CREATE":same?"UNCHANGED":"UPDATE"));
        }
        if("KNOWLEDGE".equals(kind))for(JsonNode relation:pack.path("relations")) {
            String from=text(relation,"fromNodeCode"),to=text(relation,"toNodeCode"),type=text(relation,"relationType");
            if(Arrays.asList("CONTRASTS_WITH","RELATED_TO").contains(type)&&from.compareTo(to)>0){String t=from;from=to;to=t;}
            Map<String,Object> old=db.one("SELECT * FROM knowledge_node_relation WHERE fromNodeCode=? AND toNodeCode=? AND relationType=? AND isDelete=0",from,to,type);
            boolean same=old!=null&&text(relation,"reason").equals(db.value(old,"reason"))&&relation.path("sourceRef").equals(parse(db.value(old,"sourceRefJson")));
            items.add(map("type","relations","code",from+"→"+to+":"+type,"action",old==null?"CREATE":same?"UNCHANGED":"UPDATE"));
        }
        int created=0,updated=0,unchanged=0;
        for(Map<String,Object> item:items){String action=String.valueOf(item.get("action"));if("CREATE".equals(action))created++;else if("UPDATE".equals(action))updated++;else unchanged++;}
        return map("valid",errors.isEmpty(),"items",items,"errors",errors,"created",created,"updated",updated,"unchanged",unchanged);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> commit(JsonNode request, long userId) {
        JsonNode pack = packageOf(request);
        validate(pack,request.path("assets"));
        Map<String,Object> report = preview(request);
        if (!Boolean.TRUE.equals(report.get("valid"))) throw bad("导入检查未通过");
        storeAssets(pack,request.path("assets"));
        String course = code(pack,"courseCode"), contributor = required(pack,"contributor");
        if ("KNOWLEDGE".equals(text(pack,"packageType"))) {
            for (JsonNode node : pack.path("nodes")) saveNode(node);
            for (JsonNode chunk : pack.path("chunks")) {
                ObjectNode draft=object(chunk);draft.put("assetBatchCode",text(pack,"batchCode"));
                saveDraft("chunks",draft,course,contributor,userId);
            }
            for (JsonNode relation : pack.path("relations")) saveRelation(relation);
        } else for (JsonNode unit : pack.path("units")) saveDraft("units",unit,course,contributor,userId);
        String runCode = "import." + db.id();
        report.put("importRunCode",runCode);
        db.update("INSERT INTO course_import_run(id,importRunCode,batchCode,packageType,contributor,userId,resultJson) VALUES(?,?,?,?,?,?,?)",
                db.id(),runCode,code(pack,"batchCode"),text(pack,"packageType"),contributor,userId,json(report));
        if("KNOWLEDGE".equals(text(pack,"packageType"))) {
            db.update("INSERT INTO course_graph_sync(id,importRunCode) VALUES(?,?)",db.id(),runCode);
            report.put("graphSync","PENDING");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){try{graph.syncPending();}catch(RuntimeException e){LOG.warn("知识包已入库，图谱同步保留待重试: {}",runCode,e);}}
            });
        }
        return report;
    }

    JsonNode packageOf(JsonNode request) {
        if (request.has("package")) return request.get("package");
        if (request.has("content")) return parse(required(request,"content"));
        return request;
    }

    void validate(JsonNode pack, JsonNode assets) {
        if (!"oslab-authoring/1.0".equals(text(pack,"schemaVersion"))) throw bad("schemaVersion不受支持");
        String kind = required(pack,"packageType");
        if (!Arrays.asList("KNOWLEDGE","UNITS").contains(kind)) throw bad("只接收KNOWLEDGE或UNITS包；题目请用测评导入");
        code(pack,"batchCode"); required(pack,"contributor");
        String course = code(pack,"courseCode");
        if (!db.exists("course_catalog","courseCode",course)) throw bad("未知课程:"+course);
        Set<String> nodeCodes = new HashSet<>();
        for (Map<String,Object> n : db.rows("SELECT nodeCode FROM knowledge_node_registry WHERE isDelete=0")) nodeCodes.add(db.value(n,"nodeCode"));
        Set<String> seen = new HashSet<>();
        if ("KNOWLEDGE".equals(kind)) {
            array(pack,"nodes"); array(pack,"chunks"); array(pack,"relations");
            if(pack.path("nodes").isEmpty()&&pack.path("chunks").isEmpty()&&pack.path("relations").isEmpty())throw bad("空知识模板不能导入为完成批次");
            for (JsonNode n : pack.path("nodes")) {
                unique(seen,code(n,"nodeCode")); nodeCodes.add(text(n,"nodeCode"));
                required(n,"name"); required(n,"description"); section(n,"homeSectionCode",course);
                enumeration(n,"nodeType","CONCEPT","ALGORITHM","MECHANISM","IMPLEMENTATION");
                enumeration(n,"layer","CORE","IMPLEMENTATION");
                array(n,"aliases"); array(n,"sourceRefs");
                for(JsonNode s:n.path("sourceRefs")) source(s);
            }
            seen.clear();
            for(JsonNode c:pack.path("chunks")) {
                unique(seen,code(c,"chunkCode")); required(c,"title"); required(c,"contentMd"); section(c,"sectionCode",course);
                enumeration(c,"chunkType","DEFINITION","EXPLANATION","MECHANISM","ALGORITHM","WORKED_EXAMPLE","FIGURE","SUMMARY");
                source(c.path("sourceRef")); array(c,"nodeCodes"); array(c,"assets");
                if(c.path("nodeCodes").isEmpty())throw bad("教学块必须关联知识节点");
                for(JsonNode n:c.path("nodeCodes"))if(!nodeCodes.contains(n.asText()))throw bad("未知nodeCode:"+n.asText());
                for(JsonNode a:c.path("assets")) { assetPath(required(a,"path")); source(a.path("sourceRef")); }
                checkRevision("learning_content_chunk","chunkCode",c);
            }
            validateRelations(pack.path("relations"),nodeCodes);
        } else {
            array(pack,"units");
            if(pack.path("units").isEmpty())throw bad("学习单元包不能为空");
            Set<String> packageActivities=new HashSet<>();
            for(JsonNode u:pack.path("units")) {
                unique(seen,code(u,"unitCode")); validateUnit(u,course,nodeCodes);
                for(JsonNode a:u.path("activities"))unique(packageActivities,text(a,"activityCode"));
                checkRevision("learning_unit","unitCode",u);
            }
        }
        validateAssets(assets);
    }

    void checkRevision(String table,String key,JsonNode value) {
        if(!value.has("revision"))return;
        Map<String,Object> old=db.entity(table,key,text(value,key));
        if(old!=null && value.path("revision").asInt()!=((Number)old.get("revision")).intValue()) throw bad("版本冲突:"+text(value,key));
    }

    void validateUnit(JsonNode u,String course,Set<String> nodeCodes) {
        code(u,"unitCode"); required(u,"title"); section(u,"sectionCode",course);
        array(u,"objectives"); array(u,"nodeMappings"); array(u,"activities");
        for(JsonNode objective:u.path("objectives"))if(!objective.isTextual()||objective.asText().trim().isEmpty())throw bad("学习目标必须为非空文字");
        if(u.path("estimatedMinutes").asInt(0)<0)throw bad("estimatedMinutes不得为负");
        for(JsonNode n:u.path("nodeMappings")) {
            if(!nodeCodes.contains(code(n,"nodeCode")))throw bad("未知nodeCode:"+text(n,"nodeCode"));
            enumeration(n,"role","TEACHES","PREREQUISITE","EXTENSION");
        }
        // 单元配套题映射：可选字段；只表示练习关系，不进入必做活动或进度统计。
        if(u.has("questionMappings")&&!u.path("questionMappings").isArray())throw bad("questionMappings必须为数组");
        Set<String> mappedQuestions=new HashSet<>();
        for(JsonNode mapping:u.path("questionMappings")) {
            unique(mappedQuestions,code(mapping,"questionCode"));
            enumeration(mapping,"usage","PRACTICE","ASSESSMENT","REVIEW");
        }
        Set<String> activities=new HashSet<>();
        Map<String,Object> oldUnit=db.entity("learning_unit","unitCode",text(u,"unitCode"));
        Map<String,JsonNode> publishedActivities=new HashMap<>();
        if(oldUnit!=null&&oldUnit.get("publishedJson")!=null)for(JsonNode a:parse(db.value(oldUnit,"publishedJson")).path("activities"))publishedActivities.put(text(a,"activityCode"),a);
        for(JsonNode a:u.path("activities")) {
            unique(activities,code(a,"activityCode")); required(a,"title");
            Map<String,Object> owned=db.one("SELECT unitCode FROM learning_unit_activity WHERE activityCode=?",text(a,"activityCode"));
            if(owned!=null&&!text(u,"unitCode").equals(db.value(owned,"unitCode")))throw bad("activityCode已属于其他单元");
            String type=required(a,"activityType"),kind=required(a.path("reference"),"kind"),rule=required(a,"completionRule");
            code(a.path("reference"),"code");
            JsonNode published=publishedActivities.get(text(a,"activityCode"));
            if(published!=null&&(!published.path("reference").equals(a.path("reference"))||!text(published,"activityType").equals(type)))throw bad("替换已发布任务需分配新activityCode:"+text(a,"activityCode"));
            if(!a.path("required").isBoolean()||!a.path("sortOrder").isIntegralNumber())throw bad("活动required/sortOrder格式无效");
            boolean reading=Arrays.asList("READING","WORKED_EXAMPLE","SUMMARY").contains(type);
            if(reading&&!("CHUNK".equals(kind)&&"CONFIRM".equals(rule)))throw bad("阅读活动必须引用CHUNK并用CONFIRM");
            if("DEMO".equals(type)&&!("RESOURCE".equals(kind)&&"RUN_TO_END".equals(rule)))throw bad("演示必须引用RESOURCE并用RUN_TO_END");
            if(Arrays.asList("PRACTICE","ASSESSMENT").contains(type)&&!("QUESTION".equals(kind)&&Arrays.asList("SUBMIT","PASS").contains(rule)))throw bad("测评必须引用QUESTION并用SUBMIT/PASS");
            if("EXPERIMENT".equals(type)&&!("RESOURCE".equals(kind)&&Arrays.asList("SUBMIT","PASS").contains(rule)))throw bad("实验必须引用RESOURCE并用SUBMIT/PASS");
            if(!reading&&!Arrays.asList("DEMO","PRACTICE","ASSESSMENT","EXPERIMENT").contains(type))throw bad("未知活动类型");
            rejectHiddenAnswers(a.path("config"));
        }
    }
    void rejectHiddenAnswers(JsonNode value) {
        if(value.isObject())value.fields().forEachRemaining(e->{
            if(Arrays.asList("answer","correctanswer","analysis","judgecase","solution","referenceanswer").contains(e.getKey().toLowerCase(Locale.ROOT)))throw bad("活动配置不得包含隐藏答案:"+e.getKey());
            rejectHiddenAnswers(e.getValue());
        });else if(value.isArray())value.forEach(this::rejectHiddenAnswers);
    }
    void validateRelations(JsonNode relations,Set<String> nodes) {
        Map<String,Set<String>> edges=new HashMap<>();
        for(Map<String,Object> old:db.rows("SELECT fromNodeCode,toNodeCode FROM knowledge_node_relation WHERE relationType='REQUIRES' AND isDelete=0"))edges.computeIfAbsent(db.value(old,"fromNodeCode"),k->new HashSet<>()).add(db.value(old,"toNodeCode"));
        for(JsonNode r:relations) {
            String from=code(r,"fromNodeCode"),to=code(r,"toNodeCode");
            if(!nodes.contains(from)||!nodes.contains(to))throw bad("关系引用未知节点");
            if(from.equals(to))throw bad("关系不得自指");
            enumeration(r,"relationType","REQUIRES","CONTAINS","CONTRASTS_WITH","BASED_ON","IMPLEMENTS","RELATED_TO");
            required(r,"reason");source(r.path("sourceRef"));
            if("REQUIRES".equals(text(r,"relationType")))edges.computeIfAbsent(from,k->new HashSet<>()).add(to);
        }
        Set<String> done=new HashSet<>(),visiting=new HashSet<>();
        for(String n:edges.keySet()) visit(n,edges,done,visiting);
    }
    void visit(String n,Map<String,Set<String>> edges,Set<String> done,Set<String> visiting) {
        if(done.contains(n))return;
        if(!visiting.add(n))throw bad("REQUIRES存在前置环:"+n);
        for(String to:edges.getOrDefault(n,Collections.emptySet()))visit(to,edges,done,visiting);
        visiting.remove(n);done.add(n);
    }
    void source(JsonNode s) {
        String code=code(s,"sourceCode");
        if(!db.exists("course_source","sourceCode",code))throw bad("未知sourceCode:"+code);
        enumeration(s,"origin","TEXTBOOK","ADAPTED","SELF_AUTHORED","SELF_AUTHORED_SAMPLE","EXTERNAL_QUESTION","EXTERNAL_DEMO","TOC");
        required(s,"locator");
        if(s.hasNonNull("pageStart")&&s.path("pageStart").asInt()<=0)throw bad("pageStart必须为正");
        if(s.hasNonNull("pageEnd")&&s.path("pageEnd").asInt()<s.path("pageStart").asInt())throw bad("页码范围无效");
        if(s.hasNonNull("sectionCode")&&!db.exists("course_section","sectionCode",text(s,"sectionCode")))throw bad("出处目录不存在");
    }
    void section(JsonNode n,String key,String course) {
        Map<String,Object> section=db.entity("course_section","sectionCode",code(n,key));
        if(section==null||!course.equals(db.value(section,"courseCode")))throw bad(key+"不属于课程");
    }
    void array(JsonNode n,String key) { if(!n.path(key).isArray())throw bad(key+"必须为数组"); }
    void unique(Set<String> set,String value) { if(!set.add(value))throw bad("重复编号:"+value); }
    void enumeration(JsonNode n,String key,String... values) { if(!Arrays.asList(values).contains(required(n,key)))throw bad(key+"值无效"); }

    ObjectNode normalized(JsonNode n) { ObjectNode out=object(n);out.remove(Arrays.asList("revision","status","courseCode","reviewStatus","publishedRevision","assetBatchCode","id","hasPublishedVersion"));return out; }
    boolean sameDraft(Map<String,Object> old,JsonNode n) { return normalized(parse(db.value(old,"draftJson"))).equals(normalized(n)); }
    boolean assetChangesInPreview(Map<String,Object> old,JsonNode item,JsonNode pack,JsonNode uploads) {
        String batch=text(parse(db.value(old,"draftJson")),"assetBatchCode");
        for(JsonNode asset:item.path("assets"))for(JsonNode upload:uploads)if(text(asset,"path").equals(text(upload,"path"))) {
            Map<String,Object> previous=db.one("SELECT contentHash FROM course_asset WHERE assetPath=?",batch+"/"+text(asset,"path"));
            if(previous==null||!assetHash(assetBytes(upload)).equals(db.value(previous,"contentHash")))return true;
        }
        return false;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> saveUnit(String unitCode,JsonNode request,long userId) {
        ObjectNode n=object(request);n.put("unitCode",unitCode);
        String course=code(n,"courseCode");
        Set<String> codes=new HashSet<>();for(Map<String,Object> r:db.rows("SELECT nodeCode FROM knowledge_node_registry WHERE isDelete=0"))codes.add(db.value(r,"nodeCode"));
        validateUnit(n,course,codes);checkRevision("learning_unit","unitCode",n);
        saveDraft("units",n,course,"L",userId);return manage("units",unitCode);
    }
    void saveDraft(String type,JsonNode input,String course,String contributor,long userId) {
        boolean chunk="chunks".equals(type);String table=chunk?"learning_content_chunk":"learning_unit",key=chunk?"chunkCode":"unitCode";
        ObjectNode n=normalized(input);String stableCode=code(n,key);
        Map<String,Object> old=db.entity(table,key,stableCode);
        String assetBatch=chunk?effectiveAssetBatch(input,old):"";
        boolean sameAssets=!chunk||old!=null&&assetBatch.equals(text(parse(db.value(old,"draftJson")),"assetBatchCode"));
        if(old!=null&&sameDraft(old,n)&&sameAssets)return;
        int revision=old==null?1:((Number)old.get("revision")).intValue()+1;
        n.put("courseCode",course);n.put("revision",revision);
        if(chunk&&!assetBatch.isEmpty())n.put("assetBatchCode",assetBatch);
        if(old==null) {
            if(chunk)db.update("INSERT INTO learning_content_chunk(id,chunkCode,courseCode,sectionCode,title,chunkType,contentMd,revision,draftJson,userId,contributor) VALUES(?,?,?,?,?,?,?,?,?,?,?)",db.id(),stableCode,course,text(n,"sectionCode"),text(n,"title"),text(n,"chunkType"),text(n,"contentMd"),revision,json(n),userId,contributor);
            else db.update("INSERT INTO learning_unit(id,unitCode,courseCode,sectionCode,title,sortOrder,estimatedMinutes,revision,draftJson,userId,contributor) VALUES(?,?,?,?,?,?,?,?,?,?,?)",db.id(),stableCode,course,text(n,"sectionCode"),text(n,"title"),n.path("sortOrder").asInt(),n.path("estimatedMinutes").asInt(),revision,json(n),userId,contributor);
        } else {
            if(!course.equals(db.value(old,"courseCode")))throw bad("稳定编号不能移入其他课程");
            db.update("UPDATE "+table+" SET draftJson=?,revision=?,reviewStatus='DRAFT',userId=?,contributor=?,title=?,sectionCode=? WHERE "+key+"=?",json(n),revision,userId,contributor,text(n,"title"),text(n,"sectionCode"),stableCode);
            if(chunk)db.update("UPDATE learning_content_chunk SET chunkType=?,contentMd=? WHERE chunkCode=?",text(n,"chunkType"),text(n,"contentMd"),stableCode);
            else db.update("UPDATE learning_unit SET sortOrder=?,estimatedMinutes=? WHERE unitCode=?",n.path("sortOrder").asInt(),n.path("estimatedMinutes").asInt(),stableCode);
        }
        db.update("INSERT INTO course_content_revision(id,entityType,entityCode,revision,snapshotJson,authorId) VALUES(?,?,?,?,?,?)",db.id(),type,stableCode,revision,json(n),userId);
        if(chunk) {
            db.update("DELETE FROM learning_content_chunk_node WHERE chunkCode=?",stableCode);
            for(JsonNode node:n.path("nodeCodes"))db.update("INSERT INTO learning_content_chunk_node(id,chunkCode,nodeCode) VALUES(?,?,?)",db.id(),stableCode,node.asText());
        } else {
            // Relational rows are editable drafts; student reads always use publishedJson.
            db.update("UPDATE learning_unit_activity SET isDelete=1 WHERE unitCode=?",stableCode);
            db.update("DELETE FROM learning_unit_node WHERE unitCode=?",stableCode);
            for(JsonNode node:n.path("nodeMappings"))db.update("INSERT INTO learning_unit_node(id,unitCode,nodeCode,role) VALUES(?,?,?,?)",db.id(),stableCode,text(node,"nodeCode"),text(node,"role"));
            for(JsonNode a:n.path("activities"))db.update("INSERT INTO learning_unit_activity(id,activityCode,unitCode,activityType,sortOrder,required,referenceKind,referenceCode,completionRule,configJson) VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE activityType=VALUES(activityType),sortOrder=VALUES(sortOrder),required=VALUES(required),referenceKind=VALUES(referenceKind),referenceCode=VALUES(referenceCode),completionRule=VALUES(completionRule),configJson=VALUES(configJson),isDelete=0",db.id(),text(a,"activityCode"),stableCode,text(a,"activityType"),a.path("sortOrder").asInt(),a.path("required").asBoolean(),text(a.path("reference"),"kind"),text(a.path("reference"),"code"),text(a,"completionRule"),json(a.path("config")));
        }
    }
    String effectiveAssetBatch(JsonNode incoming,Map<String,Object> old) {
        String proposed=text(incoming,"assetBatchCode");if(old==null)return proposed;
        JsonNode previous=parse(db.value(old,"draftJson"));String current=text(previous,"assetBatchCode");
        if(proposed.isEmpty()||proposed.equals(current))return current;
        if(incoming.path("assets").isEmpty())return current.isEmpty()?proposed:current;
        boolean allIncoming=true,allCurrent=true,same=true;
        for(JsonNode asset:incoming.path("assets")) {
            String path=text(asset,"path");
            Map<String,Object> next=db.one("SELECT contentHash FROM course_asset WHERE assetPath=?",proposed+"/"+path);
            Map<String,Object> before=db.one("SELECT contentHash FROM course_asset WHERE assetPath=?",current+"/"+path);
            allIncoming&=next!=null;allCurrent&=before!=null;
            same&=next!=null&&before!=null&&db.value(next,"contentHash").equals(db.value(before,"contentHash"));
        }
        if(allCurrent&&(!allIncoming||same))return current;
        return proposed;
    }
    void saveNode(JsonNode n) {
        db.update("INSERT INTO knowledge_node_registry(id,nodeCode,name,nodeType,description,aliasesJson,homeSectionCode,sourceRefsJson,layer) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),nodeType=VALUES(nodeType),description=VALUES(description),aliasesJson=VALUES(aliasesJson),homeSectionCode=VALUES(homeSectionCode),sourceRefsJson=VALUES(sourceRefsJson),layer=VALUES(layer),isDelete=0",db.id(),text(n,"nodeCode"),text(n,"name"),text(n,"nodeType"),text(n,"description"),json(n.path("aliases")),text(n,"homeSectionCode"),json(n.path("sourceRefs")),text(n,"layer"));
    }
    void saveRelation(JsonNode n) {
        String from=text(n,"fromNodeCode"),to=text(n,"toNodeCode"),type=text(n,"relationType");
        if(Arrays.asList("CONTRASTS_WITH","RELATED_TO").contains(type)&&from.compareTo(to)>0){String temp=from;from=to;to=temp;}
        db.update("INSERT INTO knowledge_node_relation(id,fromNodeCode,toNodeCode,relationType,reason,sourceRefJson) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE reason=VALUES(reason),sourceRefJson=VALUES(sourceRefJson),isDelete=0",db.id(),from,to,type,text(n,"reason"),json(n.path("sourceRef")));
    }
    public Map<String,Object> manage(String type,String stableCode) {
        String table=table(type),key="chunks".equals(type)?"chunkCode":"unitCode";
        Map<String,Object> row=db.entity(table,key,stableCode);if(row==null)throw bad("内容不存在");
        Map<String,Object> out=MAPPER.convertValue(parse(db.value(row,"draftJson")),Map.class);
        out.put("status",db.value(row,"reviewStatus"));out.put("revision",row.get("revision"));out.put("id",db.value(row,"id"));
        out.put("hasPublishedVersion",row.get("publishedJson")!=null);return out;
    }
    String table(String type) { if("chunks".equals(type))return "learning_content_chunk";if("units".equals(type))return "learning_unit";throw bad("未知内容类型"); }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> review(String type,String stableCode,JsonNode request,long userId) {
        String table=table(type),key="chunks".equals(type)?"chunkCode":"unitCode";
        Map<String,Object> row=db.one("SELECT * FROM "+table+" WHERE "+key+"=? AND isDelete=0 FOR UPDATE",stableCode);
        if(row==null)throw bad("内容不存在");
        String action=required(request,"action"),status=db.value(row,"reviewStatus"),next;
        if("SUBMIT".equals(action)&&Arrays.asList("DRAFT","REJECTED").contains(status))next="SUBMITTED";
        else if("APPROVE".equals(action)&&"SUBMITTED".equals(status))next="APPROVED";
        else if("REJECT".equals(action)&&"SUBMITTED".equals(status)){required(request,"reason");next="REJECTED";}
        else if("PUBLISH".equals(action)&&"APPROVED".equals(status))next="PUBLISHED";
        else throw bad("当前状态不允许该审核动作:"+status+"→"+action);
        if("PUBLISHED".equals(next)) {
            JsonNode snapshot=parse(db.value(row,"draftJson"));
            if("units".equals(type))validatePublishedUnit(snapshot);
            else validateChunkAssets(snapshot);
            Set<String> nodeCodes=new HashSet<>();
            if("units".equals(type)) {
                for(JsonNode n:snapshot.path("nodeMappings"))nodeCodes.add(text(n,"nodeCode"));
                for(JsonNode activity:snapshot.path("activities")) {
                    String kind=text(activity.path("reference"),"kind"),reference=text(activity.path("reference"),"code");
                    if("CHUNK".equals(kind)) {
                        Map<String,Object> chunk=db.entity("learning_content_chunk","chunkCode",reference);
                        for(JsonNode code:parse(db.value(chunk,"publishedJson")).path("nodeCodes"))nodeCodes.add(code.asText());
                    }else if("QUESTION".equals(kind))for(Map<String,Object> n:db.rows("SELECT n.nodeCode FROM learning_question_node n JOIN learning_question q ON q.id=n.questionId WHERE q.questionCode=? AND n.nodeCode IS NOT NULL",reference))nodeCodes.add(db.value(n,"nodeCode"));
                    else if("EXPERIMENT".equals(text(activity,"activityType")))for(Map<String,Object> n:db.rows("SELECT n.nodeCode FROM experiment_node n JOIN course_resource r ON r.questionId=n.questionId WHERE r.resourceCode=? AND r.isDelete=0",reference))nodeCodes.add(db.value(n,"nodeCode"));
                }
            }
            else for(JsonNode n:snapshot.path("nodeCodes"))nodeCodes.add(n.asText());
            graph.requireSynced(nodeCodes);
            db.update("UPDATE "+table+" SET publishedJson=draftJson WHERE "+key+"=?",stableCode);
        }
        db.update("UPDATE "+table+" SET reviewStatus=? WHERE "+key+"=?",next,stableCode);
        db.update("INSERT INTO course_content_review(id,entityType,entityCode,revision,action,reviewerId,reason) VALUES(?,?,?,?,?,?,?)",db.id(),type,stableCode,row.get("revision"),action,userId,text(request,"reason"));
        return manage(type,stableCode);
    }
    void validatePublishedUnit(JsonNode u) {
        if(u.path("objectives").isEmpty()||u.path("activities").isEmpty())throw bad("发布需要可验证目标与实际活动");
        for(JsonNode mapping:u.path("questionMappings")) {
            String questionCode=text(mapping,"questionCode");
            Map<String,Object> question=db.one("SELECT id FROM learning_question WHERE questionCode=? AND reviewStatus='PUBLISHED' AND isDelete=0",questionCode);
            if(question==null)throw bad("配套题目尚未发布:"+questionCode);
        }
        boolean hasRequired=false;
        for(JsonNode a:u.path("activities")) {
            rejectHiddenAnswers(a.path("config"));
            hasRequired|=a.path("required").asBoolean();
            String kind=text(a.path("reference"),"kind"),code=text(a.path("reference"),"code");Map<String,Object> ref;
            if("CHUNK".equals(kind)) { ref=db.entity("learning_content_chunk","chunkCode",code);if(ref==null||ref.get("publishedJson")==null)throw bad("教学块尚未发布:"+code); }
            else if("QUESTION".equals(kind)){ref=db.one("SELECT id FROM learning_question WHERE questionCode=? AND reviewStatus='PUBLISHED' AND isDelete=0",code);if(ref==null)throw bad("题目尚未发布:"+code);}
            else {ref=db.one("SELECT * FROM course_resource WHERE resourceCode=? AND status='PUBLISHED' AND isDelete=0",code);if(ref==null)throw bad("资源尚未启用:"+code);if("EXPERIMENT".equals(text(a,"activityType"))&&ref.get("questionId")==null)throw bad("实验未绑定原题ID");}
        }
        if(!hasRequired)throw bad("发布需要至少一个必需活动");
    }
    void validateChunkAssets(JsonNode c) {
        String origin=text(c.path("sourceRef"),"origin");
        if(Arrays.asList("SELF_AUTHORED_SAMPLE","TOC").contains(origin))throw bad("协议示例或目录不能作为正式正文发布");
        for(JsonNode a:c.path("assets")) {
            String path=text(c,"assetBatchCode")+"/"+required(a,"path");
            if(db.one("SELECT id FROM course_asset WHERE assetPath=?",path)==null)throw bad("附件尚未上传:"+path);
        }
    }
    void assetPath(String path) { if(path.length()>300||!path.startsWith("assets/")||path.contains("..")||path.contains("\\")||path.contains(":")||path.contains("?")||path.contains("#"))throw bad("附件必须为assets/下的安全相对路径"); }
    public void validateAssets(JsonNode assets) {
        if(assets.isMissingNode()||assets.isNull())return;
        if(!assets.isArray()||assets.size()>100)throw bad("附件数量超限");int total=0;
        Set<String> paths=new HashSet<>();
        for(JsonNode a:assets) {
            String path=required(a,"path");assetPath(path);unique(paths,path);
            byte[] bytes=assetBytes(a);total+=bytes.length;
            if(total>20*1024*1024)throw bad("附件总大小超过20MiB");
        }
    }
    byte[] assetBytes(JsonNode a) {
        String mime=required(a,"contentType");
        if(!Arrays.asList("image/png","image/jpeg","image/webp","image/gif").contains(mime))throw bad("仅接收图片附件");
        byte[] b;try{b=Base64.getDecoder().decode(required(a,"contentBase64"));}catch(IllegalArgumentException e){throw bad("附件Base64无效");}
        if(b.length<12||b.length>5*1024*1024)throw bad("单个图片须小于5MiB");
        boolean valid="image/png".equals(mime)&&b[0]==(byte)137&&b[1]==80&&b[2]==78&&b[3]==71
                ||"image/jpeg".equals(mime)&&b[0]==(byte)255&&b[1]==(byte)216&&b[2]==(byte)255
                ||"image/gif".equals(mime)&&b[0]==71&&b[1]==73&&b[2]==70
                ||"image/webp".equals(mime)&&b[0]==82&&b[1]==73&&b[2]==70&&b[3]==70&&b[8]==87&&b[9]==69&&b[10]==66&&b[11]==80;
        if(!valid)throw bad("图片内容与类型不一致");return b;
    }
    public void storeAssets(JsonNode pack,JsonNode assets) {
        for(JsonNode a:assets) {
            byte[] bytes=assetBytes(a);String hash=assetHash(bytes);
            String path=code(pack,"batchCode")+"/"+required(a,"path");
            Map<String,Object> old=db.one("SELECT contentHash FROM course_asset WHERE batchCode=? AND assetPath=?",text(pack,"batchCode"),path);
            if(old!=null&&!hash.equals(db.value(old,"contentHash")))throw bad("已归档附件不可覆盖，请使用新批次或新文件名:"+path);
            db.update("INSERT IGNORE INTO course_asset(id,batchCode,assetPath,contentType,contentHash,contentBytes) VALUES(?,?,?,?,?,?)",db.id(),text(pack,"batchCode"),path,text(a,"contentType"),hash,bytes);
        }
    }
    String assetHash(byte[] bytes) {try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
