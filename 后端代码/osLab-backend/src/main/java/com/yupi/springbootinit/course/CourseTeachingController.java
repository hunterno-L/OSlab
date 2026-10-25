package com.yupi.springbootinit.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import static com.yupi.springbootinit.course.CourseJson.*;

@RestController
@RequestMapping("/teaching")
public class CourseTeachingController {
    /** 18 项新播放器算法键 + 旧六算法适配器键（兼容既有资源）。 */
    private static final Set<String> DEMO_ADAPTERS = new HashSet<>(Arrays.asList(
            "cpu.fcfs", "cpu.sjf", "cpu.hrrn",
            "cpu.priority_nonpreemptive", "cpu.priority_preemptive", "cpu.rr", "cpu.mlfq",
            "allocation.first_fit", "allocation.best_fit", "allocation.worst_fit", "allocation.next_fit",
            "page.fifo", "page.lru", "page.opt",
            "disk.fcfs", "disk.sstf", "disk.scan", "disk.cscan",
            "cpu.FCFS", "cpu.SJF", "cpu.RR", "page.FIFO", "page.LRU", "page.OPT"));

    @Resource CourseContentService content;
    @Resource CourseService courses;
    @Resource CourseRepository db;
    @Resource UserService users;
    @Resource CourseGraphSyncService graph;
    long user(HttpServletRequest r){return users.getLoginUser(r).getId();}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/content/graph-sync") public BaseResponse<?> syncGraph(HttpServletRequest r){user(r);return ResultUtils.success(graph.syncPending());}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/graph-sync") public BaseResponse<?> graphStatus(HttpServletRequest r){user(r);return ResultUtils.success(graph.status());}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/content/import/preview") public BaseResponse<?> preview(@RequestBody JsonNode body,HttpServletRequest r){user(r);return ResultUtils.success(content.preview(body));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/content/import/commit") public BaseResponse<?> commit(@RequestBody JsonNode body,HttpServletRequest r){return ResultUtils.success(content.commit(body,user(r)));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/courses/{courseCode}/outline") public BaseResponse<?> outline(@PathVariable String courseCode,HttpServletRequest r){return ResultUtils.success(courses.outline(courseCode,user(r),true));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/courses/{courseCode}/outline")
    @Transactional(rollbackFor=Exception.class)
    public BaseResponse<?> saveOutline(@PathVariable String courseCode,@RequestBody JsonNode body,HttpServletRequest r) {
        long userId=user(r);courses.course(courseCode);String title=required(body,"title");content.array(body,"sections");
        Map<String,String> parents=new HashMap<>();
        for(Map<String,Object> old:db.rows("SELECT sectionCode,parentSectionCode FROM course_section WHERE courseCode=? AND isDelete=0",courseCode))parents.put(db.value(old,"sectionCode"),db.value(old,"parentSectionCode"));
        Set<String> seen=new HashSet<>();
        for(JsonNode s:body.path("sections")) {
            String code=code(s,"sectionCode");content.unique(seen,code);required(s,"title");content.enumeration(s,"sectionType","CHAPTER","CONTENT");
            Map<String,Object> old=db.entity("course_section","sectionCode",code);if(old!=null&&!courseCode.equals(db.value(old,"courseCode")))throw bad("目录已属于其他课程");
            parents.put(code,text(s,"parentSectionCode"));
        }
        for(String section:parents.keySet()) {
            Set<String> path=new HashSet<>();String current=section;
            while(current!=null&&!current.isEmpty()){if(!path.add(current))throw bad("目录存在环");if(!parents.containsKey(current))throw bad("未知父目录:"+current);current=parents.get(current);}
        }
        db.update("UPDATE course_catalog SET title=?,description=? WHERE courseCode=?",title,text(body,"description"),courseCode);
        for(JsonNode s:body.path("sections"))db.update("INSERT INTO course_section(id,sectionCode,courseCode,parentSectionCode,title,textbookNumber,sectionType,sortOrder,sourceCode,pageStart,metadataJson) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE parentSectionCode=VALUES(parentSectionCode),title=VALUES(title),textbookNumber=VALUES(textbookNumber),sectionType=VALUES(sectionType),sortOrder=VALUES(sortOrder),sourceCode=VALUES(sourceCode),pageStart=VALUES(pageStart),metadataJson=VALUES(metadataJson)",db.id(),text(s,"sectionCode"),courseCode,s.hasNonNull("parentSectionCode")?text(s,"parentSectionCode"):null,text(s,"title"),text(s,"textbookNumber"),text(s,"sectionType"),s.path("sortOrder").asInt(),text(s,"sourceCode"),s.hasNonNull("pageStart")?s.path("pageStart").asInt():null,json(s));
        return ResultUtils.success(courses.outline(courseCode,userId,true));
    }
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/units/{unitCode}") public BaseResponse<?> unit(@PathVariable String unitCode,HttpServletRequest r){user(r);return ResultUtils.success(content.manage("units",unitCode));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/units/{unitCode}") public BaseResponse<?> saveUnit(@PathVariable String unitCode,@RequestBody JsonNode body,HttpServletRequest r){return ResultUtils.success(content.saveUnit(unitCode,body,user(r)));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/units/{unitCode}/publish") public BaseResponse<?> publish(@PathVariable String unitCode,HttpServletRequest r){ObjectNode body=MAPPER.createObjectNode().put("action","PUBLISH");return ResultUtils.success(content.review("units",unitCode,body,user(r)));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/{entityType}/{entityCode}") public BaseResponse<?> manage(@PathVariable String entityType,@PathVariable String entityCode,HttpServletRequest r){user(r);return ResultUtils.success(content.manage(entityType,entityCode));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PostMapping("/content/{entityType}/{entityCode}/review") public BaseResponse<?> review(@PathVariable String entityType,@PathVariable String entityCode,@RequestBody JsonNode body,HttpServletRequest r){return ResultUtils.success(content.review(entityType,entityCode,body,user(r)));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/catalog") public BaseResponse<?> catalog(HttpServletRequest r) {
        user(r);
        List<JsonNode> sources=new ArrayList<>();for(Map<String,Object> source:db.rows("SELECT metadataJson FROM course_source WHERE isDelete=0"))sources.add(parse(db.value(source,"metadataJson")));
        List<Map<String,Object>> resources=db.rows("SELECT resourceCode,title,resourceType,status,adapterKey,algorithmKey,metadataJson FROM course_resource WHERE isDelete=0");
        for(Map<String,Object> resource:resources){JsonNode metadata=parse(db.value(resource,"metadataJson"));resource.put("metadata",metadata);resource.put("resourceConfig",metadata.path("resourceConfig"));resource.remove("metadataJson");}
        return ResultUtils.success(map("sources",sources,"nodes",db.rows("SELECT nodeCode,name,nodeType,description,aliasesJson,homeSectionCode,sourceRefsJson,layer FROM knowledge_node_registry WHERE isDelete=0"),"chunks",db.rows("SELECT chunkCode,title,courseCode,sectionCode,revision,reviewStatus,publishedJson IS NOT NULL AS hasPublishedVersion FROM learning_content_chunk WHERE isDelete=0"),"units",db.rows("SELECT unitCode,title,courseCode,sectionCode,sortOrder,revision,reviewStatus,publishedJson IS NOT NULL AS hasPublishedVersion FROM learning_unit WHERE isDelete=0"),"resources",resources,"graphSync",graph.status()));
    }
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/sources/{sourceCode}") public BaseResponse<?> sourceDetails(@PathVariable String sourceCode,HttpServletRequest r){user(r);Map<String,Object> row=db.entity("course_source","sourceCode",sourceCode);if(row==null)throw bad("来源不存在");return ResultUtils.success(parse(db.value(row,"metadataJson")));}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/nodes/{nodeCode}") public BaseResponse<?> nodeDetails(@PathVariable String nodeCode,HttpServletRequest r){user(r);Map<String,Object> row=db.entity("knowledge_node_registry","nodeCode",nodeCode);if(row==null)throw bad("知识节点不存在");row.put("aliases",parse(db.value(row,"aliasesJson")));row.put("sourceRefs",parse(db.value(row,"sourceRefsJson")));row.put("relations",db.rows("SELECT fromNodeCode,toNodeCode,relationType,reason,sourceRefJson FROM knowledge_node_relation WHERE (fromNodeCode=? OR toNodeCode=?) AND isDelete=0",nodeCode,nodeCode));return ResultUtils.success(row);}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/content/nodes/{nodeCode}") public BaseResponse<?> nodeSave(@PathVariable String nodeCode,@RequestBody JsonNode body,HttpServletRequest r) {
        long operator=user(r);ObjectNode node=object(body);node.put("nodeCode",nodeCode);
        ObjectNode pack=MAPPER.createObjectNode().put("schemaVersion","oslab-authoring/1.0").put("packageType","KNOWLEDGE").put("batchCode","batch.nodeedit."+db.id()).put("contributor","L").put("courseCode",required(body,"courseCode"));
        pack.putArray("nodes").add(node);pack.putArray("chunks");pack.putArray("relations");return ResultUtils.success(content.commit(pack,operator));
    }
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/content/sources/{sourceCode}") public BaseResponse<?> source(@PathVariable String sourceCode,@RequestBody JsonNode body,HttpServletRequest r) {
        user(r);ObjectNode source=object(body);source.put("sourceCode",sourceCode);code(source,"sourceCode");required(source,"title");content.enumeration(source,"sourceType","TEXTBOOK","ADAPTED","SELF_AUTHORED","SELF_AUTHORED_SAMPLE","EXTERNAL_QUESTION","EXTERNAL_DEMO","TOC");
        if(!source.path("authorized").isBoolean())throw bad("authorized须明确填写");
        db.update("INSERT INTO course_source(id,sourceCode,metadataJson) VALUES(?,?,?) ON DUPLICATE KEY UPDATE metadataJson=VALUES(metadataJson),isDelete=0",db.id(),sourceCode,json(source));return ResultUtils.success(source);
    }
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/content/nodes/{nodeCode}/relations")
    @Transactional(rollbackFor=Exception.class)
    public BaseResponse<?> nodeRelations(@PathVariable String nodeCode,@RequestBody JsonNode body,HttpServletRequest r) {
        user(r);
        if(body==null||db.rows("SELECT nodeCode FROM knowledge_node_registry WHERE nodeCode=? AND isDelete=0",nodeCode).isEmpty())throw bad("知识节点不存在");
        JsonNode relations=body.path("relations");
        if(!relations.isArray())throw bad("relations 必须为数组");
        Set<String> types=new HashSet<>(Arrays.asList("REQUIRES","CONTAINS","CONTRASTS_WITH","BASED_ON","IMPLEMENTS","RELATED_TO"));
        Set<String> seen=new HashSet<>();
        for(JsonNode relation:relations){
            String to=text(relation,"toNodeCode"),type=text(relation,"relationType");
            if(to.isEmpty()||to.equals(nodeCode))throw bad("关系目标无效");
            if(!types.contains(type))throw bad("关系类型无效:"+type);
            if(!seen.add(fromNode(to,type,nodeCode)))throw bad("重复关系:"+to+" "+type);
            if(db.rows("SELECT nodeCode FROM knowledge_node_registry WHERE nodeCode=? AND isDelete=0",to).isEmpty())throw bad("目标节点不存在:"+to);
        }
        // 前置关系必须无环；其他关系不套用全局无环限制。
        Map<String,Set<String>> requires=new HashMap<>();
        for(Map<String,Object> row:db.rows("SELECT fromNodeCode,toNodeCode FROM knowledge_node_relation WHERE relationType='REQUIRES' AND isDelete=0 AND fromNodeCode<>?",nodeCode)){
            requires.computeIfAbsent(row.get("fromNodeCode").toString(),k->new HashSet<>()).add(row.get("toNodeCode").toString());
        }
        for(JsonNode relation:relations){
            if("REQUIRES".equals(text(relation,"relationType")))requires.computeIfAbsent(nodeCode,k->new HashSet<>()).add(text(relation,"toNodeCode"));
        }
        if(reaches(requires,nodeCode,nodeCode))throw bad("前置关系存在环");
        // 只替换本节点的出向关系，其他节点指向本节点的入向边保持不变。
        db.update("UPDATE knowledge_node_relation SET isDelete=1 WHERE fromNodeCode=? AND isDelete=0",nodeCode);
        for(JsonNode relation:relations){
            db.update("INSERT INTO knowledge_node_relation(id,fromNodeCode,toNodeCode,relationType,reason,sourceRefJson) VALUES(?,?,?,?,?,?)",
                    db.id(),nodeCode,text(relation,"toNodeCode"),text(relation,"relationType"),
                    relation.path("reason").asText(null),
                    relation.hasNonNull("sourceRef")?relation.path("sourceRef").toString():null);
        }
        long syncId=db.id();
        db.update("INSERT INTO course_graph_sync(id,importRunCode,status) VALUES(?,?,'PENDING')",syncId,"relations."+syncId);
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){graph.syncPending();}
            });
        }
        return ResultUtils.success(map("nodeCode",nodeCode,
                "outgoing",db.rows("SELECT toNodeCode,relationType,reason,sourceRefJson FROM knowledge_node_relation WHERE fromNodeCode=? AND isDelete=0 ORDER BY relationType,toNodeCode",nodeCode),
                "incoming",db.rows("SELECT fromNodeCode,relationType,reason FROM knowledge_node_relation WHERE toNodeCode=? AND isDelete=0 ORDER BY relationType,fromNodeCode",nodeCode),
                "syncStatus","PENDING"));
    }

    private static String fromNode(String to,String type,String from){return from+"|"+type+"|"+to;}

    private static boolean reaches(Map<String,Set<String>> edges,String start,String target){
        Deque<String> queue=new ArrayDeque<>();Set<String> visited=new HashSet<>();queue.add(start);
        while(!queue.isEmpty()){
            String current=queue.poll();
            if(!visited.add(current))continue;
            for(String next:edges.getOrDefault(current,Collections.emptySet())){
                if(next.equals(target))return true;
                queue.add(next);
            }
        }
        return false;
    }

    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @GetMapping("/content/resources/{resourceCode}") public BaseResponse<?> resourceDetails(@PathVariable String resourceCode,HttpServletRequest r){user(r);Map<String,Object> row=db.entity("course_resource","resourceCode",resourceCode);if(row==null)throw bad("资源不存在");row.put("metadata",parse(db.value(row,"metadataJson")));row.put("resourceConfig",parse(db.value(row,"metadataJson")).path("resourceConfig"));row.remove("metadataJson");return ResultUtils.success(row);}
    @AuthCheck(mustRole=UserConstant.ADMIN_ROLE)
    @PutMapping("/content/resources/{resourceCode}") public BaseResponse<?> resource(@PathVariable String resourceCode,@RequestBody JsonNode body,HttpServletRequest r) {
        user(r);Map<String,Object> old=db.entity("course_resource","resourceCode",resourceCode);if(old==null)throw bad("先登记资源再启用");
        String status=required(body,"status");if(!Arrays.asList("DRAFT","PUBLISHED","OFFLINE").contains(status))throw bad("资源状态无效");
        String adapter=body.has("adapterKey")?text(body,"adapterKey"):db.value(old,"adapterKey"),algorithm=body.has("algorithmKey")?text(body,"algorithmKey"):db.value(old,"algorithmKey");
        ObjectNode metadata=object(parse(db.value(old,"metadataJson")));if(body.has("resourceConfig")){if(!body.path("resourceConfig").isObject())throw bad("resourceConfig必须为对象");content.rejectHiddenAnswers(body.path("resourceConfig"));metadata.set("resourceConfig",body.path("resourceConfig"));}
        if("DEMO".equals(db.value(old,"resourceType"))&&"PUBLISHED".equals(status)) {
            if(!DEMO_ADAPTERS.contains(adapter))throw bad("仅允许启用已登记的算法演示适配器");
            if(!Boolean.TRUE.equals(body.path("runtimeVerified").isBoolean()?body.path("runtimeVerified").asBoolean():false))throw bad("需明确确认运行验证");
            String expected=db.value(old,"resourceCode").replace("demo.","");
            if(!expected.equalsIgnoreCase(adapter))throw bad("适配器与资源代码不匹配");
            metadata.put("runtimeVerified",true);
        }
        if("EXPERIMENT".equals(db.value(old,"resourceType"))&&"PUBLISHED".equals(status)) {
            Map<String,Object> applied=db.one("SELECT id FROM experiment_revision WHERE questionId=? AND reviewStatus='APPLIED' ORDER BY revision DESC LIMIT 1",old.get("questionId"));
            if(applied==null)throw bad("请先审核并应用实验修订与映射");
            if(!body.path("runtimeVerified").isBoolean()||!body.path("runtimeVerified").asBoolean())throw bad("启用实验资源前请确认运行核验");
            metadata.put("runtimeVerified",true);
        }
        db.update("UPDATE course_resource SET status=?,adapterKey=?,algorithmKey=?,metadataJson=? WHERE resourceCode=?",status,adapter,algorithm,json(metadata),resourceCode);return ResultUtils.success(map("resourceCode",resourceCode,"status",status,"adapterKey",adapter,"algorithmKey",algorithm,"resourceConfig",metadata.path("resourceConfig")));
    }
}
