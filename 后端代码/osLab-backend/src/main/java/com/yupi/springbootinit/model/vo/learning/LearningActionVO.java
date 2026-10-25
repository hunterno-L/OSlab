package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习行动 VO（第三阶段 §6.3）：路径项可执行的下一步，必须指向真实资源
 */
@Data
public class LearningActionVO implements Serializable {

    /**
     * 行动类型：NODE（知识学习）/ ASSESSMENT（知识测评）/ EXPERIMENT（编程实验）/ SIMULATION（交互模拟）
     */
    private String kind;

    /**
     * 目标节点（所有行动都携带节点上下文）
     */
    private String nodeName;
    private String nodeCode;
    private String unitCode;
    private String courseCode;

    /**
     * 资源 id：EXPERIMENT=questionId；其余为空
     */
    private Long resourceId;

    /**
     * SIMULATION 的可选算法键（与前端 simulation/catalog.ts 一致）
     */
    private List<String> algorithms;

    /**
     * 章节上下文（测评范围选择用）
     */
    private String chapter;
}
