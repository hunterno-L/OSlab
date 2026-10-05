package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 页面置换模拟器请求（WP6）
 * algorithm：FIFO / LRU
 * predictStep >= 0 时进入“预测下一步”模式：客户端预测第 predictStep 次访问是否缺页及淘汰页。
 */
@Data
public class SimulationPageRequest implements Serializable {

    /**
     * 置换算法：FIFO/LRU
     */
    private String algorithm;

    /**
     * 物理块数（>0）
     */
    private Integer frames;

    /**
     * 页面访问序列
     */
    private List<Integer> accessSequence;

    /**
     * 预测模式：要预测的访问步序号（从 0 开始；null 表示非预测模式）
     */
    private Integer predictStep;

    /**
     * 学生预测该步是否缺页
     */
    private Boolean predictedFault;

    /**
     * 学生预测的淘汰页（仅当该步缺页时需要；无淘汰传 null）
     */
    private Integer predictedEvicted;

    /**
     * 关联知识节点业务键（可选；必须是图谱真实节点才会记学习事件，如“页面置换”）
     */
    private String nodeName;
}
