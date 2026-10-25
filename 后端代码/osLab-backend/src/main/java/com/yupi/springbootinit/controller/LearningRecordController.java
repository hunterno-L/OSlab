package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.learning.LearningActivityService;
import com.yupi.springbootinit.model.dto.learning.LearningSimulationRunRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningActivityPageVO;
import com.yupi.springbootinit.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 学生学习活动记录：只读取当前登录用户自己的活动。
 * 掌握证据/数字孪生仍由 /learning/mastery 提供，二者不混用。
 */
@RestController
@RequestMapping("/learning/records")
public class LearningRecordController {

    @Resource
    private LearningActivityService learningActivityService;

    @Resource
    private UserService userService;

    /**
     * 按“一次学习活动”分页；服务端先归并事件组，再分页返回活动总数。
     */
    @GetMapping("/activities")
    public BaseResponse<LearningActivityPageVO> activities(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "courseCode", required = false) String courseCode,
            @RequestParam(value = "unitCode", required = false) String unitCode,
            @RequestParam(value = "nodeCode", required = false) String nodeCode,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningActivityService.activities(
                user, type, courseCode, unitCode, nodeCode, startTime, endTime, current, pageSize));
    }

    /**
     * 普通算法演示完成一次运行后记录活动；不触发掌握度更新。
     */
    @PostMapping("/simulations")
    public BaseResponse<Long> recordSimulationRun(
            @RequestBody LearningSimulationRunRequest body,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningActivityService.recordSimulationRun(user, body));
    }
}
