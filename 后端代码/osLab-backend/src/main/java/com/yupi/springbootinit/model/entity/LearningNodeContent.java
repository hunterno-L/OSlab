package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 节点共享学习素材（教学管理维护，学生侧仅消费 PUBLISHED）
 * @TableName learning_node_content
 */
@TableName(value = "learning_node_content")
@Data
public class LearningNodeContent implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 知识节点业务键（唯一）
     */
    private String nodeName;

    /**
     * 学习素材正文（纯文本/轻量 markdown）
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
     * 最近维护人用户 id
     */
    private Long updateUserId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
