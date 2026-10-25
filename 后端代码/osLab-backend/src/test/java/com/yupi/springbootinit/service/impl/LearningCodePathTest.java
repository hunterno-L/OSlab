package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yupi.springbootinit.learning.LearningResourceResolver;
import com.yupi.springbootinit.learning.NodeIdentityService;
import com.yupi.springbootinit.mapper.LearningMasteryMapper;
import com.yupi.springbootinit.model.dto.knowledgegraph.*;
import com.yupi.springbootinit.model.entity.LearningMastery;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.service.KnowledgeGraphService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class LearningCodePathTest {
    @Test void requiresKeepsPrerequisitesFirstAndRenameKeepsHistoricalMastery() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"code-path"),LearningMastery.class);
        KnowledgeGraphService graphService=mock(KnowledgeGraphService.class);LearningMasteryMapper mapper=mock(LearningMasteryMapper.class);
        NodeIdentityService identities=mock(NodeIdentityService.class);LearningResourceResolver resources=mock(LearningResourceResolver.class);
        LearningPathServiceImpl service=new LearningPathServiceImpl();
        ReflectionTestUtils.setField(service,"knowledgeGraphService",graphService);ReflectionTestUtils.setField(service,"learningMasteryMapper",mapper);
        ReflectionTestUtils.setField(service,"nodeIdentities",identities);ReflectionTestUtils.setField(service,"resourceResolver",resources);
        NodeDTO target=node("1","os.memory.lru","LRU","最近最少使用"),pre=node("2","os.memory.locality","局部性","局部性原理"),contrast=node("3","os.memory.fifo","FIFO","先进先出");
        GraphDataDTO graph=new GraphDataDTO();graph.setNodes(List.of(target,pre,contrast));graph.setLinks(List.of(edge("1","2","REQUIRES"),edge("1","3","CONTRASTS_WITH")));
        when(graphService.getGraphSnapshot()).thenReturn(graph);
        for(NodeDTO n:graph.getNodes())when(identities.requireCode(n.getNodeCode())).thenReturn(new NodeIdentityService.Identity(n.getNodeCode(),n.getDisplayName(),n.getName()));
        when(identities.resolve("os.memory.lru")).thenReturn(new NodeIdentityService.Identity("os.memory.lru","最近最少使用","LRU"));
        LearningMastery mastery=new LearningMastery();mastery.setNodeName("LRU");mastery.setConceptEvidenceCount(1);mastery.setStatus("RED");mastery.setPKnown(.3);
        when(mapper.selectList(any())).thenReturn(List.of(mastery));User user=new User();user.setId(7L);
        LearningPathVO path=service.buildPath(user,"os.memory.lru");
        assertEquals(2,path.getItems().size());assertEquals("os.memory.locality",path.getItems().get(0).getNodeCode());
        assertEquals("os.memory.lru",path.getItems().get(1).getNodeCode());assertEquals("RED",path.getItems().get(1).getStatus());
        assertEquals("最近最少使用",path.getItems().get(1).getDisplayName());
    }
    NodeDTO node(String id,String code,String key,String title){NodeDTO n=new NodeDTO();n.setId(id);n.setNodeCode(code);n.setName(key);n.setDisplayName(title);return n;}
    LinkDTO edge(String a,String b,String type){LinkDTO e=new LinkDTO();e.setSource(a);e.setTarget(b);e.setRelationType(type);return e;}
}
