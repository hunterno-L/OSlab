package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

@Data
public class NodeDTO {
    private String id;
    private String name;
    private String category; // 分类索引
    private String description; // 节点的详细信息
    private int value; // 可能用于表示节点的重要性
    private String symbol; // 形状（可选）
}
