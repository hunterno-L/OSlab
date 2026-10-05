package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * CPU 调度模拟结果 VO（WP6）
 */
@Data
public class SimulationCpuVO implements Serializable {

    /**
     * 甘特图片段 [{process, start, end}]（时间单位与输入一致）
     */
    private List<CpuSegment> gantt;

    /**
     * 各进程统计
     */
    private List<CpuProcessStat> processStats;

    /**
     * 平均等待时间
     */
    private Double avgWaitingTime;

    /**
     * 平均周转时间
     */
    private Double avgTurnaroundTime;

    /**
     * 预测练习结果（非预测模式为 null）
     */
    private PredictionResult prediction;

    @Data
    public static class CpuSegment implements Serializable {
        private String process;
        private Integer start;
        private Integer end;
    }

    @Data
    public static class CpuProcessStat implements Serializable {
        private String name;
        private Integer arrivalTime;
        private Integer burstTime;
        private Integer startTime;
        private Integer completionTime;
        private Integer waitingTime;
        private Integer turnaroundTime;
    }

    @Data
    public static class PredictionResult implements Serializable {
        /**
         * 学生预测是否正确
         */
        private Boolean correct;
        /**
         * 服务端计算的正确答案（该时间片应运行的进程名）
         */
        private String expectedProcess;
        /**
         * 学生预测的进程名
         */
        private String predictedProcess;
        /**
         * 本次练习关联的学习事件 id（未映射到知识节点时为 null）
         */
        private Long eventId;
    }
}
