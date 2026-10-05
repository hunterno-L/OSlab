package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.dto.knowledgegraph.GraphDataDTO;
import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.KnowledgeGraphNode;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // ============ Phase 1：图谱快照契约（§5） ============

    @Test
    void snapshot_includesAllNodes() {
        Long dbNodes = knowledgeGraphService.countByQuery("MATCH (n:Entity) RETURN count(n) AS c");
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        assertEquals(dbNodes, snapshot.getNodes().size(),
                "快照节点数必须与 Neo4j 节点总数一致（孤立节点不得丢失）");
    }

    @Test
    void snapshot_includesOrphanNodes() {
        Long orphans = knowledgeGraphService.countByQuery(
                "MATCH (n:Entity) WHERE NOT (n)--() RETURN count(n) AS c");
        assertTrue(orphans >= 1, "测试数据应至少存在 1 个孤立节点");
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        Set<String> linkedIds = new HashSet<>();
        snapshot.getLinks().forEach(l -> {
            linkedIds.add(String.valueOf(l.getSource()));
            linkedIds.add(String.valueOf(l.getTarget()));
        });
        long orphansInSnapshot = snapshot.getNodes().stream()
                .filter(n -> !linkedIds.contains(n.getId()))
                .count();
        assertEquals(orphans, orphansInSnapshot, "所有孤立节点必须出现在快照 nodes 中");
    }

    @Test
    void snapshot_linksDedupedAndDirectionStable() {
        Long distinctTriples = knowledgeGraphService.countByQuery(
                "MATCH (a:Entity)-[r]->(b:Entity) WITH id(a) AS a, id(b) AS b, type(r) AS t RETURN count(*) AS c");
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        assertEquals(distinctTriples, snapshot.getLinks().size(),
                "快照边数必须等于 Neo4j 去重后的三元组数量（方向按存储方向）");
    }

    @Test
    void snapshot_chapterDefaultsToUncategorized() {
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        assertFalse(snapshot.getNodes().isEmpty());
        for (var node : snapshot.getNodes()) {
            assertNotNull(node.getChapter(), "节点 chapter 不得为 null: " + node.getName());
            assertFalse(node.getChapter().isBlank(), "节点 chapter 不得为空: " + node.getName());
        }
    }

    @Test
    void snapshot_reportsTotals() {
        GraphDataDTO snapshot = knowledgeGraphService.getGraphSnapshot();
        assertEquals(snapshot.getNodes().size(), snapshot.getTotalNodes());
        assertEquals(snapshot.getLinks().size(), snapshot.getTotalLinks());
        assertNotNull(snapshot.getChapterSummary());
    }
}