package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 路径项 VO（第三阶段 §6.3）：order 从 1 开始，前置在前
 */
@Data
public class PathItemVO implements Serializable {

    /**
     * 节点名
     */
    private String nodeName;
    private String nodeCode;
    private String displayName;

    /**
     * 顺序（1 开始）
     */
    private Integer order;

    /**
     * 本人掌握状态：GRAY/RED/YELLOW/GREEN
     */
    private String status;

    /**
     * 纳入路径的原因（人类可读）
     */
    private String reason;

    /**
     * 直接前置（仅包含路径内的前置节点）
     */
    private List<String> prerequisites = new ArrayList<>();

    /**
     * 该项的可执行行动（真实资源，无资源时 kind=NODE）
     */
    private LearningActionVO action;
}
