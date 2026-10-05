package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.dto.learning.SimulationCpuRequest;
import com.yupi.springbootinit.model.dto.learning.SimulationPageRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.SimulationCpuVO;
import com.yupi.springbootinit.model.vo.learning.SimulationPageVO;
import com.yupi.springbootinit.service.LearningSimulationService;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 模拟器接口（WP6）：/learning/simulation/*
 * 预测练习由服务端判分并写入学习事件，前端上报结果不被采信。
 */
@RestController
@RequestMapping("/learning/simulation")
public class LearningSimulationController {

    @Resource
    private LearningSimulationService learningSimulationService;

    @Resource
    private UserService userService;

    /**
     * CPU 调度模拟（FCFS/SJF/RR + 甘特图 + 统计 + 预测判分）
     */
    @PostMapping("/cpu")
    public BaseResponse<SimulationCpuVO> cpu(@RequestBody SimulationCpuRequest request,
                                             HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningSimulationService.runCpu(request, user));
    }

    /**
     * 页面置换模拟（FIFO/LRU + 逐步回放 + 预测判分）
     */
    @PostMapping("/page")
    public BaseResponse<SimulationPageVO> page(@RequestBody SimulationPageRequest request,
                                               HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(learningSimulationService.runPage(request, user));
    }
}
