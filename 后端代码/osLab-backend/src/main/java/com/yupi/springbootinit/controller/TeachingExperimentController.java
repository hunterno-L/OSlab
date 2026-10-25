package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.dto.task.ExperimentDraftRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.task.ExperimentDraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** 教师共享实验表单维护：保留原实验 ID，发布后改稿先进入待应用修订。 */
@RestController
@RequestMapping("/teaching/experiments")
public class TeachingExperimentController {

    @Resource
    private ExperimentDraftService experimentDraftService;
    @Resource
    private UserService userService;

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping
    public BaseResponse<Map<String, Object>> create(@RequestBody ExperimentDraftRequest body,
                                                    HttpServletRequest request) {
        return ResultUtils.success(experimentDraftService.create(body, user(request)));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/{id}")
    public BaseResponse<Map<String, Object>> get(@PathVariable long id) {
        return ResultUtils.success(experimentDraftService.get(id));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PutMapping("/{id}")
    public BaseResponse<Map<String, Object>> save(@PathVariable long id,
                                                  @RequestBody ExperimentDraftRequest body,
                                                  HttpServletRequest request) {
        return ResultUtils.success(experimentDraftService.save(id, body, user(request)));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{id}/apply")
    public BaseResponse<Map<String, Object>> apply(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(experimentDraftService.apply(id, user(request)));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{id}/publish")
    public BaseResponse<Map<String, Object>> publish(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(experimentDraftService.publish(id, user(request)));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{id}/offline")
    public BaseResponse<Map<String, Object>> offline(@PathVariable long id) {
        return ResultUtils.success(experimentDraftService.offline(id));
    }

    private User user(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
