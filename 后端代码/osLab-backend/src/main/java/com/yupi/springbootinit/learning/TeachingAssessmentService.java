package com.yupi.springbootinit.learning;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;

/** Stable question upsert and immutable revision ledger; published question rows remain active. */
@Service
public class TeachingAssessmentService {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Resource private JdbcTemplate jdbc;
    @Resource private NodeIdentityService identities;
    @Resource private AssessmentAssetService assets;
    private boolean same(JsonNode a,JsonNode b){return normalized(a).equals(normalized(b));}
    public JsonNode unpack(JsonNode request){try {if(request==null||!request.isObject())throw bad("请求必须为JSON对象");JsonNode pack=request.has("package")?request.get("package"):request.has("content")?JSON.readTree(request.path("content").asText()):request;if(!pack.isObject())throw bad("包必须为JSON对象");return pack;}catch(Exception e){throw bad("无效 JSON 包");}}
    public Map<String,Object> preview(JsonNode request) {
        JsonNode pack=unpack(request);List<Map<String,Object>> errors=new ArrayList<>(),items=new ArrayList<>();
        try{if(!"oslab-authoring/1.0".equals(pack.path("schemaVersion").asText())||!"QUESTIONS".equals(pack.path("packageType").asText()))throw bad("需要 QUESTIONS 协作包");required(pack,"batchCode");required(pack,"contributor");if(!pack.path("questions").isArray()||pack.path("questions").isEmpty()||pack.path("questions").size()>500)throw bad("questions 必须为1到500项数组");}catch(BusinessException e){errors.add(map("path","package","message",e.getMessage()));}
        Set<String> seen=new HashSet<>();int index=0;
        for(JsonNode original:pack.path("questions")){try{JsonNode q=assets==null?original:assets.prepare(original,request);validate(q);String code=q.path("questionCode").asText();if(!seen.add(code))throw bad("重复 questionCode");Map<String,Object> old=question(code);Map<String,Object> draft=old==null?null:latest(((Number)old.get("id")).longValue());String action=old==null?"CREATE":draft!=null&&same(parse(draft.get("contentJson")),q)?"UNCHANGED":"UPDATE";items.add(map("type","questions","code",code,"action",action));}catch(BusinessException e){errors.add(map("path","questions["+index+"]","message",e.getMessage()));}index++;}
        return map("valid",errors.isEmpty(),"items",items,"errors",errors,"created",count(items,"CREATE"),"updated",count(items,"UPDATE"),"unchanged",count(items,"UNCHANGED"));
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> commit(JsonNode request,long userId) {
        Map<String,Object> result=preview(request);if(!Boolean.TRUE.equals(result.get("valid")))throw bad("导入校验失败: "+result.get("errors"));
        if(assets!=null)assets.archive(unpack(request),request);
        for(JsonNode q:unpack(request).path("questions"))save(assets==null?q:assets.prepare(q,request),userId);
        JsonNode pack=unpack(request);String run="import.assessment."+IdWorker.getId();result.put("importRunCode",run);
        for(Map<String,Object> item:(List<Map<String,Object>>)result.get("items")){Map<String,Object> row=question(item.get("code").toString());item.put("id",row==null?null:row.get("id").toString());}
        jdbc.update("INSERT INTO course_import_run(id,importRunCode,batchCode,packageType,contributor,userId,resultJson) VALUES(?,?,?,'QUESTIONS',?,?,?)",IdWorker.getId(),run,required(pack,"batchCode"),required(pack,"contributor"),userId,JSON.valueToTree(result).toString());
        return result;
    }
    public void validate(JsonNode q) {
        required(q,"questionCode");required(q,"title");required(q,"content");required(q,"analysis");required(q,"sectionCode");
        if(!q.path("questionCode").asText().matches("[A-Za-z0-9][A-Za-z0-9._-]{1,127}"))throw bad("questionCode 格式错误");
        JsonNode answer=q.path("answer"),options=q.path("options");String type=required(q,"type");
        if(!options.isArray())throw bad("options 必须为数组");Set<String> keys=new HashSet<>();
        for(JsonNode o:options){String key=required(o,"key");required(o,"value");if(!key.matches("[A-Z]")||!keys.add(key))throw bad("选项代码须为无重复的大写字母");}
        switch(type){case "JUDGE":if(!answer.isTextual()||!Arrays.asList("true","false").contains(answer.asText())||!options.isEmpty())throw bad("判断答案必须为字符串 true/false 且 options=[]");break;
            case "SINGLE":if(keys.size()<2||!answer.isTextual()||!keys.contains(answer.asText()))throw bad("单选答案须为真实选项字符串");break;
            case "MULTIPLE":strings(answer);Set<String> selected=new HashSet<>();for(JsonNode a:answer)if(!keys.contains(a.asText())||!selected.add(a.asText()))throw bad("多选答案重复或不在选项中");if(keys.size()<2)throw bad("选择题至少两个选项");break;
            case "FILL_BLANK":strings(answer);if(!options.isEmpty())throw bad("填空 options 必须为空");break;
            case "COMPREHENSIVE":if(!answer.isTextual()||answer.asText().trim().isEmpty()||!options.isEmpty())throw bad("综合应用题参考答案必须为非空文字且 options=[]");break;
            default:throw bad("不支持的题型");}
        if(!q.path("difficulty").isIntegralNumber()||q.path("difficulty").asInt()<1||q.path("difficulty").asInt()>5)throw bad("difficulty 须为1到5整数");
        if(!q.path("sourceRef").isObject())throw bad("sourceRef 必填");
        String source=required(q.path("sourceRef"),"sourceCode"),origin=required(q.path("sourceRef"),"origin");
        if(!Arrays.asList("TEXTBOOK","ADAPTED","SELF_AUTHORED","SELF_AUTHORED_SAMPLE","EXTERNAL_QUESTION").contains(origin))throw bad("sourceRef.origin 无效");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM course_source WHERE sourceCode=? AND isDelete=0",Integer.class,source)==0)throw bad("sourceCode 未登记");
        if(q.hasNonNull("partIndex")&&(!q.path("partIndex").isIntegralNumber()||q.path("partIndex").asInt()<1||!q.path("parentQuestionCode").isTextual()))throw bad("拆题需 parentQuestionCode 和正整数 partIndex");
        if(!q.path("usageSuggestions").isMissingNode()&&!q.path("usageSuggestions").isArray())throw bad("usageSuggestions 须为数组");
        for(JsonNode usage:q.path("usageSuggestions")){required(usage,"unitCode");if(!Arrays.asList("PRACTICE","ASSESSMENT","REVIEW").contains(required(usage,"usage")))throw bad("用途建议 usage 无效");}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM course_section WHERE sectionCode=? AND isDelete=0",Integer.class,q.path("sectionCode").asText())==0)throw bad("sectionCode 未登记");
        validateMappings(q.path("nodeMappings"));
        checkRevision(q);
    }
    private void checkRevision(JsonNode input) {
        if(!input.has("revision"))return;
        if(!input.path("revision").isIntegralNumber()||input.path("revision").asInt()<1)throw bad("revision 须为正整数");
        Map<String,Object> old=question(required(input,"questionCode"));
        if(old==null)return;
        Map<String,Object> current=latest(((Number)old.get("id")).longValue());
        if(current!=null&&same(parse(current.get("contentJson")),normalized(input)))return;
        int expected=current==null?((Number)old.get("revision")).intValue():((Number)current.get("revision")).intValue();
        if(input.path("revision").asInt()!=expected)throw bad("revision 版本冲突: "+required(input,"questionCode")+"，当前版本 "+expected);
    }
    public void validateMappings(JsonNode mappings){if(!mappings.isArray()||mappings.isEmpty())throw bad("nodeMappings 至少一个 PRIMARY");boolean primary=false;Set<String> seen=new HashSet<>();for(JsonNode n:mappings){String code=required(n,"nodeCode");identities.requireCode(code);if(!seen.add(code))throw bad("重复 nodeCode");String role=required(n,"role");if(!Arrays.asList("PRIMARY","SECONDARY").contains(role))throw bad("role 无效");primary|="PRIMARY".equals(role);if(n.has("weight")&&(!n.path("weight").isNumber()||n.path("weight").asDouble()<=0||n.path("weight").asDouble()>1))throw bad("weight 必须在(0,1]");}if(!primary)throw bad("至少一个 PRIMARY");}
    private void strings(JsonNode a){if(!a.isArray()||a.isEmpty())throw bad("答案必须为非空字符串数组");for(JsonNode v:a)if(!v.isTextual()||v.asText().trim().isEmpty())throw bad("答案必须为非空字符串数组");}
    @Transactional(rollbackFor=Exception.class)
    public long save(JsonNode input,long user){checkRevision(input);ObjectNode q=normalized(input);String code=required(q,"questionCode");Map<String,Object> old=question(code);long id=old==null?IdWorker.getId():((Number)old.get("id")).longValue();Map<String,Object> draft=latest(id);if(draft!=null&&same(parse(draft.get("contentJson")),q))return id;
        int rev=draft==null?(old==null?1:((Number)old.get("revision")).intValue()+1):((Number)draft.get("revision")).intValue()+1;
        if(old==null)jdbc.update("INSERT INTO learning_question(id,questionCode,title,content,type,options,answer,analysis,misconception,chapter,sectionCode,difficulty,reviewStatus,userId,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)",id,code,q.path("title").asText(),q.path("content").asText(),q.path("type").asText(),q.path("options").toString(),q.path("answer").toString(),q.path("analysis").asText(),q.path("misconception").asText(),chapterTitle(q.path("sectionCode").asText()),q.path("sectionCode").asText(),q.path("difficulty").asInt(),user,rev);
        jdbc.update("INSERT INTO learning_question_revision(id,questionId,revision,reviewStatus,contentJson,authorId) VALUES(?,?,?,'DRAFT',?,?)",IdWorker.getId(),id,rev,q.toString(),user);
        if(old==null||!"PUBLISHED".equals(old.get("reviewStatus")))activate(id,rev,q,"DRAFT");return id;
    }
    public Map<String,Object> manage(String code){Map<String,Object> q=question(code);if(q==null)throw bad("题目不存在");Map<String,Object> draft=latest(((Number)q.get("id")).longValue());return map("questionId",q.get("id").toString(),"questionCode",code,"publishedRevision",q.get("revision"),"activeStatus",q.get("reviewStatus"),"revision",draft==null?q.get("revision"):draft.get("revision"),"status",draft==null?q.get("reviewStatus"):draft.get("reviewStatus"),"draft",draft==null?null:parse(draft.get("contentJson")));}
    public List<Long> questionIdsByDraftStatus(String status){return jdbc.queryForList("SELECT r.questionId FROM learning_question_revision r WHERE r.reviewStatus=? AND r.revision=(SELECT MAX(r2.revision) FROM learning_question_revision r2 WHERE r2.questionId=r.questionId)",Long.class,status);}
    public Map<String,Object> draft(long questionId){Map<String,Object> r=latest(questionId);return r==null?null:map("content",parse(r.get("contentJson")),"reviewStatus",r.get("reviewStatus"),"revision",r.get("revision"));}
    @Transactional(rollbackFor=Exception.class)
    public boolean review(long id,String action,String reason,long user){Map<String,Object> draft=latest(id);if(draft==null)return false;String from=draft.get("reviewStatus").toString(),to;
        switch(action){case "SUBMIT":if(!Arrays.asList("DRAFT","REJECTED").contains(from))throw bad("仅草稿可提交");to="SUBMITTED";break;case "APPROVE":if(!"SUBMITTED".equals(from))throw bad("仅待审可通过");to="APPROVED";break;case "REJECT":if(!"SUBMITTED".equals(from)||reason==null||reason.trim().isEmpty())throw bad("驳回需待审及理由");to="REJECTED";break;case "PUBLISH":if(!Arrays.asList("APPROVED","OFFLINE").contains(from))throw bad("仅已批准或已下线的修订可发布");to="PUBLISHED";activate(id,((Number)draft.get("revision")).intValue(),parse(draft.get("contentJson")),to);break;
            case "OFFLINE":if(!"PUBLISHED".equals(from))throw bad("仅已发布题目可下线");to="OFFLINE";break;default:return false;}
        jdbc.update("UPDATE learning_question_revision SET reviewStatus=? WHERE id=?",to,draft.get("id"));
        if("OFFLINE".equals(to))jdbc.update("UPDATE learning_question SET reviewStatus=? WHERE id=?",to,id);
        else jdbc.update("UPDATE learning_question SET reviewStatus=? WHERE id=? AND reviewStatus<>'PUBLISHED'",to,id);
        jdbc.update("INSERT INTO learning_review(id,questionId,reviewerId,action,reason) VALUES(?,?,?,?,?)",IdWorker.getId(),id,user,action,reason);return true;
    }
    private String chapterTitle(String section) {
        Set<String> seen=new HashSet<>();String title=section;
        while(section!=null&&!section.isEmpty()&&seen.add(section)) {
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT title,parentSectionCode FROM course_section WHERE sectionCode=? AND isDelete=0",section);
            if(rows.isEmpty())break;title=rows.get(0).get("title").toString();Object parent=rows.get(0).get("parentSectionCode");section=parent==null?null:parent.toString();
        }
        return title;
    }
    private void activate(long id,int revision,JsonNode q,String status){jdbc.update("UPDATE learning_question SET title=?,content=?,type=?,options=?,answer=?,analysis=?,misconception=?,chapter=?,sectionCode=?,difficulty=?,reviewStatus=?,revision=? WHERE id=?",q.path("title").asText(),q.path("content").asText(),q.path("type").asText(),q.path("options").toString(),q.path("answer").toString(),q.path("analysis").asText(),q.path("misconception").asText(),chapterTitle(q.path("sectionCode").asText()),q.path("sectionCode").asText(),q.path("difficulty").asInt(),status,revision,id);
        jdbc.update("DELETE FROM learning_question_node WHERE questionId=?",id);for(JsonNode n:q.path("nodeMappings")){NodeIdentityService.Identity identity=n.hasNonNull("nodeCode")?identities.requireCode(n.path("nodeCode").asText()):null;jdbc.update("INSERT INTO learning_question_node(id,questionId,nodeName,nodeCode,role,weight) VALUES(?,?,?,?,?,?)",IdWorker.getId(),id,identity==null?n.path("nodeName").asText():identity.storageKey,identity==null?null:identity.nodeCode,n.path("role").asText("PRIMARY"),weight(n));}}
    /** Existing editor keeps its DTO and ID; a published edit enters the same draft review flow. */
    @Transactional(rollbackFor=Exception.class)
    public void saveEditorRevision(com.yupi.springbootinit.model.dto.learning.LearningQuestionUpdateRequest request,
                                   com.yupi.springbootinit.model.entity.LearningQuestion old,long user){
        Map<String,Object> previous=latest(old.getId());ObjectNode q=previous==null?JSON.createObjectNode():(ObjectNode)parse(previous.get("contentJson"));ObjectNode editor=JSON.valueToTree(request);editor.remove(Arrays.asList("id","nodeNames"));q.setAll(editor);String code=old.getQuestionCode()==null?"q.legacy."+old.getId():old.getQuestionCode();q.put("questionCode",code);q.put("sectionCode",request.getSectionCode()!=null?request.getSectionCode():(old.getSectionCode()==null?request.getChapter():old.getSectionCode()));
        com.fasterxml.jackson.databind.node.ArrayNode nodes=q.putArray("nodeMappings");
        List<Map<String,Object>> current=jdbc.queryForList("SELECT nodeCode,nodeName,role,weight FROM learning_question_node WHERE questionId=?",old.getId());
        if(request.getNodeNames()!=null)for(String name:request.getNodeNames()){Map<String,Object> existing=null;for(Map<String,Object> n:current)if(name.equals(n.get("nodeName"))){existing=n;break;}ObjectNode n=nodes.addObject();NodeIdentityService.Identity identity=identities.resolve(name);if(identity!=null)n.put("nodeCode",identity.nodeCode);else n.put("nodeName",name);n.put("role",existing==null||existing.get("role")==null?"PRIMARY":existing.get("role").toString());n.put("weight",existing==null?1.0:((Number)existing.get("weight")).doubleValue());}
        if(old.getQuestionCode()==null)jdbc.update("UPDATE learning_question SET questionCode=? WHERE id=?",code,old.getId());save(q,user);
    }
    public static double weight(JsonNode n){return n.path("weight").asDouble("SECONDARY".equals(n.path("role").asText())?0.3:1.0);}
    private ObjectNode normalized(JsonNode n){ObjectNode out=n.deepCopy();out.remove(Arrays.asList("status","revision","reviewStatus"));for(String field:Arrays.asList("nodeMappings","usageSuggestions"))if(out.path(field).isArray()){List<JsonNode> entries=new ArrayList<>();out.path(field).forEach(entries::add);entries.sort(Comparator.comparing(e->e.path("nodeCode").asText()+e.path("unitCode").asText()+e.path("role").asText()+e.path("usage").asText()));com.fasterxml.jackson.databind.node.ArrayNode a=out.putArray(field);for(JsonNode entry:entries){ObjectNode item=entry.deepCopy();if("nodeMappings".equals(field))item.put("weight",weight(item));a.add(item);}}if("MULTIPLE".equals(out.path("type").asText())&&out.path("answer").isArray()){List<String> answers=new ArrayList<>();out.path("answer").forEach(a->answers.add(a.asText()));Collections.sort(answers);com.fasterxml.jackson.databind.node.ArrayNode a=out.putArray("answer");answers.forEach(a::add);}return out;}
    private Map<String,Object> question(String code){List<Map<String,Object>> r=jdbc.queryForList("SELECT * FROM learning_question WHERE questionCode=? AND isDelete=0 FOR UPDATE",code);return r.isEmpty()?null:r.get(0);}
    private Map<String,Object> latest(long id){List<Map<String,Object>> r=jdbc.queryForList("SELECT * FROM learning_question_revision WHERE questionId=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",id);return r.isEmpty()?null:r.get(0);}
    static JsonNode parse(Object s){try{return JSON.readTree(s.toString());}catch(Exception e){throw bad("无效 JSON");}}
    static String required(JsonNode n,String k){if(!n.path(k).isTextual()||n.path(k).asText().trim().isEmpty())throw bad(k+" 必填字符串");return n.path(k).asText();}
    static BusinessException bad(String s){return new BusinessException(ErrorCode.PARAMS_ERROR,s);}
    static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i].toString(),pairs[i+1]);return m;}
    static long count(List<Map<String,Object>> items,String action){return items.stream().filter(i->action.equals(i.get("action"))).count();}
}
