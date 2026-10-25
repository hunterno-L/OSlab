package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 普通题与知识节点映射
 * @TableName learning_question_node
 */
@TableName(value = "learning_question_node")
@Data
public class LearningQuestionNode implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 普通题 id
     */
    private Long questionId;

    /**
     * 知识节点业务键（Neo4j EntityNode.name）
     */
    private String nodeName;
    private String nodeCode;
    private String role;

    /**
     * 对节点掌握度的权重 0-1
     */
    private Double weight;

    /**
     * 创建时间
     */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
