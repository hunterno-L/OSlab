package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.dto.student.StudentQueryRequest;
import com.yupi.springbootinit.model.entity.Students;
import com.yupi.springbootinit.service.StudentsService;
import com.yupi.springbootinit.mapper.StudentsMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author 张娜
* @description 针对表【students(学生表)】的数据库操作Service实现
* @createDate 2025-05-04 16:28:29
*/
@Service
public class StudentsServiceImpl extends ServiceImpl<StudentsMapper, Students>
    implements StudentsService{

    @Override
    public QueryWrapper<Students> getQueryWrapper(StudentQueryRequest studentQueryRequest) {
        if(studentQueryRequest==null)
        {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请求参数为空");
        }
        QueryWrapper<Students> queryWrapper=new QueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(studentQueryRequest.getStudentNumber()),"studentNumber",studentQueryRequest.getStudentNumber());
        queryWrapper.like(StringUtils.isNotBlank(studentQueryRequest.getClassName()),"className",studentQueryRequest.getClassName());
        queryWrapper.like(StringUtils.isNotBlank(studentQueryRequest.getName()),"name",studentQueryRequest.getName());
        return queryWrapper;
    }
}




