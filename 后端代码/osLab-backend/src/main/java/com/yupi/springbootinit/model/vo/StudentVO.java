package com.yupi.springbootinit.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 学生信息
 */
@Data
public class StudentVO {
    private Integer id;

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

    /**
     * 用户ID，关联用户表
     */
    private Long userId;
    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 创建时间
     */
    private Date createTime;
}
