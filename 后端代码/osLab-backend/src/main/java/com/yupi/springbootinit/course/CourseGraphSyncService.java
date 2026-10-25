package com.yupi.springbootinit.course;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;
import static com.yupi.springbootinit.course.CourseJson.*;

/** Durable retry boundary: a Neo4j outage cannot erase a committed member package. */
@Service
public class CourseGraphSyncService {
    @Resource CourseRepository db;
    @Resource Driver driver;
    /** Binding is an explicit administrator decision. Keep the original graph node and its edges. */
    public void validateLegacyBinding(String code,String legacy) {
        try(Session session=driver.session()) {
            List<Record> rows=session.run("MATCH (n:Entity {name:$name}) WHERE n.nodeCode IS NULL OR n.nodeCode<>$code OR n.courseProjection=false RETURN id(n) AS id,n.nodeCode AS code",map("name",legacy,"code",code)).list();
            if(rows.size()!=1)throw bad("历史名称必须唯一定位图谱节点:"+legacy);
            String existing=rows.get(0).get("code").asString(null);
            if(existing!=null&&!code.equals(existing))throw bad("历史节点已绑定其他代码:"+existing);
        }
    }
    void bindProjection(Transaction tx,String code,String legacy) {
        List<Record> original=tx.run("MATCH (n:Entity {name:$name}) WHERE n.nodeCode IS NULL OR n.nodeCode<>$code OR n.courseProjection=false RETURN id(n) AS id,n.nodeCode AS code",map("name",legacy,"code",code)).list();
        if(original.size()!=1)throw bad("已确认历史名称不再唯一:"+legacy);
        long originalId=original.get(0).get("id").asLong();
        String bound=original.get(0).get("code").asString(null);
        if(bound!=null&&!code.equals(bound))throw bad("历史节点已有其他稳定代码");
        List<Record> projected=tx.run("MATCH (n:Entity {nodeCode:$code}) WHERE id(n)<>$id RETURN id(n) AS id,n.displayName AS displayName,n.homeSectionCode AS section",map("code",code,"id",originalId)).list();
        for(Record p:projected) {
            if(p.get("displayName").isNull()||p.get("section").isNull())throw bad("同代码节点不是课程投影，请核对后再绑定");
            long projectedId=p.get("id").asLong();
            List<Record> relationships=tx.run("MATCH (n)-[r]->(m) WHERE id(n)=$id OR id(m)=$id RETURN id(n) AS fromId,id(m) AS toId,type(r) AS type,properties(r) AS props",map("id",projectedId)).list();
            for(Record r:relationships) {
                long from=r.get("fromId").asLong(),to=r.get("toId").asLong();
                from=from==projectedId?originalId:from;to=to==projectedId?originalId:to;
                if(from==to)continue;
                String type=r.get("type").asString().replace("`","``");
                tx.run("MATCH (a),(b) WHERE id(a)=$from AND id(b)=$to MERGE (a)-[r:`"+type+"`]->(b) SET r += $props",map("from",from,"to",to,"props",r.get("props").asMap())).consume();
            }
            tx.run("MATCH (n) WHERE id(n)=$id DETACH DELETE n",map("id",projectedId)).consume();
        }
        tx.run("MATCH (n) WHERE id(n)=$id SET n.nodeCode=$code,n.courseProjection=false",map("id",originalId,"code",code)).consume();
    }
    public Map<String,Object> status() {
        List<Map<String,Object>> runs=db.rows("SELECT importRunCode,status,errorMessage,updateTime FROM course_graph_sync ORDER BY createTime DESC LIMIT 100");
        Map<String,Object> count=db.one("SELECT COUNT(*) AS pendingCount FROM course_graph_sync WHERE status<>'SYNCED'");
        int pending=count==null?0:((Number)count.get("pendingCount")).intValue();
        boolean available=false,constraint=false;
        try(Session session=driver.session()) {constraint=session.run("SHOW CONSTRAINTS YIELD name WHERE name='course_entity_node_code' RETURN name").hasNext();available=true;}catch(Exception ignored){ }
        return map("status",pending>0?"PENDING":available&&constraint?"SYNCED":"NOT_READY","pendingCount",pending,"graphAvailable",available,"constraintReady",constraint,"runs",runs);
    }
    public void requireSynced(Collection<String> nodeCodes) {
        if(nodeCodes.isEmpty())return;
        if(((Number)status().get("pendingCount")).intValue()>0)throw bad("知识图谱同步尚未完成，请先重试同步");
        try(Session session=driver.session()) {
            if(!session.run("SHOW CONSTRAINTS YIELD name WHERE name='course_entity_node_code' RETURN name").hasNext())throw bad("nodeCode唯一约束尚未建立，请先同步图谱");
            List<String> codes=new ArrayList<>(new LinkedHashSet<>(nodeCodes));
            long count=session.run("MATCH (n:Entity) WHERE n.nodeCode IN $codes RETURN count(DISTINCT n.nodeCode) AS total",Collections.singletonMap("codes",codes)).single().get("total").asLong();
            if(count!=codes.size())throw bad("相关知识节点尚未完整同步到图谱");
        }catch(com.yupi.springbootinit.exception.BusinessException e){throw e;}
        catch(Exception e){throw bad("无法确认图谱同步状态，暂不允许发布");}
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public Map<String,Object> syncPending() {
        List<Map<String,Object>> pending=db.rows("SELECT id FROM course_graph_sync WHERE status<>'SYNCED'");
        try(Session session=driver.session()) {
            // Schema runs in its own auto-commit transaction before data MERGE.
            session.run("CREATE CONSTRAINT course_entity_node_code IF NOT EXISTS FOR (n:Entity) REQUIRE n.nodeCode IS UNIQUE").consume();
            if(pending.isEmpty())return map("status","SYNCED","pendingCount",0);
            List<Map<String,Object>> nodes=db.rows("SELECT r.*,l.legacyNodeName FROM knowledge_node_registry r LEFT JOIN knowledge_node_legacy_identity l ON l.nodeCode=r.nodeCode WHERE r.isDelete=0");
            List<Map<String,Object>> relations=db.rows("SELECT * FROM knowledge_node_relation WHERE isDelete=0");
            Map<String,Map<String,Object>> sections=new HashMap<>();
            for(Map<String,Object> s:db.rows("SELECT sectionCode,parentSectionCode,title,sectionType FROM course_section WHERE isDelete=0"))sections.put(db.value(s,"sectionCode"),s);
            session.writeTransaction(tx->{
                for(Map<String,Object> n:nodes) {
                    Map<String,Object> parameters=map("code",db.value(n,"nodeCode"),"name",db.value(n,"name"),"description",db.value(n,"description"),"nodeType",db.value(n,"nodeType"),"sectionCode",db.value(n,"homeSectionCode"),"chapter",chapterTitle(db.value(n,"homeSectionCode"),sections),"category",category(n),"layer",db.value(n,"layer"));
                    String legacy=db.value(n,"legacyNodeName");
                    if(!legacy.isEmpty()) {
                        bindProjection(tx,db.value(n,"nodeCode"),legacy);
                    }
                    // Keep historical storage names on explicitly bound nodes; displayName carries current wording.
                    tx.run("MERGE (n:Entity {nodeCode:$code}) ON CREATE SET n.name=$name,n.courseProjection=true SET n.displayName=$name,n.description=$description,n.nodeType=$nodeType,n.homeSectionCode=$sectionCode,n.chapter=$chapter,n.label=$category,n.layer=$layer",parameters).consume();
                }
                for(Map<String,Object> r:relations) {
                    String type=db.value(r,"relationType");if(!Arrays.asList("REQUIRES","CONTAINS","CONTRASTS_WITH","BASED_ON","IMPLEMENTS","RELATED_TO").contains(type))throw bad("关系类型不合法");
                    tx.run("MATCH (a:Entity {nodeCode:$from}),(b:Entity {nodeCode:$to}) MERGE (a)-[r:"+type+"]->(b) SET r.reason=$reason,r.sourceRefJson=$source",map("from",db.value(r,"fromNodeCode"),"to",db.value(r,"toNodeCode"),"reason",db.value(r,"reason"),"source",db.value(r,"sourceRefJson"))).consume();
                }
                return null;
            });
            for(Map<String,Object> p:pending)db.update("UPDATE course_graph_sync SET status='SYNCED',errorMessage=NULL WHERE id=?",p.get("id"));
            return map("status","SYNCED","pendingCount",0);
        } catch(Exception e) {
            String error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();if(error.length()>1000)error=error.substring(0,1000);
            for(Map<String,Object> p:pending)db.update("UPDATE course_graph_sync SET status='PENDING',errorMessage=? WHERE id=?",error,p.get("id"));
            return map("status","PENDING","pendingCount",pending.size(),"message","图谱约束或同步未完成，请检查错误后重试","errorMessage",error);
        }
    }
    String category(Map<String,Object> node) {
        if("IMPLEMENTATION".equals(db.value(node,"layer"))||"IMPLEMENTATION".equals(db.value(node,"nodeType")))return "应用";
        return "CONCEPT".equals(db.value(node,"nodeType"))?"基础":"进阶";
    }
    String chapterTitle(String sectionCode,Map<String,Map<String,Object>> sections) {
        Set<String> seen=new HashSet<>();String cursor=sectionCode,last="未分类";
        while(!cursor.isEmpty()&&seen.add(cursor)) {
            Map<String,Object> section=sections.get(cursor);if(section==null)break;
            last=db.value(section,"title");if("CHAPTER".equals(db.value(section,"sectionType")))return last;
            cursor=db.value(section,"parentSectionCode");
        }
        return last;
    }
}
