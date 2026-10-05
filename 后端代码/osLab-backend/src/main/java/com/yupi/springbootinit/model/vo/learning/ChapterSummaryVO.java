package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 章节汇总（快照统计）：章节名 + 该章节节点数
 */
@Data
public class ChapterSummaryVO implements Serializable {
    private String name;
    private Integer count;
}
