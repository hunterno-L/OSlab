package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.learning.LearningNodeContentSaveRequest;
import com.yupi.springbootinit.model.entity.LearningNodeContent;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningNodeContentVO;

import java.util.List;

/**
 * 节点共享学习素材服务（Phase 8 §12.2）：知识维护编辑，学生侧仅消费 PUBLISHED
 */
public interface LearningNodeContentService extends IService<LearningNodeContent> {

    /**
     * 按节点读取素材（任意状态；不存在返回 null）
     */
    LearningNodeContentVO getByNode(String nodeName);

    /**
     * 全部素材列表（维护视角）
     */
    List<LearningNodeContentVO> listAll();

    /**
     * 保存素材（按 nodeName upsert；须为图谱真实节点）
     */
    boolean saveContent(LearningNodeContentSaveRequest request, User user);
}
