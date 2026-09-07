package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

import java.util.List;

@Data
public class GraphDataDTO {
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private List<CategoryDTO> categories;
}
