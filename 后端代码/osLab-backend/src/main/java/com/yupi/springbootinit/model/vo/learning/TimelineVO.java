package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成长时间轴 VO（WP5）：回放不可变事件，不重算状态
 */
@Data
public class TimelineVO implements Serializable {

    /**
     * 事件总数（筛选后）
     */
    private Long total;

    /**
     * 事件列表（按时间倒序）
     */
    private List<LearningEventVO> events;
}
