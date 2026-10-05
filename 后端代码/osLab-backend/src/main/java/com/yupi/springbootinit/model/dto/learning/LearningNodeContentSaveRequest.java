package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 节点共享学习素材保存请求（Phase 8 §12.2：按 nodeName upsert）
 */
@Data
public class LearningNodeContentSaveRequest implements Serializable {

    /**
     * 知识节点业务键（必填，须为图谱真实节点）
     */
    private String nodeName;

    /**
     * 学习素材正文（可空=清空；上限 50000 字符）
     */
    private String contentMd;

    /**
     * 关联资料 JSON 数组字符串 [{title,url}]；可空
     */
    private String resourceLinks;

    /**
     * 目标状态：DRAFT/PUBLISHED/OFFLINE（必填）
     */
    private String status;

    private static final long serialVersionUID = 1L;
}
