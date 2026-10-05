package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * GraphRAG 节点导师回答 VO（WP4）
 * mode：AI（模型生成）/ FALLBACK（无密钥或调用失败，返回检索证据与规则建议）
 */
@Data
public class TutorAnswerVO implements Serializable {

    /**
     * 回答文本（AI 模式）；FALLBACK 模式为规则生成的确定性建议
     */
    private String answer;

    /**
     * 引用来源（回答中引用必须都存在于本列表）
     */
    private List<TutorSourceVO> sources;

    /**
     * 检索轨迹（每步检索说明，可追溯）
     */
    private List<String> retrievalTrace;

    /**
     * AI / FALLBACK
     */
    private String mode;
}
