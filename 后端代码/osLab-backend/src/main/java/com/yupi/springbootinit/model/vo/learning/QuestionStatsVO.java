package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 节点关联普通题统计
 */
@Data
public class QuestionStatsVO implements Serializable {
    private Integer total;
    private Integer attempted;
    private Integer unattempted;
    private Map<String, Integer> byType;
}
