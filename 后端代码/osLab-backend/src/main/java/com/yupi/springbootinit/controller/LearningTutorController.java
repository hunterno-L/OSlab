package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.dto.learning.TutorAskRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.TutorAnswerVO;
import com.yupi.springbootinit.service.LearningTutorService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.RateLimiter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * GraphRAG 节点导师接口（WP4）：/learning/tutor/*
 */
@RestController
@RequestMapping("/learning/tutor")
public class LearningTutorController {

    /**
     * 提问限流：同一用户 1 分钟窗口内最多 6 次（AI 成本保护）
     */
    private final RateLimiter tutorLimiter = new RateLimiter(6, 60 * 1000L);

    @Resource
    private LearningTutorService learningTutorService;

    @Resource
    private UserService userService;

    /**
     * 端到端 GraphRAG 问答：answer + sources + retrievalTrace + mode
     */
    @PostMapping("/ask")
    public BaseResponse<TutorAnswerVO> ask(@RequestBody TutorAskRequest request,
                                           HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        if (!tutorLimiter.tryAcquire("tutor:" + user.getId())) {
            return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "提问过于频繁，请稍后再试");
        }
        return ResultUtils.success(learningTutorService.ask(request, user));
    }
}
