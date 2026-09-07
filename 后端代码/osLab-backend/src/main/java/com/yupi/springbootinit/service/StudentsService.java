package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.springbootinit.model.dto.student.StudentQueryRequest;
import com.yupi.springbootinit.model.entity.Students;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 张娜
* @description 针对表【students(学生表)】的数据库操作Service
* @createDate 2025-05-04 16:28:29
*/
public interface StudentsService extends IService<Students> {

    QueryWrapper<Students> getQueryWrapper(StudentQueryRequest studentQueryRequest);
}
