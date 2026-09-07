package com.yupi.springbootinit.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.StudentsMapper;
import com.yupi.springbootinit.model.dto.student.StudentAddRequest;
import com.yupi.springbootinit.model.dto.student.StudentExcel;
import com.yupi.springbootinit.model.dto.student.StudentQueryRequest;
import com.yupi.springbootinit.model.entity.Students;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.StudentVO;
import com.yupi.springbootinit.model.vo.UserVO;
import com.yupi.springbootinit.service.StudentsService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.PasswordGenerator;
import com.yupi.springbootinit.utils.PinyinConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生管理
 */
@RestController
@RequestMapping("/student")
@Slf4j
public class StudentsController {
    @Autowired
    private StudentsService studentsService;
    @Autowired
    private UserService userService;
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StudentVO> addStudents(@RequestBody StudentAddRequest studentAddRequest)
    {
        StudentVO studentVO = saveStudent(studentAddRequest.getName(), studentAddRequest.getStudentNumber(), studentAddRequest.getClassName());
        return ResultUtils.success(studentVO);

    }
    @PostMapping("/batchAdd")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public ResponseEntity<Resource> uploadStudentsFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw  new BusinessException(ErrorCode.PARAMS_ERROR,"文件为空");
        }

        try {
            List<Map<Integer, String>> list = EasyExcel.read(file.getInputStream())
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
            Map<Integer,String> header=list.get(0);
            Integer studentIdKey = findKeyByValue(header, "学号");
            Integer nameKey = findKeyByValue(header, "姓名");
            Integer classKey = findKeyByValue(header, "班级");
            if(studentIdKey==null||nameKey==null||classKey==null)
            {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"表格列缺失");
            }
            List<StudentExcel> students=new ArrayList<>();
            for(int i=1;i<list.size();i++)
            {
                Map<Integer,String> line=list.get(i);
                StudentVO studentVO = saveStudent(line.get(nameKey), line.get(studentIdKey), line.get(classKey));
                StudentExcel studentExcel=new StudentExcel();
                BeanUtils.copyProperties(studentVO,studentExcel);
                students.add(studentExcel);
            }
            // 保存到本地文件
            String filePath = "students.xlsx";
            File saveFile = new File(filePath);
            EasyExcel.write(saveFile, StudentExcel.class)
                .sheet("学生信息")
                .head(StudentExcel.class)
                .doWrite(students);
            // 准备文件下载
            Resource resource = new FileSystemResource(saveFile);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
    }
    // 根据值获取键的方法
    public static Integer findKeyByValue(Map<Integer, String> map, String value) {
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null; // 如果没有找到对应的值，返回 null
    }

    /**
     * 保存学生信息，生成学生账号
     * @param name 姓名
     * @param studentNumber 学号
     * @param className 班级
     * @return 学生信息
     */
    private StudentVO saveStudent(String name,String studentNumber,String className)
    {
        String account = PinyinConverter.convertToPinyin(name);
        String defaultPassword = PasswordGenerator.generatePassword();
        User user = new User();
        user.setUserAccount(account);
        String encryptPassword = userService.encodePassword(defaultPassword);
        user.setUserPassword(encryptPassword);
        user.setUserName(name);
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        boolean userSave = userService.save(user);
        if(!userSave)
        {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        Students student = new Students();
        student.setName(name);
        student.setStudentNumber(studentNumber);
        student.setClassName(className);
        student.setUserId(user.getId());
        try{
            studentsService.save(student);
            StudentVO studentVO = new StudentVO();
            BeanUtils.copyProperties(student,studentVO);
            studentVO.setUserAccount(account);
            studentVO.setUserPassword(defaultPassword);
            studentVO.setUserId(user.getId());
            studentVO.setUserName(user.getUserName());
            return studentVO;
        }catch (DuplicateKeyException e)
        {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"学号重复");
        }
    }
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<StudentVO>> listStudentByPage(@RequestBody StudentQueryRequest studentQueryRequest)
    {
        long current = studentQueryRequest.getCurrent();
        long size = studentQueryRequest.getPageSize();
        Page<Students> studentsPage=studentsService.page(new Page<>(current,size),studentsService.getQueryWrapper(studentQueryRequest));
        return ResultUtils.success(getStudentVOPage(studentsPage));
    }

    private Page<StudentVO> getStudentVOPage(Page<Students> studentsPage) {
        List<Students> students = studentsPage.getRecords();
        Page<StudentVO> studentVOPage = new Page<>(studentsPage.getCurrent(),studentsPage.getSize(),studentsPage.getTotal());
        Set<Long> userIdSet = students.stream().map(Students::getUserId).collect(Collectors.toSet());
        if(userIdSet.isEmpty())
        {
            return studentVOPage;
        }
        Map<Long,List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        List<StudentVO> studentVOList = students.stream().map(student->{
            StudentVO studentVO = new StudentVO();
            BeanUtils.copyProperties(student,studentVO);
            Long userId = studentVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            if (user != null) {
                studentVO.setUserAccount(user.getUserAccount());
                studentVO.setUserName(user.getUserName());
            }
            return studentVO;
        }).collect(Collectors.toList());
        studentVOPage.setRecords(studentVOList);
        return studentVOPage;
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteStudent(Integer id)
    {
        Students student = studentsService.getById(id);
        User user = userService.getById(student.getUserId());
        userService.removeById(user.getId());
        boolean b = studentsService.removeById(id);
        return ResultUtils.success(b);
    }
    @PostMapping("/reset-password")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StudentVO> resetPassword(Integer id)
    {
        Students student = studentsService.getById(id);
        User user = userService.getById(student.getUserId());
        String defaultPassword = PasswordGenerator.generatePassword();
        String encryptPassword = userService.encodePassword(defaultPassword);
        user.setUserPassword(encryptPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", user.getId());
        userService.update(user,queryWrapper);
        StudentVO studentVO = new StudentVO();
        BeanUtils.copyProperties(student,studentVO);
        studentVO.setUserAccount(user.getUserAccount());
        studentVO.setUserPassword(defaultPassword);
        studentVO.setUserName(user.getUserName());
        return ResultUtils.success(studentVO);
    }
}
