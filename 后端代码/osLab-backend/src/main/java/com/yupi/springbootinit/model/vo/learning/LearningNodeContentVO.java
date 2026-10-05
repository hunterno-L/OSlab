package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 节点共享学习素材 VO（教学管理编辑视角）
 */
@Data
public class LearningNodeContentVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 知识节点业务键
     */
    private String nodeName;

    /**
     * 学习素材正文
     */
    private String contentMd;

    /**
     * 关联资料 JSON 数组 [{title,url}]
     */
    private String resourceLinks;

    /**
     * 状态：DRAFT/PUBLISHED/OFFLINE
     */
    private String status;

    /**
     * 最近更新时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
