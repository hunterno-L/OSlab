package com.yupi.springbootinit.model.entity;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Entity")
@Data
public class EntityNode {
    @Id
    @GeneratedValue
    private Long id;
    private String identity;
    private String name;
    private String description;
    private String label;
    /**
     * 所属章节（课程维度，区别于 label 表示的概念/算法/功能分类）。
     * 历史节点可能无该属性，读取时缺省为"未分类"。
     */
    private String chapter;
}
