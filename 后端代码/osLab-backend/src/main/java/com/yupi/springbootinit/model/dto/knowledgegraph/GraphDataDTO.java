package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

import java.util.List;

@Data
public class GraphDataDTO {
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private List<CategoryDTO> categories;
    private Integer totalNodes; // 快照节点总数
    private Integer totalLinks; // 快照去重后关系总数
    private java.util.Map<String, Integer> chapterSummary; // 章节 -> 节点数
}
