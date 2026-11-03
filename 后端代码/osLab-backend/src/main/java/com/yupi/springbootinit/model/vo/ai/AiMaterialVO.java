package com.yupi.springbootinit.model.vo.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约：{@code POST /learning/ai/materials} 响应
 * {@code {materialId, chunkCount, revision}}。
 */
@Data
public class AiMaterialVO implements Serializable {

    private String materialId;

    private Integer chunkCount;

    private Integer revision;

    private String courseCode;

    private String title;

    /** PENDING / KEYWORD_ONLY / EMBEDDED / FAILED —— embedding 不可用时诚实标注。 */
    private String indexStatus;

    /** 索引使用的 embedding 模型；未向量化时为 null。 */
    private String embeddingModel;

    private String createdAt;
}
