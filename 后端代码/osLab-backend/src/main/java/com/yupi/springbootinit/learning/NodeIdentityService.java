package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yupi.springbootinit.course.CourseGraphSyncService;
import javax.annotation.Resource;
import java.util.*;

/** The only bridge between public codes and historical name-based storage identities. */
@Service
public class NodeIdentityService {
    @Resource private JdbcTemplate jdbc;
    @Resource private CourseGraphSyncService graphSync;
    public Identity requireCode(String code) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.nodeCode,r.name,l.legacyNodeName FROM knowledge_node_registry r LEFT JOIN knowledge_node_legacy_identity l ON l.nodeCode=r.nodeCode WHERE r.nodeCode=? AND r.isDelete=0",code);
        if(rows.size()!=1)throw bad("未知 nodeCode: "+code);
        Map<String,Object> r=rows.get(0);return new Identity(code,r.get("name").toString(),r.get("legacyNodeName")==null?code:r.get("legacyNodeName").toString());
    }
    public Identity resolve(String codeOrName) {
        List<String> codes=jdbc.queryForList("SELECT nodeCode FROM knowledge_node_registry WHERE nodeCode=? AND isDelete=0",String.class,codeOrName);
        if(codes.size()==1)return requireCode(codes.get(0));
        codes=jdbc.queryForList("SELECT DISTINCT r.nodeCode FROM knowledge_node_registry r LEFT JOIN knowledge_node_legacy_identity l ON l.nodeCode=r.nodeCode WHERE r.isDelete=0 AND (r.name=? OR l.legacyNodeName=? OR JSON_CONTAINS(COALESCE(r.aliasesJson,JSON_ARRAY()),JSON_QUOTE(?)))",String.class,codeOrName,codeOrName,codeOrName);
        if(codes.size()>1)throw bad("节点名称不唯一，请使用 nodeCode: "+codeOrName);
        return codes.isEmpty()?null:requireCode(codes.get(0));
    }
    /** Internal storage keys may equal a public display name; prefer the explicit binding. */
    public Identity byStorageKey(String key) {
        List<String> bound=jdbc.queryForList("SELECT nodeCode FROM knowledge_node_legacy_identity WHERE legacyNodeName=?",String.class,key);
        if(bound.size()==1)return requireCode(bound.get(0));
        List<String> exact=jdbc.queryForList("SELECT nodeCode FROM knowledge_node_registry WHERE nodeCode=? AND isDelete=0",String.class,key);
        return exact.size()==1?requireCode(exact.get(0)):null;
    }
    /** Project stable codes on history at read time; the historical ledger is never rewritten. */
    public void decorateEvents(List<com.yupi.springbootinit.model.vo.learning.LearningEventVO> events) {
        if(events==null||events.isEmpty())return;Map<String,String> codes=new HashMap<>();
        for(Map<String,Object> row:jdbc.queryForList("SELECT nodeCode,legacyNodeName FROM knowledge_node_legacy_identity"))codes.put(row.get("legacyNodeName").toString(),row.get("nodeCode").toString());
        for(com.yupi.springbootinit.model.vo.learning.LearningEventVO e:events)if(e.getNodeCode()==null)e.setNodeCode(codes.get(e.getNodeName()));
    }
    /** Explicit administrator decision; never merge by similar names, aliases or tags. */
    @Transactional(rollbackFor=Exception.class)
    public Identity bindLegacy(String code,String name) {
        requireCode(code);if(name==null||name.trim().isEmpty())throw bad("历史存储名不能为空");
        Long codedEvidence=jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE nodeName=?",Long.class,code);
        Long codedState=jdbc.queryForObject("SELECT COUNT(*) FROM learning_mastery WHERE nodeName=?",Long.class,code);
        if(!code.equals(name)&&((codedEvidence!=null&&codedEvidence>0)||(codedState!=null&&codedState>0)))throw bad("该代码已积累学习证据；不能事后切换历史存储身份");
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT * FROM knowledge_node_legacy_identity WHERE nodeCode=? OR legacyNodeName=?",code,name);
        for(Map<String,Object> row:existing)if(!code.equals(row.get("nodeCode"))||!name.equals(row.get("legacyNodeName")))throw bad("身份已绑定；为保留历史不得重绑定");
        if(graphSync!=null)graphSync.validateLegacyBinding(code,name);
        Long conflicting=jdbc.queryForObject("SELECT COUNT(*) FROM learning_mastery WHERE nodeName=? AND nodeCode IS NOT NULL AND nodeCode<>?",Long.class,name,code);
        if(conflicting!=null&&conflicting>0)throw bad("旧掌握状态已关联其他代码");
        jdbc.update("INSERT IGNORE INTO knowledge_node_legacy_identity(nodeCode,legacyNodeName) VALUES(?,?)",code,name);
        // Add identity metadata only; snapshots, display names, IDs and numeric model state remain untouched.
        jdbc.update("UPDATE learning_mastery SET nodeCode=? WHERE nodeName=? AND nodeCode IS NULL",code,name);
        jdbc.update("UPDATE learning_question_node SET nodeCode=? WHERE nodeName=? AND nodeCode IS NULL",code,name);
        jdbc.update("UPDATE learning_question_node SET nodeName=? WHERE nodeCode=?",name,code);
        jdbc.update("UPDATE experiment_node SET nodeName=? WHERE nodeCode=?",name,code);
        long syncId=IdWorker.getId();
        jdbc.update("INSERT INTO course_graph_sync(id,importRunCode,status) VALUES(?,?,'PENDING')",syncId,"bind."+syncId);
        if(graphSync!=null&&TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){graphSync.syncPending();}
            });
        return requireCode(code);
    }
    public String storageKey(String codeOrName) {Identity i=resolve(codeOrName);return i==null?codeOrName:i.storageKey;}
    public List<String> displayNames(Collection<String> keys) {
        List<String> out=new ArrayList<>();for(String key:keys){Identity i=byStorageKey(key);out.add(i==null?key:i.name);}return out;
    }
    private BusinessException bad(String message){return new BusinessException(ErrorCode.PARAMS_ERROR,message);}
    public static class Identity {
        public final String nodeCode,name,storageKey;
        public Identity(String code,String name,String storageKey){this.nodeCode=code;this.name=name;this.storageKey=storageKey;}
    }
}
