package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.KnowledgeGraphNode;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class KnowledgeGraphServiceTest {
    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    @Test
    void findByName() {
        List<EntityNode> nodes = knowledgeGraphService.findByName("内存管理");
        for (EntityNode node : nodes) {
            System.out.println("名字："+node.getDescription()+"id:"+node.getId());
        }
        assertNotNull(nodes);
    }
    @Test
    void findRelations()
    {
        List<KnowledgeGraphNode> graph = knowledgeGraphService.getKnowledgeGraph("内存管理");
        System.out.println("节点:"+graph.size());
        assertNotNull(graph);
    }
    @Test
    void testCql()
    {
        // 创建一个 Neo4j 驱动实例
        // 根据实际设置修改 URI 和凭证

        // 创建一个会话
        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "111111")); Session session = driver.session()) {
            // 运行查询
            Result result = session.run("MATCH (e:Entity {name: \"内存管理\"})-[r]-(related) \n" +
                    "RETURN e AS entity, collect(r) AS relationships, collect(related) AS relatedEntities\n");

            // 遍历结果并打印
            while (result.hasNext()) {
                Record record = result.next();
                System.out.println(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 关闭驱动实例
    }
    @Test
    void testContain()
    {
        KnowledgeGraphNode node = knowledgeGraphService.findByNameWithRelationship("MMU","contain");
        System.out.println("包含关系："+node.getRelatedEntities().size());
        GraphDataDTO graphDataDTO = knowledgeGraphService.convertToEChartsFormat(node);
        System.out.println(graphDataDTO);
        assertNotNull(node);
    }
    @Test
    void testAllNode()
    {
        List<EntityNode> allEntityNode = knowledgeGraphService.getAllEntityNode();
        for(EntityNode node:allEntityNode)
        {
            System.out.println(node.getName());
        }
        assertNotNull(allEntityNode);
    }
}