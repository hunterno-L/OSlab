package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 普通题正确率统计 VO
 */
@Data
public class QuestionAccuracyVO implements Serializable {

    private Long questionId;

    private String title;

    /**
     * 题型
     */
    private String type;

    /**
     * 班级内总作答次数（含重复）
     */
    private Integer attemptCount;

    /**
     * 班级内首次作答正确数
     */
    private Integer firstCorrectCount;

    /**
     * 全部作答正确率 0-1
     */
    private Double correctRate;
}
