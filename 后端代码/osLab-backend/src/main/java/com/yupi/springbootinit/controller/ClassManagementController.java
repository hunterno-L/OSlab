package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.ClassManagementService;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** 班级管理：教师维护班级，学生使用邀请码加入。 */
@RestController
public class ClassManagementController {
    @Resource private ClassManagementService classes;
    @Resource private UserService userService;

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/teaching/classes")
    public BaseResponse<List<Map<String, Object>>> teacherClasses(HttpServletRequest request) {
        return ResultUtils.success(classes.listTeacherClasses(userService.getLoginUser(request)));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/teaching/classes")
    public BaseResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResultUtils.success(classes.create(userService.getLoginUser(request), body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PatchMapping("/teaching/classes/{classId}")
    public BaseResponse<Map<String, Object>> update(@PathVariable long classId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResultUtils.success(classes.update(userService.getLoginUser(request), classId, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/teaching/classes/{classId}/rotate-code")
    public BaseResponse<Map<String, Object>> rotateCode(@PathVariable long classId, HttpServletRequest request) {
        return ResultUtils.success(classes.rotateCode(userService.getLoginUser(request), classId));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @DeleteMapping("/teaching/classes/{classId}")
    public BaseResponse<Boolean> delete(@PathVariable long classId, HttpServletRequest request) {
        classes.delete(userService.getLoginUser(request), classId);
        return ResultUtils.success(true);
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/teaching/classes/{classId}/members")
    public BaseResponse<List<Map<String, Object>>> members(@PathVariable long classId, HttpServletRequest request) {
        return ResultUtils.success(classes.members(userService.getLoginUser(request), classId));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @DeleteMapping("/teaching/classes/{classId}/members/{userId}")
    public BaseResponse<Boolean> removeMember(@PathVariable long classId, @PathVariable long userId, HttpServletRequest request) {
        classes.removeMember(userService.getLoginUser(request), classId, userId);
        return ResultUtils.success(true);
    }

    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @GetMapping("/classes/mine")
    public BaseResponse<List<Map<String, Object>>> myClasses(HttpServletRequest request) {
        return ResultUtils.success(classes.listMyClasses(userService.getLoginUser(request)));
    }

    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/classes/join")
    public BaseResponse<Map<String, Object>> join(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ResultUtils.success(classes.join(userService.getLoginUser(request), body));
    }

    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/classes/{classId}/leave")
    public BaseResponse<Boolean> leave(@PathVariable long classId, HttpServletRequest request) {
        classes.leave(userService.getLoginUser(request), classId);
        return ResultUtils.success(true);
    }
}
