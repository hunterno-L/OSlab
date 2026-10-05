package com.yupi.springbootinit.model.vo.learning;

import lombok.Data;

import java.io.Serializable;

/**
 * 班级选项（Phase 8 §12.1：真实班级列表，来自 students 表聚合）
 */
@Data
public class ClassOptionVO implements Serializable {

    /**
     * 班级名称
     */
    private String className;

    /**
     * 有效绑定用户的学生数
     */
    private Integer studentCount;

    private static final long serialVersionUID = 1L;
}
