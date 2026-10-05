package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.dto.learning.TutorAskRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.TutorAnswerVO;

/**
 * GraphRAG 节点导师服务（WP4）
 * 检索链：种子节点 -> 一跳相关节点 -> 已发布题目解析 -> 本人学习事件；
 * AI 仅做 grounded 生成，引用必须可追溯；无密钥或调用失败时降级为规则建议。
 */
public interface LearningTutorService {

    /**
     * 端到端 GraphRAG 问答
     *
     * @return answer + sources + retrievalTrace + mode
     */
    TutorAnswerVO ask(TutorAskRequest request, User user);
}
