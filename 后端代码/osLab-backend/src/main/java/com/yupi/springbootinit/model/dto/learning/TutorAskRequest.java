package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * GraphRAG 节点导师提问请求（WP4）
 */
@Data
public class TutorAskRequest implements Serializable {

    /**
     * 当前知识节点业务键（可空，空则为全图问答）
     */
    private String nodeName;
    private String nodeCode;

    /**
     * 学生的问题
     */
    private String question;
}
