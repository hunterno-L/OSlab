package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.dto.knowledgegraph.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;

/** Overlay registered course nodes without requiring a Neo4j write or guessing a legacy match. */
@Service
public class RegistryGraphService {
    @Resource private JdbcTemplate jdbc;
    @Resource private NodeIdentityService identities;
    public void overlay(GraphDataDTO graph){List<NodeDTO> nodes=graph.getNodes()==null?new ArrayList<>():new ArrayList<>(graph.getNodes());Map<String,String> ids=new HashMap<>();
        for(Map<String,Object> r:jdbc.queryForList("SELECT * FROM knowledge_node_registry WHERE isDelete=0")){String code=r.get("nodeCode").toString();NodeIdentityService.Identity identity=identities.requireCode(code);NodeDTO target=null;
            for(NodeDTO n:nodes)if(code.equals(n.getNodeCode())){target=n;break;}
            if(target==null&&!code.equals(identity.storageKey)){List<NodeDTO> candidates=new ArrayList<>();for(NodeDTO n:nodes)if(identity.storageKey.equals(n.getName()))candidates.add(n);if(candidates.size()==1)target=candidates.get(0);}
            if(target==null){target=new NodeDTO();target.setId("code:"+code);nodes.add(target);}
            target.setNodeCode(code);target.setName(identity.storageKey);target.setDisplayName(identity.name);target.setNodeType(String.valueOf(r.get("nodeType")));target.setLayer(r.get("layer")==null?null:r.get("layer").toString());target.setHomeSectionCode(r.get("homeSectionCode")==null?null:r.get("homeSectionCode").toString());
            if(target.getCategory()==null)target.setCategory("IMPLEMENTATION".equals(target.getNodeType())?"应用":"ALGORITHM".equals(target.getNodeType())?"进阶":"基础");target.setDescription(r.get("description")==null?null:r.get("description").toString());
            target.setChapter(chapter(target.getHomeSectionCode()));ids.put(code,target.getId());}
        List<LinkDTO> links=graph.getLinks()==null?new ArrayList<>():new ArrayList<>(graph.getLinks());Map<String,LinkDTO> existing=new HashMap<>();for(LinkDTO l:links)existing.put(l.getSource()+"|"+canonical(l.getRelationType())+"|"+l.getTarget(),l);
        for(Map<String,Object> r:jdbc.queryForList("SELECT * FROM knowledge_node_relation WHERE isDelete=0")){String from=ids.get(r.get("fromNodeCode")),to=ids.get(r.get("toNodeCode")),type=String.valueOf(r.get("relationType"));if(from==null||to==null)continue;String key=from+"|"+type+"|"+to;LinkDTO l=existing.get(key);if(l==null){l=new LinkDTO();l.setSource(from);l.setTarget(to);links.add(l);existing.put(key,l);}l.setRelationType(type);l.setValue(type);}
        graph.setNodes(nodes);graph.setLinks(links);graph.setTotalNodes(nodes.size());graph.setTotalLinks(links.size());Map<String,Integer> chapters=new LinkedHashMap<>();Set<String> categories=new LinkedHashSet<>();for(NodeDTO n:nodes){chapters.merge(n.getChapter()==null?"未分类":n.getChapter(),1,Integer::sum);if(n.getCategory()!=null)categories.add(n.getCategory());}graph.setChapterSummary(chapters);List<CategoryDTO> cat=new ArrayList<>();for(String c:categories)cat.add(new CategoryDTO(c));graph.setCategories(cat);
    }
    private String canonical(String type){return "dependent".equals(type)?"REQUIRES":"contain".equals(type)?"CONTAINS":"implements".equals(type)?"IMPLEMENTS":"related".equals(type)?"RELATED_TO":type;}
    private String chapter(String code){String fallback="未分类";Set<String> seen=new HashSet<>();while(code!=null&&seen.add(code)){List<Map<String,Object>> rows=jdbc.queryForList("SELECT title,sectionType,parentSectionCode FROM course_section WHERE sectionCode=? AND isDelete=0",code);if(rows.isEmpty())break;Map<String,Object> row=rows.get(0);fallback=row.get("title").toString();if("CHAPTER".equals(row.get("sectionType")))return fallback;code=row.get("parentSectionCode")==null?null:row.get("parentSectionCode").toString();}return fallback;}
}
