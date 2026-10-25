package com.yupi.springbootinit.model.dto.learning;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;

/**
 * 普通算法演示完成一次运行后的活动记录请求。
 * 该记录只用于学习活动时间线，不参与掌握度/BKT 更新。
 */
@Data
public class LearningSimulationRunRequest implements Serializable {

    /** 算法稳定 key，如 cpu.fcfs */
    private String algorithmKey;

    /** 页面展示标题；为空时由服务端使用算法 key 兜底 */
    private String title;

    private String courseCode;

    private String unitCode;

    private String activityCode;

    /** 输入参数快照（结构化 JSON，服务端限制大小） */
    private JsonNode input;

    /** 结果摘要（结构化 JSON，服务端限制大小） */
    private JsonNode resultSummary;

    private static final long serialVersionUID = 1L;
}
