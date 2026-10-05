package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

/** 管理员维护节点所属课程章节，不修改节点本身或关联关系。 */
@Data
public class UpdateNodeChapterRequest {
    private String nodeName;
    private String chapter;
}
