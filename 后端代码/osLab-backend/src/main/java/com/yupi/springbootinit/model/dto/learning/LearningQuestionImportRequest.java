package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 测评题 JSON 导入请求（Phase 8 §12.2：content 为 LearningQuestionAddRequest 的 JSON 数组文本）
 */
@Data
public class LearningQuestionImportRequest implements Serializable {

    /**
     * JSON 数组文本，每个元素结构同 LearningQuestionAddRequest
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
