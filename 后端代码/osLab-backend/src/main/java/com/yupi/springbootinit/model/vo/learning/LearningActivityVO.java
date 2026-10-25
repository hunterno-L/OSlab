package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 一次学习活动（时间线主单位）。字段为空表示该活动没有对应上下文。
 */
@Data
public class LearningActivityVO implements Serializable {

    /** event:{groupKey|eventId} / task-accept:{id} / task-submit:{id} / run:{id} / unit:{id} */
    private String activityId;

    /** ATTEMPT/JUDGE/SIMULATION/SIMULATION_RUN/TASK_ACCEPTED/TASK_SUBMISSION/UNIT_COMPLETED */
    private String activityType;

    private String title;

    private String status;

    private String result;

    private Date occurredAt;

    private String courseCode;

    private String unitCode;

    private String activityCode;

    private String taskCode;

    private String taskItemCode;

    private Integer roundNo;

    private String resourceType;

    private Long resourceId;

    /** 仅普通演示运行记录/可确定的模拟事件下发 */
    private String algorithmKey;

    /** 可读摘要（题目标题、判题信息、任务分数等） */
    private String summary;

    /** 普通演示运行输入快照（JSON 字符串，活动详情展示/恢复输入） */
    private String inputSnapshot;

    /** 普通演示运行结果摘要（JSON 字符串） */
    private String resultSnapshot;

    /** 同一活动影响的节点证据；无节点影响时为空 */
    private List<ActivityNodeRefVO> nodeRefs;

    /** 关联学习事件 id；仅事件活动/任务题目活动有值 */
    private List<Long> evidenceRefs;

    /** 可回放的历史快照锚点事件 id（掌握证据活动） */
    private Long replayEventId;

    /** 直接回到资源的站内路径 */
    private String returnRoute;

    private static final long serialVersionUID = 1L;
}
