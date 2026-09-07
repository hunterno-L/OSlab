package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

@Data
public class LinkDTO {
    private String source; // 起点
    private String target; // 终点
    private String value; // 关系的强度或权重
    private String relationType; // 关系类型
}
