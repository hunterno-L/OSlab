package com.yupi.springbootinit.model.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * CPU 调度模拟器请求（WP6）
 * algorithm：FCFS / SJF（非抢占）/ RR
 * predictIndex >= 0 时进入“预测下一步”模式：客户端预测第 predictIndex 个时间片应运行的进程名。
 */
@Data
public class SimulationCpuRequest implements Serializable {

    /**
     * 调度算法：FCFS/SJF/RR
     */
    private String algorithm;

    /**
     * RR 时间片（algorithm=RR 时必填，>0）
     */
    private Integer timeSlice;

    /**
     * 进程列表
     */
    private List<CpuProcessInput> processes;

    /**
     * 预测模式：要预测的时间片序号（从 0 开始；null 表示非预测模式）
     */
    private Integer predictIndex;

    /**
     * 学生预测的进程名（predictIndex >= 0 时必填）
     */
    private String predictedProcess;

    /**
     * 关联知识节点业务键（可选；必须是图谱真实节点才会记学习事件，如“CPU调度”）
     */
    private String nodeName;

    @Data
    public static class CpuProcessInput implements Serializable {
        /**
         * 进程名
         */
        private String name;
        /**
         * 到达时间
         */
        private Integer arrivalTime;
        /**
         * 运行时间（burst）
         */
        private Integer burstTime;
    }
}
