package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习活动分页结果：按活动计数与分页，而不是按事件行数。
 */
@Data
public class LearningActivityPageVO implements Serializable {

    private Long total;

    private List<LearningActivityVO> records;

    private static final long serialVersionUID = 1L;
}
