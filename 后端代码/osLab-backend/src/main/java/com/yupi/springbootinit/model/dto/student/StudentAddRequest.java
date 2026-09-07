package com.yupi.springbootinit.model.dto.student;

import lombok.Data;

/**
 * 创建学生请求
 */
@Data
public class StudentAddRequest {
    /**
     * 学号，唯一标识学生
     */
    private String studentNumber;

    /**
     * 学生姓名
     */
    private String name;

    /**
     * 班级名称
     */
    private String className;
}
