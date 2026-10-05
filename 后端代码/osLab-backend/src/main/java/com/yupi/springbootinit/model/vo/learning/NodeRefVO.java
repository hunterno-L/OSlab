package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 前置/后继节点引用（带实时掌握状态，供工作台路径区展示）
 */
@Data
public class NodeRefVO implements Serializable {
    private String name;
    private String status;
    private String chapter;
}
