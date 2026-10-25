package com.yupi.springbootinit.learning;

import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.service.QuestionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;

/** Shared authoritative mapping for resource discovery and terminal judge attribution. */
@Service
public class ExperimentMappingService {
    @Resource private JdbcTemplate jdbc;
    @Resource private NodeIdentityService identities;
    @Resource private QuestionService questions;
    public List<Mapping> mappings(long questionId){List<Mapping> out=new ArrayList<>();for(Map<String,Object> r:jdbc.queryForList("SELECT nodeCode,nodeName,weight FROM experiment_node WHERE questionId=? ORDER BY nodeCode",questionId)){NodeIdentityService.Identity i=identities.requireCode(r.get("nodeCode").toString());out.add(new Mapping(i.nodeCode,i.storageKey,((Number)r.get("weight")).doubleValue()));}return out;}
    public String snapshot(long id){return JSONUtil.toJsonStr(mappings(id));}
    public List<Mapping> fromSnapshot(String json,long id){return json==null?mappings(id):JSONUtil.toList(json,Mapping.class);}
    public List<Question> experiments(String codeOrName){NodeIdentityService.Identity i=identities.byStorageKey(codeOrName);if(i==null)i=identities.resolve(codeOrName);List<Long> ids=jdbc.queryForList("SELECT questionId FROM experiment_node WHERE "+(i==null?"nodeName":"nodeCode")+"=? ORDER BY questionId",Long.class,i==null?codeOrName:i.nodeCode);if(ids.isEmpty())return Collections.emptyList();return questions.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>().in(Question::getId,ids).eq(Question::getIsDelete,0).eq(Question::getResourceScope,"SHARED").eq(Question::getPublicationStatus,"PUBLISHED").orderByDesc(Question::getCreateTime));}
    public static class Mapping {
        public String nodeCode,nodeName;public double weight;
        public Mapping(){} public Mapping(String code,String name,double weight){this.nodeCode=code;this.nodeName=name;this.weight=weight;}
    }
}
