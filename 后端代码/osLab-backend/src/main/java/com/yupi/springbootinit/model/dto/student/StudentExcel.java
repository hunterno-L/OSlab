package com.yupi.springbootinit.model.dto.student;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class StudentExcel {
    /**
     * 学号，唯一标识学生
     */
    @ExcelProperty("学号")
    private String studentNumber;

    /**
     * 学生姓名
     */
    @ExcelProperty("姓名")
    private String name;

    /**
     * 班级名称
     */
    @ExcelProperty("班级")
    private String className;
    /**
     * 用户账号
     */
    @ExcelProperty("账号")
    private String userAccount;

    /**
     * 用户密码
     */
    @ExcelProperty("密码")
    private String userPassword;
}
