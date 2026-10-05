package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 页面置换模拟结果 VO（WP6）
 */
@Data
public class SimulationPageVO implements Serializable {

    /**
     * 逐步回放
     */
    private List<PageStep> steps;

    /**
     * 总缺页次数
     */
    private Integer pageFaults;

    /**
     * 命中率 0-1
     */
    private Double hitRate;

    /**
     * 预测练习结果（非预测模式为 null）
     */
    private PredictionResult prediction;

    @Data
    public static class PageStep implements Serializable {
        /**
         * 访问序号（从 0 开始）
         */
        private Integer step;
        /**
         * 本次访问的页面
         */
        private Integer page;
        /**
         * 该步后内存帧内容（顺序 = FIFO 队列序 / LRU 最近使用序）
         */
        private List<Integer> frames;
        /**
         * 是否缺页
         */
        private Boolean fault;
        /**
         * 缺页时被淘汰的页（命中为 null）
         */
        private Integer evicted;
    }

    @Data
    public static class PredictionResult implements Serializable {
        /**
         * 预测缺页是否正确
         */
        private Boolean faultCorrect;
        /**
         * 淘汰页预测是否正确（该步无淘汰且学生未传淘汰页时视为正确）
         */
        private Boolean evictedCorrect;
        /**
         * 服务端计算的真实结果
         */
        private Boolean actualFault;
        private Integer actualEvicted;
        /**
         * 本次练习关联的学习事件 id（未映射到知识节点时为 null）
         */
        private Long eventId;
    }
}
