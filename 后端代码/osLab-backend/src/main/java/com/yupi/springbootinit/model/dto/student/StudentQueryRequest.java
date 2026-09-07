package com.yupi.springbootinit.model.dto.student;

import com.yupi.springbootinit.common.PageRequest;
import lombok.Data;

import java.io.Serializable;
@Data
public class StudentQueryRequest extends PageRequest implements Serializable {
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
