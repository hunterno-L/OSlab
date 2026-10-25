package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.course.CourseContentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import static com.yupi.springbootinit.learning.TeachingAssessmentService.*;

/** Updates the original programming question, never creates a replacement question identity. */
@Service
public class ExperimentMaintenanceService {
    private static final ObjectMapper JSON=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Set<String> FIELDS=new HashSet<>(Arrays.asList("content","originalCode","answer","judgeCase","judgeConfig","tags"));
    @Resource private JdbcTemplate jdbc;
    @Resource private TeachingAssessmentService assessments;
    @Resource private NodeIdentityService identities;
    @Resource private CourseContentService course;
    public Map<String,Object> preview(JsonNode request){List<Map<String,Object>> errors=new ArrayList<>(),items=new ArrayList<>();JsonNode pack=assessments.unpack(request);Map<String,String> files=new HashMap<>();
        try{if(!"oslab-experiment-maintenance/1.0".equals(required(pack,"schemaVersion")))throw bad("实验包协议错误");required(pack,"batchCode");required(pack,"contributor");if(!pack.path("experiments").isArray())throw bad("experiments 必须为数组");files=files(request);}catch(Exception e){errors.add(map("path","package","message",e.getMessage()));}
        Set<String> ids=new HashSet<>(),codes=new HashSet<>();int i=0;for(JsonNode e:pack.path("experiments")){try{validate(e,files);String id=required(e,"questionId"),code=required(e,"resourceCode");if(!ids.add(id)||!codes.add(code))throw bad("重复实验ID或资源代码");items.add(map("type","experiments","code",code,"action",unchanged(e,files)?"UNCHANGED":"UPDATE"));}catch(Exception ex){errors.add(map("path","experiments["+i+"]","message",ex.getMessage()));}i++;}
        return map("valid",errors.isEmpty(),"items",items,"errors",errors,"created",0,"updated",count(items,"UPDATE"),"unchanged",count(items,"UNCHANGED"));}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> commit(JsonNode request,long user){Map<String,Object> result=preview(request);if(!Boolean.TRUE.equals(result.get("valid")))throw bad("实验导入校验失败: "+result.get("errors"));Map<String,String> files=files(request);JsonNode pack=assessments.unpack(request);for(JsonNode e:pack.path("experiments"))stage(e,files,user,required(pack,"batchCode"),required(pack,"contributor"));return result;}
    void validate(JsonNode e,Map<String,String> files){String id=required(e,"questionId");if(!id.matches("[1-9][0-9]*"))throw bad("questionId 须为原ID字符串");if(question(id)==null)throw bad("原实验不存在");String code=required(e,"resourceCode");if(!code.matches("[a-z][a-z0-9._-]{1,127}"))throw bad("资源代码格式错误");required(e,"title");
        List<Map<String,Object>> resources=jdbc.queryForList("SELECT resourceCode,questionId FROM course_resource WHERE resourceCode=? OR (questionId=? AND resourceType='EXPERIMENT')",code,id);for(Map<String,Object> r:resources)if(!code.equals(r.get("resourceCode"))||!id.equals(String.valueOf(r.get("questionId"))))throw bad("实验与资源代码已存在其他绑定");
        assessments.validateMappings(e.path("nodeMappings"));if(!e.path("unitMappings").isArray()||!e.path("contentChanges").isArray())throw bad("映射及内容修订须为数组");Set<String> units=new HashSet<>();for(JsonNode u:e.path("unitMappings")){String unit=required(u,"unitCode");if(!units.add(unit))throw bad("单元映射重复");if(!u.path("required").isBoolean())throw bad("required 须为布尔值");if(!Arrays.asList("SUBMIT","PASS").contains(required(u,"completionRule")))throw bad("完成规则无效");course.manage("units",unit);}
        Set<String> changed=new HashSet<>();for(JsonNode c:e.path("contentChanges")){String field=required(c,"field");required(c,"reason");if(!FIELDS.contains(field)||!changed.add(field))throw bad("不支持或重复的修订字段: "+field);String file=required(c,"file");if(!files.containsKey(file))throw bad("缺少附件: "+file);validateContent(field,files.get(file));}}
    void validateContent(String field,String text){if(text.trim().isEmpty())throw bad("修订文件不能为空");if("judgeCase".equals(field)){JsonNode cases=parse(text);if(!cases.isArray()||cases.isEmpty())throw bad("judgeCase 须为非空数组");for(JsonNode c:cases)if(!c.path("input").isTextual()||!c.path("output").isTextual())throw bad("用例须有字符串 input/output");}if("judgeConfig".equals(field)){JsonNode n=parse(text);if(!n.isObject()||!n.path("timeLimit").isNumber()||n.path("timeLimit").asLong()<=0||!n.path("memoryLimit").isNumber()||n.path("memoryLimit").asLong()<=0)throw bad("judgeConfig 需正数 timeLimit/memoryLimit");}if("tags".equals(field)){JsonNode n=parse(text);if(!n.isArray())throw bad("tags 须为数组");for(JsonNode t:n)if(!t.isTextual())throw bad("tags 须为字符串数组");}}
    Map<String,String> files(JsonNode request){JsonNode attachments=request.has("files")?request.path("files"):request.path("assets");Map<String,String> out=new HashMap<>();int total=0;for(JsonNode f:attachments){String path=required(f,"path");if(!path.startsWith("experiment-files/")||path.contains("..")||path.contains("\\")||path.contains(":")||path.contains("//")||path.contains("?")||path.contains("#")||path.length()>300)throw bad("附件路径无效");byte[] bytes;try{bytes=Base64.getDecoder().decode(required(f,"contentBase64"));}catch(Exception e){throw bad("附件Base64无效");}total+=bytes.length;if(total>16*1024*1024||bytes.length>4*1024*1024)throw bad("实验附件超过大小限制");try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();if(out.put(path,text.replaceFirst("^\\uFEFF",""))!=null)throw bad("附件路径重复");}catch(CharacterCodingException e){throw bad("附件须为UTF-8");}}return out;}
    private Map<String,Object> question(String id){List<Map<String,Object>> r=jdbc.queryForList("SELECT * FROM question WHERE id=? AND isDelete=0",id);return r.isEmpty()?null:r.get(0);}
    boolean unchanged(JsonNode e,Map<String,String> files){Map<String,Object> revision=latest(required(e,"questionId"));if(revision!=null){if(!parse(revision.get("requestJson")).equals(e))return false;JsonNode after=parse(revision.get("afterJson"));for(JsonNode change:e.path("contentChanges"))if(!Objects.equals(after.path(required(change,"field")).asText(),files.get(required(change,"file"))))return false;return true;}return false;}
    Map<String,Object> latest(String id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM experiment_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",id);return rows.isEmpty()?null:rows.get(0);}
    void stage(JsonNode e,Map<String,String> files,long user,String batch,String contributor) {
        if(unchanged(e,files))return;
        String id=required(e,"questionId"),resource=required(e,"resourceCode");
        Map<String,Object> previous=latest(id);int revision=previous==null?1:((Number)previous.get("revision")).intValue()+1;
        ObjectNode before=JSON.valueToTree(question(id)),after=before.deepCopy();after.put("title",required(e,"title"));
        for(JsonNode change:e.path("contentChanges"))after.put(required(change,"field"),files.get(required(change,"file")));
        jdbc.update("INSERT INTO experiment_revision(id,questionId,resourceCode,revision,requestJson,beforeJson,afterJson,userId,contributor,batchCode) VALUES(?,?,?,?,?,?,?,?,?,?)",IdWorker.getId(),id,resource,revision,e.toString(),before.toString(),after.toString(),user,contributor,batch);
        jdbc.update("INSERT IGNORE INTO course_resource(id,resourceCode,resourceType,title,questionId,status,metadataJson) VALUES(?,?,'EXPERIMENT',?,?,'DRAFT','{}')",IdWorker.getId(),resource,required(e,"title"),id);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> review(String id,JsonNode request,long user) {
        Map<String,Object> current=latest(id);if(current==null)throw bad("实验没有待处理修订");
        String action=required(request,"action"),status=current.get("reviewStatus").toString(),next;
        if("SUBMIT".equals(action)&&Arrays.asList("DRAFT","REJECTED").contains(status))next="SUBMITTED";
        else if("APPROVE".equals(action)&&"SUBMITTED".equals(status))next="APPROVED";
        else if("REJECT".equals(action)&&"SUBMITTED".equals(status)){required(request,"reason");next="REJECTED";}
        else if("APPLY".equals(action)&&"APPROVED".equals(status)) {
            if(!request.path("confirmed").asBoolean())throw bad("请确认将批准修订应用到原实验");
            JsonNode before=parse(current.get("beforeJson")),actual=JSON.valueToTree(question(id));
            Set<String> compared=new HashSet<>(FIELDS);compared.add("title");
            for(String field:compared)if(!before.path(field).equals(actual.path(field)))throw bad("原实验已发生变化，请重新导入修订:"+field);
            JsonNode e=parse(current.get("requestJson")),after=parse(current.get("afterJson"));Map<String,String> files=new HashMap<>();
            for(JsonNode change:e.path("contentChanges"))files.put(required(change,"file"),after.path(required(change,"field")).asText());
            validate(e,files);apply(e,files,user);next="APPLIED";
        } else throw bad("当前状态不允许该实验审核动作");
        jdbc.update("UPDATE experiment_revision SET reviewStatus=?,reviewerId=?,reviewReason=?,appliedTime=IF(?='APPLIED',CURRENT_TIMESTAMP,appliedTime) WHERE id=?",next,user,request.path("reason").asText(),next,current.get("id"));return mappings(id);
    }
    void apply(JsonNode e,Map<String,String> files,long user){String id=required(e,"questionId"),resource=required(e,"resourceCode");
        List<Map<String,Object>> previous=jdbc.queryForList("SELECT metadataJson FROM course_resource WHERE resourceCode=? AND isDelete=0",resource);
        if(!previous.isEmpty()){JsonNode old=parse(previous.get(0).get("metadataJson"));Set<String> retained=new HashSet<>();for(JsonNode m:e.path("unitMappings"))retained.add(required(m,"unitCode"));for(JsonNode m:old.path("unitMappings"))if(!retained.contains(required(m,"unitCode"))){String unit=required(m,"unitCode");ObjectNode draft=JSON.valueToTree(course.manage("units",unit));ArrayNode acts=JSON.createArrayNode();for(JsonNode a:draft.path("activities"))if(!("EXPERIMENT".equals(a.path("activityType").asText())&&resource.equals(a.path("reference").path("code").asText())))acts.add(a);draft.set("activities",acts);course.saveUnit(unit,draft,user);}}
        jdbc.update("UPDATE question SET title=? WHERE id=?",required(e,"title"),id);
        for(JsonNode c:e.path("contentChanges")){String field=required(c,"field");if(!FIELDS.contains(field))throw bad("字段不可修订");jdbc.update("UPDATE question SET "+field+"=? WHERE id=?",files.get(required(c,"file")),id);}
        jdbc.update("INSERT INTO course_resource(id,resourceCode,resourceType,title,questionId,status,metadataJson) VALUES(?,?,'EXPERIMENT',?,?,'DRAFT',?) ON DUPLICATE KEY UPDATE title=VALUES(title),metadataJson=VALUES(metadataJson),isDelete=0",IdWorker.getId(),resource,required(e,"title"),id,e.toString());
        jdbc.update("DELETE FROM experiment_node WHERE questionId=?",id);for(JsonNode n:e.path("nodeMappings")){NodeIdentityService.Identity i=identities.requireCode(required(n,"nodeCode"));jdbc.update("INSERT INTO experiment_node(id,questionId,nodeCode,nodeName,role,weight,reason) VALUES(?,?,?,?,?,?,?)",IdWorker.getId(),id,i.nodeCode,i.storageKey,required(n,"role"),weight(n),n.path("reason").asText());}
        // Reuse an existing activity for this same resource in each draft, preserving activityCode.
        for(JsonNode m:e.path("unitMappings")){String unit=required(m,"unitCode");ObjectNode draft=JSON.valueToTree(course.manage("units",unit));ArrayNode acts=(ArrayNode)draft.path("activities");ObjectNode target=null;for(JsonNode a:acts)if("EXPERIMENT".equals(a.path("activityType").asText())&&resource.equals(a.path("reference").path("code").asText())){target=(ObjectNode)a;break;}
            if(target==null){target=acts.addObject();String activity="act.exp."+IdWorker.getId();target.put("activityCode",activity);target.put("title",required(e,"title"));target.put("activityType","EXPERIMENT");target.put("sortOrder",acts.size()*10);target.putObject("reference").put("kind","RESOURCE").put("code",resource);target.putObject("config");}
            target.put("required",m.path("required").asBoolean());target.put("completionRule",required(m,"completionRule"));course.saveUnit(unit,draft,user);}}
    public Map<String,Object> mappings(String id){Map<String,Object> q=question(id);if(q==null)throw bad("实验不存在");List<Map<String,Object>> r=jdbc.queryForList("SELECT * FROM course_resource WHERE questionId=? AND resourceType='EXPERIMENT' AND isDelete=0",id);JsonNode metadata=r.isEmpty()||r.get(0).get("metadataJson")==null?JSON.createObjectNode():parse(r.get(0).get("metadataJson"));Map<String,Object> revision=latest(id);JsonNode draft=revision==null?null:parse(revision.get("requestJson"));boolean pending=revision!=null&&!"APPLIED".equals(revision.get("reviewStatus"));return map("questionId",id,"resourceCode",r.isEmpty()?"exp.legacy."+id:r.get(0).get("resourceCode"),"title",pending?draft.path("title").asText():q.get("title"),"nodeMappings",pending?draft.path("nodeMappings"):jdbc.queryForList("SELECT nodeCode,role,weight,reason FROM experiment_node WHERE questionId=?",id),"unitMappings",pending?draft.path("unitMappings"):metadata.path("unitMappings").isArray()?metadata.path("unitMappings"):JSON.createArrayNode(),"revision",revision==null?null:revision.get("revision"),"status",revision==null?null:revision.get("reviewStatus"),"before",revision==null?null:parse(revision.get("beforeJson")),"after",revision==null?null:parse(revision.get("afterJson")));}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> saveMappings(String id,JsonNode request,long user){ObjectNode e=JSON.createObjectNode();Map<String,Object> current=mappings(id);e.put("questionId",id);e.put("resourceCode",current.get("resourceCode").toString());e.put("title",current.get("title").toString());e.set("nodeMappings",request.path("nodeMappings"));e.set("unitMappings",request.path("unitMappings"));e.putArray("contentChanges");validate(e,Collections.emptyMap());stage(e,Collections.emptyMap(),user,"batch.mapping."+IdWorker.getId(),"L");return mappings(id);}
}
