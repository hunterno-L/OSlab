package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.learning.JudgeResultClassifier;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;
import static com.yupi.springbootinit.course.CourseJson.*;

@Service
public class CourseService {
    @Resource CourseRepository db;

    public List<Map<String,Object>> courses(long userId) {
        List<Map<String,Object>> out=new ArrayList<>();
        Set<String> registryCodes=new HashSet<>();
        for(Map<String,Object> r:db.rows("SELECT nodeCode FROM knowledge_node_registry WHERE isDelete=0")) registryCodes.add(db.value(r,"nodeCode"));
        for(Map<String,Object> row:db.rows("SELECT * FROM course_catalog WHERE isDelete=0 ORDER BY courseCode")) {
            String code=db.value(row,"courseCode");List<JsonNode> units=publishedUnits(code);int completed=0;
            // hide demo courses whose units reference no registered node
            boolean hasRegisteredNode=false;
            for(JsonNode u:units){JsonNode nm=u.get("nodeMappings");if(nm==null)continue;for(JsonNode m:nm){String nc=m.path("nodeCode").asText("");if(!nc.isEmpty()&&registryCodes.contains(nc)){hasRegisteredNode=true;break;}}if(hasRegisteredNode)break;}
            if(!hasRegisteredNode)continue;
            for(JsonNode u:units)if("COMPLETED".equals(progress(u,userId).get("status")))completed++;
            out.add(map("courseCode",code,"title",row.get("title"),"description",row.get("description"),"status",units.isEmpty()?"AWAITING_CONTENT":"AVAILABLE","publishedUnitCount",units.size(),"completedUnitCount",completed,"progressPercent",units.isEmpty()?0:Math.round(100.0*completed/units.size())));
        }
        return out;
    }
    List<JsonNode> publishedUnits(String course) {
        List<JsonNode> units=new ArrayList<>();
        for(Map<String,Object> row:db.rows("SELECT publishedJson FROM learning_unit WHERE courseCode=? AND publishedJson IS NOT NULL AND isDelete=0",course))units.add(parse(db.value(row,"publishedJson")));
        sortUnits(course,units);return units;
    }
    void sortUnits(String course,List<JsonNode> units) {
        Map<String,Map<String,Object>> sections=new HashMap<>();
        for(Map<String,Object> s:db.rows("SELECT sectionCode,parentSectionCode,sortOrder FROM course_section WHERE courseCode=? AND isDelete=0",course))sections.put(db.value(s,"sectionCode"),s);
        units.sort((a,b)->{
            int compared=sectionPath(text(a,"sectionCode"),sections).compareTo(sectionPath(text(b,"sectionCode"),sections));
            if(compared!=0)return compared;
            compared=Integer.compare(a.path("sortOrder").asInt(),b.path("sortOrder").asInt());
            return compared!=0?compared:text(a,"unitCode").compareTo(text(b,"unitCode"));
        });
    }
    String sectionPath(String code,Map<String,Map<String,Object>> sections) {
        LinkedList<String> path=new LinkedList<>();Set<String> seen=new HashSet<>();String cursor=code;
        while(!cursor.isEmpty()&&seen.add(cursor)) {
            Map<String,Object> section=sections.get(cursor);
            if(section==null){path.addFirst("9999999999:"+cursor);break;}
            int order=((Number)section.get("sortOrder")).intValue();
            path.addFirst(String.format(Locale.ROOT,"%010d:%s",(long)order-Integer.MIN_VALUE,cursor));
            cursor=db.value(section,"parentSectionCode");
        }
        return String.join("/",path);
    }
    JsonNode publishedUnit(String unitCode) {
        Map<String,Object> row=db.entity("learning_unit","unitCode",unitCode);
        if(row==null||row.get("publishedJson")==null)throw bad("学习单元尚未发布");return parse(db.value(row,"publishedJson"));
    }
    Map<String,Object> course(String code) { Map<String,Object> r=db.entity("course_catalog","courseCode",code);if(r==null)throw bad("课程不存在");return r; }
    public Map<String,Object> outline(String code,long userId,boolean manage) {
        Map<String,Object> course=course(code);
        List<Map<String,Object>> sections=db.rows("SELECT sectionCode,parentSectionCode,title,textbookNumber,sectionType,sortOrder,sourceCode,pageStart FROM course_section WHERE courseCode=? AND isDelete=0 ORDER BY sortOrder,sectionCode",code);
        Map<String,Map<String,Object>> index=new LinkedHashMap<>();List<Map<String,Object>> roots=new ArrayList<>();
        for(Map<String,Object> s:sections){s.put("children",new ArrayList<>());s.put("units",new ArrayList<>());index.put(db.value(s,"sectionCode"),s);}
        for(Map<String,Object> s:sections){Map<String,Object> parent=index.get(db.value(s,"parentSectionCode"));if(parent==null)roots.add(s);else ((List)parent.get("children")).add(s);}
        List<JsonNode> units=manage?draftUnits(code):publishedUnits(code);int completed=0;
        for(JsonNode u:units){Map<String,Object> s=index.get(text(u,"sectionCode"));Map<String,Object> summary=summary(u,userId);if(s!=null)((List)s.get("units")).add(summary);if("COMPLETED".equals(((Map)summary.get("progress")).get("status")))completed++;}
        return map("courseCode",code,"title",course.get("title"),"description",course.get("description"),"sections",roots,"progress",map("publishedUnitCount",manage?publishedUnits(code).size():units.size(),"completedUnitCount",completed,"percent",units.isEmpty()?0:Math.round(100.0*completed/units.size())));
    }
    List<JsonNode> draftUnits(String course) {
        List<JsonNode> out=new ArrayList<>();for(Map<String,Object> r:db.rows("SELECT draftJson,reviewStatus FROM learning_unit WHERE courseCode=? AND isDelete=0 ORDER BY sortOrder",course)){ObjectNode n=object(parse(db.value(r,"draftJson")));n.put("status",db.value(r,"reviewStatus"));out.add(n);}sortUnits(course,out);return out;
    }
    Map<String,Object> summary(JsonNode u,long userId) {
        List<Map<String,Object>> mappings=new ArrayList<>();
        for(JsonNode n:u.path("nodeMappings")) {
            Map<String,Object> node=db.entity("knowledge_node_registry","nodeCode",text(n,"nodeCode"));
            mappings.add(map("nodeCode",text(n,"nodeCode"),"role",text(n,"role"),"name",node==null?null:db.value(node,"name")));
        }
        return map("unitCode",text(u,"unitCode"),"courseCode",text(u,"courseCode"),"sectionCode",text(u,"sectionCode"),"title",text(u,"title"),"sortOrder",u.path("sortOrder").asInt(),"estimatedMinutes",u.path("estimatedMinutes").asInt(),"status",text(u,"status"),"nodeMappings",mappings,"progress",progress(u,userId));
    }

    public Map<String,Object> resume(String course,long userId) {
        course(course);List<JsonNode> units=publishedUnits(course);
        if(units.isEmpty())return map("courseCode",course,"unitCode",null,"activityCode",null,"reason","AWAITING_CONTENT");
        Map<String,Object> pos=db.one("SELECT * FROM learning_course_position WHERE userId=? AND courseCode=? AND isDelete=0",userId,course);
        if(pos!=null)for(JsonNode u:units)if(text(u,"unitCode").equals(db.value(pos,"unitCode"))&&!"COMPLETED".equals(progress(u,userId).get("status"))) {
            Map<String,Object> result=resumeAt(course,u,userId,"CONTINUE");
            for(JsonNode a:u.path("activities"))if(text(a,"activityCode").equals(db.value(pos,"activityCode")))result.put("activityCode",db.value(pos,"activityCode"));
            return result;
        }
        for(JsonNode u:units)if(!"COMPLETED".equals(progress(u,userId).get("status")))return resumeAt(course,u,userId,pos==null?"FIRST_UNIT":"CONTINUE");
        return resumeAt(course,units.get(units.size()-1),userId,"COURSE_COMPLETED");
    }
    Map<String,Object> resumeAt(String course,JsonNode u,long userId,String reason) {return map("courseCode",course,"unitCode",text(u,"unitCode"),"activityCode",nextActivity(u,userId),"reason",reason);}
    String nextActivity(JsonNode u,long userId) {Set<String> completed=completed(u,userId);for(JsonNode a:activities(u))if(a.path("required").asBoolean()&&!completed.contains(text(a,"activityCode")))return text(a,"activityCode");return u.path("activities").isEmpty()?null:text(activities(u).get(activities(u).size()-1),"activityCode");}
    List<JsonNode> activities(JsonNode u){List<JsonNode> out=new ArrayList<>();u.path("activities").forEach(out::add);out.sort(Comparator.comparingInt(n->n.path("sortOrder").asInt()));return out;}
    Set<String> completed(JsonNode u,long userId){Set<String> out=new HashSet<>();for(Map<String,Object> p:progressRows(u,userId))if(((Number)p.get("completed")).intValue()==1)out.add(db.value(p,"activityCode"));return out;}
    List<Map<String,Object>> progressRows(JsonNode u,long userId){return db.rows("SELECT * FROM learning_activity_progress WHERE userId=? AND unitCode=? AND isDelete=0",userId,text(u,"unitCode"));}
    public Map<String,Object> progress(JsonNode u,long userId) {
        Map<String,Map<String,Object>> rows=new HashMap<>();for(Map<String,Object> p:progressRows(u,userId))rows.put(db.value(p,"activityCode"),p);
        int required=0,done=0,seen=0,checks=0,passed=0;boolean failed=false;
        for(JsonNode a:u.path("activities")) {
            Map<String,Object> p=rows.get(text(a,"activityCode"));boolean complete=p!=null&&((Number)p.get("completed")).intValue()==1;
            if(p!=null)seen++;
            if(a.path("required").asBoolean()){required++;if(complete)done++;}
            if(Arrays.asList("PRACTICE","ASSESSMENT","EXPERIMENT").contains(text(a,"activityType"))&&a.path("required").asBoolean()) {
                checks++;if(p!=null&&p.get("passed")!=null){if(((Number)p.get("passed")).intValue()==1)passed++;else failed=true;}
            }
        }
        return map("status",required>0&&done==required?"COMPLETED":seen>0?"IN_PROGRESS":"NOT_STARTED","goalStatus",failed?"REVIEW_RECOMMENDED":checks>0&&checks==passed?"PASSED":"NOT_CHECKED","requiredCount",required,"completedCount",done,"percent",required==0?0:Math.round(100.0*done/required));
    }
    public Map<String,Object> unit(String code,long userId) {
        JsonNode u=publishedUnit(code);ObjectNode out=object(u);out.retain("unitCode","courseCode","sectionCode","title","objectives","sortOrder","estimatedMinutes","revision","nodeMappings","questionMappings");ArrayNode acts=MAPPER.createArrayNode();
        Map<String,Map<String,Object>> progress=new HashMap<>();for(Map<String,Object> p:progressRows(u,userId))progress.put(db.value(p,"activityCode"),p);
        for(JsonNode a:activities(u)) {
            ObjectNode activity=object(a);activity.retain("activityCode","title","activityType","sortOrder","required","reference","config","completionRule");stripHidden(activity.path("config"));Map<String,Object> p=progress.get(text(a,"activityCode"));
            activity.put("completed",p!=null&&((Number)p.get("completed")).intValue()==1);
            if(p==null||p.get("passed")==null)activity.putNull("passed");else activity.put("passed",((Number)p.get("passed")).intValue()==1);
            String kind=text(a.path("reference"),"kind"),ref=text(a.path("reference"),"code");
            if("CHUNK".equals(kind)) {
                Map<String,Object> c=db.entity("learning_content_chunk","chunkCode",ref);
                if(c!=null&&c.get("publishedJson")!=null){ObjectNode content=object(parse(db.value(c,"publishedJson")));String md=text(content,"contentMd");for(JsonNode asset:content.path("assets")){String path=text(asset,"path");String url="/api/courses/assets/"+assetId(text(content,"assetBatchCode")+"/"+path);md=md.replace("("+path+")","("+url+")");((ObjectNode)asset).put("url",url);}content.retain("chunkCode","title","chunkType","contentMd","sourceRef","assets","revision","nodeCodes");content.put("contentMd",md);activity.set("content",content);}
            } else if("QUESTION".equals(kind)) {
                Map<String,Object> q=db.one("SELECT id FROM learning_question WHERE questionCode=? AND reviewStatus='PUBLISHED' AND isDelete=0",ref);if(q!=null)activity.put("questionId",db.value(q,"id"));
            } else {
                Map<String,Object> r=db.entity("course_resource","resourceCode",ref);
                if(r!=null&&"PUBLISHED".equals(db.value(r,"status"))) {
                    JsonNode resourceConfig=resourceConfig(r);
                    activity.set("resource",MAPPER.valueToTree(map("resourceCode",ref,"resourceType",r.get("resourceType"),"title",r.get("title"),"questionId",r.get("questionId")==null?null:db.value(r,"questionId"),"adapterKey",r.get("adapterKey"),"algorithmKey",r.get("algorithmKey"),"resourceConfig",resourceConfig)));
                }
            }
            acts.add(activity);
        }
        for(JsonNode n:out.path("nodeMappings")){Map<String,Object> row=db.entity("knowledge_node_registry","nodeCode",text(n,"nodeCode"));if(row!=null)((ObjectNode)n).put("name",db.value(row,"name"));}
        out.set("activities",acts);out.set("progress",MAPPER.valueToTree(progress(u,userId)));out.put("currentActivityCode",selectedActivity(u,userId));
        List<JsonNode> units=publishedUnits(text(u,"courseCode"));String previous=null,next=null;
        for(int i=0;i<units.size();i++)if(code.equals(text(units.get(i),"unitCode"))){previous=i>0?text(units.get(i-1),"unitCode"):null;next=i+1<units.size()?text(units.get(i+1),"unitCode"):null;}
        out.put("previousUnitCode",previous);out.put("nextUnitCode",next);
        return MAPPER.convertValue(out,Map.class);
    }
    String assetId(String path){Map<String,Object> a=db.one("SELECT id FROM course_asset WHERE assetPath=?",path);return a==null?"missing":db.value(a,"id");}
    JsonNode resourceConfig(Map<String,Object> resource) {
        JsonNode metadata=parse(db.value(resource,"metadataJson"));ObjectNode config=MAPPER.createObjectNode();
        if(metadata.path("resourceConfig").isObject())config=object(metadata.path("resourceConfig"));
        else for(String key:Arrays.asList("algorithm","algorithmKey","frames","frameCount","referenceString","pageSequence","processes","quantum","timeQuantum","task"))if(metadata.has(key))config.set(key,metadata.path(key));
        stripHidden(config);return config;
    }
    void stripHidden(JsonNode node) {
        if(node.isObject()) {
            List<String> hidden=new ArrayList<>();node.fieldNames().forEachRemaining(key->{if(Arrays.asList("answer","correctanswer","analysis","judgecase","solution","referenceanswer","contentchanges").contains(key.toLowerCase(Locale.ROOT)))hidden.add(key);});
            ((ObjectNode)node).remove(hidden);node.elements().forEachRemaining(this::stripHidden);
        }else if(node.isArray())node.forEach(this::stripHidden);
    }
    void position(long user,String course,String unit,String activity){db.update("INSERT INTO learning_course_position(id,userId,courseCode,unitCode,activityCode) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE unitCode=VALUES(unitCode),activityCode=VALUES(activityCode),updateTime=CURRENT_TIMESTAMP",db.id(),user,course,unit,activity);}
    String selectedActivity(JsonNode u,long userId) {
        Map<String,Object> position=db.one("SELECT activityCode FROM learning_unit_position WHERE userId=? AND unitCode=?",userId,text(u,"unitCode"));
        if(position!=null)for(JsonNode a:u.path("activities"))if(text(a,"activityCode").equals(db.value(position,"activityCode")))return text(a,"activityCode");
        return nextActivity(u,userId);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> selectActivity(String unitCode,JsonNode request,long userId) {
        JsonNode u=publishedUnit(unitCode);String activity=required(request,"activityCode");boolean found=false;
        for(JsonNode a:u.path("activities"))if(activity.equals(text(a,"activityCode")))found=true;
        if(!found)throw bad("活动不属于该发布单元");
        String mode=text(request,"mode");if(mode.isEmpty())mode="MAINLINE";
        if(!Arrays.asList("MAINLINE","REVIEW").contains(mode))throw bad("mode必须为MAINLINE或REVIEW");
        db.update("INSERT INTO learning_unit_position(id,userId,unitCode,activityCode,mode) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE activityCode=VALUES(activityCode),mode=VALUES(mode),updateTime=CURRENT_TIMESTAMP",db.id(),userId,unitCode,activity,mode);
        boolean mainline=!"COMPLETED".equals(progress(u,userId).get("status"))&&updateMainline(u,activity,userId,mode);
        return map("unitCode",unitCode,"activityCode",activity,"mode",mode,"mainlineUpdated",mainline);
    }
    boolean updateMainline(JsonNode unit,String activity,long userId,String mode) {
        if("REVIEW".equals(mode))return false;
        String course=text(unit,"courseCode"),code=text(unit,"unitCode");
        Map<String,Object> previous=db.one("SELECT unitCode FROM learning_course_position WHERE userId=? AND courseCode=? AND isDelete=0",userId,course);
        if(previous!=null&&!code.equals(db.value(previous,"unitCode"))) {
            List<JsonNode> units=publishedUnits(course);int old=-1,target=-1;
            for(int i=0;i<units.size();i++){String c=text(units.get(i),"unitCode");if(code.equals(c))target=i;if(db.value(previous,"unitCode").equals(c))old=i;}
            if(old>target)return false;
        }
        position(userId,course,code,activity);return true;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> complete(String activityCode,JsonNode request,long userId) {
        // Look up published snapshots, not mutable authoring activity rows.
        JsonNode unit=null,activity=null;
        for(Map<String,Object> r:db.rows("SELECT publishedJson FROM learning_unit WHERE publishedJson IS NOT NULL AND isDelete=0")) {
            JsonNode candidate=parse(db.value(r,"publishedJson"));for(JsonNode a:candidate.path("activities"))if(activityCode.equals(text(a,"activityCode"))){unit=candidate;activity=a;break;}
            if(unit!=null)break;
        }
        if(unit==null)throw bad("活动尚未发布");
        if(!text(unit,"unitCode").equals(required(request,"sourceUnitCode")))throw bad("sourceUnitCode与活动不匹配");
        String type=text(activity,"activityType"),rule=text(activity,"completionRule"),evidenceType=null,evidenceId=null;
        Boolean passed=null;boolean completed;
        if("CONFIRM".equals(rule)) {if(!request.path("confirmed").asBoolean())throw bad("请明确确认阅读完成");completed=true;}
        else if("RUN_TO_END".equals(rule)){if(!request.path("runToEnd").asBoolean())throw bad("演示尚未运行到终步");completed=true;}
        else {
            boolean experiment="EXPERIMENT".equals(type);evidenceType=experiment?"QUESTION_SUBMIT":"LEARNING_ATTEMPT";
            evidenceId=required(request,experiment?"submissionId":"attemptId");
            String ref=text(activity.path("reference"),"code"),unitCode=text(unit,"unitCode");
            Map<String,Object> result;
            if(experiment) {
                result=db.one("SELECT s.* FROM question_submit s JOIN course_resource r ON r.questionId=s.questionId WHERE s.id=? AND s.userId=? AND r.resourceCode=? AND s.sourceUnitCode=? AND s.sourceActivityCode=? AND s.isDelete=0 AND r.isDelete=0",evidenceId,userId,ref,unitCode,activityCode);
                if(result==null)throw bad("提交不属于本人、资源或当前活动");
                int status=((Number)result.get("status")).intValue();
                if(!JudgeResultClassifier.isTerminal(status))throw bad("实验仍在判题");
                LearningResultEnum judgeResult=JudgeResultClassifier.classify(status,text(parse(db.value(result,"judgeInfo")),"message"));
                if(judgeResult==LearningResultEnum.SYSTEM_ERROR)throw bad("判题系统错误不计学习结果");
                passed=judgeResult==LearningResultEnum.PASS;
            } else {
                result=db.one("SELECT a.* FROM learning_attempt a JOIN learning_question q ON q.id=a.questionId WHERE a.id=? AND a.userId=? AND q.questionCode=? AND a.sourceUnitCode=? AND a.sourceActivityCode=? AND q.isDelete=0",evidenceId,userId,ref,unitCode,activityCode);
                if(result==null)throw bad("作答不属于本人、题目或当前活动");passed=((Number)result.get("isCorrect")).intValue()==1;
            }
            completed="SUBMIT".equals(rule)||Boolean.TRUE.equals(passed);
        }
        db.update("INSERT INTO learning_activity_progress(id,userId,unitCode,activityCode,completed,passed,evidenceType,evidenceId) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE completed=GREATEST(completed,VALUES(completed)),passed=VALUES(passed),evidenceType=VALUES(evidenceType),evidenceId=VALUES(evidenceId),updateTime=CURRENT_TIMESTAMP",db.id(),userId,text(unit,"unitCode"),activityCode,completed,passed,evidenceType,evidenceId);
        Map<String,Object> selected=db.one("SELECT mode FROM learning_unit_position WHERE userId=? AND unitCode=?",userId,text(unit,"unitCode"));
        updateMainline(unit,nextActivity(unit,userId),userId,selected==null?text(request,"mode"):db.value(selected,"mode"));
        Map<String,Object> response=progress(unit,userId);
        Map<String,Object> stored=db.one("SELECT completed,passed FROM learning_activity_progress WHERE userId=? AND activityCode=?",userId,activityCode);
        response.put("activityCode",activityCode);response.put("completed",stored==null?completed:((Number)stored.get("completed")).intValue()==1);response.put("passed",stored==null?passed:stored.get("passed")==null?null:((Number)stored.get("passed")).intValue()==1);response.put("completionRule",rule);
        return response;
    }
    public List<Map<String,Object>> nodeUnits(String nodeCode,long userId) {
        List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> c:db.rows("SELECT courseCode FROM course_catalog WHERE isDelete=0"))for(JsonNode u:publishedUnits(db.value(c,"courseCode")))for(JsonNode n:u.path("nodeMappings"))if(nodeCode.equals(text(n,"nodeCode"))){Map<String,Object> s=summary(u,userId);s.put("role",text(n,"role"));out.add(s);break;}return out;
    }
}
