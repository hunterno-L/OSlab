package com.yupi.springbootinit.model.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;
@Data
public class KnowledgeGraphNode {
    private EntityNode entity;
    private String relationType;
    private List<EntityNode> relatedEntities;

    // 构造方法、getter和setter
    public KnowledgeGraphNode() {
        this.relatedEntities = new ArrayList<>();
    }
}
