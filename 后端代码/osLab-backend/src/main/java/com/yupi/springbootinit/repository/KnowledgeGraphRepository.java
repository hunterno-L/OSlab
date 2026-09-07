package com.yupi.springbootinit.repository;

import com.yupi.springbootinit.model.entity.EntityNode;
import com.yupi.springbootinit.model.entity.KnowledgeGraphNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KnowledgeGraphRepository extends Neo4jRepository<EntityNode, Long> {
    List<EntityNode> findByName(String name);
    @NotNull
    List<EntityNode> findAll();
    // 查询节点及其关联节点
    @Query("MATCH (e:Entity {name: $name})-[r]-(related) WHERE type(r) = $relationshipType \n" +
            "RETURN e AS entity, collect(related) AS relatedEntities\n")
    Optional<KnowledgeGraphNode> findByNameWithRelationship(String name, String relationshipType);

    // 查询节点及其关联节点(有向)
    @Query("MATCH (e:Entity {name: $name})-[r]->(related) WHERE type(r) = $relationshipType \n" +
            "RETURN e AS entity, collect(related) AS relatedEntities\n")
    Optional<KnowledgeGraphNode> findByNameWithRelationshipDirected(String name, String relationshipType);

    @Query("MATCH (n:Entity {name: $name}) DETACH DELETE n")
    void deleteByName(String name);

    @Query("MATCH (a:Entity {name: $fromName}), (b:Entity {name: $toName}) " +
            "MERGE (a)-[:related]->(b)")
    void createRelatedRelationByName(String fromName, String toName);
    @Query("MATCH (a:Entity {name: $fromName}), (b:Entity {name: $toName}) " +
            "MERGE (a)-[:contain]->(b)")
    void createContainRelationByName(String fromName, String toName);
    @Query("MATCH (a:Entity {name: $fromName}), (b:Entity {name: $toName}) " +
            "MERGE (a)-[:dependent]->(b)")
    void createDependentRelationByName(String fromName, String toName);
    @Query("MATCH (a:Entity {name: $fromName}), (b:Entity {name: $toName}) " +
            "MERGE (a)-[:implements]->(b)")
    void createImplementsRelationByName(String fromName, String toName);

}
