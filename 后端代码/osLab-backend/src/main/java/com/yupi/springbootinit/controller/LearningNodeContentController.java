package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.dto.learning.LearningNodeContentSaveRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningNodeContentVO;
import com.yupi.springbootinit.service.LearningNodeContentService;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 节点共享学习素材接口（Phase 8 §12.2）：知识维护，全部要求管理员权限。
 * 学生侧通过 /learning/mastery/node 工作台消费 PUBLISHED 素材，不经本控制器。
 */
@RestController
@RequestMapping("/learning/nodeContent")
public class LearningNodeContentController {

    @Resource
    private LearningNodeContentService learningNodeContentService;

    @Resource
    private UserService userService;

    /**
     * 按节点读取素材（编辑表单回显；不存在返回 null）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<LearningNodeContentVO> getByNode(@RequestParam("nodeName") String nodeName) {
        return ResultUtils.success(learningNodeContentService.getByNode(nodeName));
    }

    /**
     * 全部素材列表（维护视角）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/list")
    public BaseResponse<List<LearningNodeContentVO>> listAll() {
        return ResultUtils.success(learningNodeContentService.listAll());
    }

    /**
     * 保存素材（按 nodeName upsert）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/save")
    public BaseResponse<Boolean> save(@RequestBody LearningNodeContentSaveRequest request,
                                      HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(learningNodeContentService.saveContent(request, user));
    }
}
