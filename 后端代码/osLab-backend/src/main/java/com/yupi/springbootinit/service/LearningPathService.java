package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;

/**
 * 补救学习路径服务（第三阶段 §6.3）
 *
 * <p>基于同一份图谱快照建立多前置邻接表，输出拓扑有序的行动路径；
 * 检测依赖环并返回涉及节点，不删除真实边。
 */
public interface LearningPathService {

    /**
     * 计算指定目标节点的补救学习路径（前置在前、目标在后）
     *
     * @param user           当前登录用户
     * @param targetNodeName 目标节点名
     */
    LearningPathVO buildPath(User user, String targetNodeName);
}
