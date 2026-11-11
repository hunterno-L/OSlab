package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.LearningPathVO;
import com.yupi.springbootinit.model.vo.learning.MasteryNodeVO;
import com.yupi.springbootinit.model.vo.learning.MasterySnapshotVO;
import com.yupi.springbootinit.model.vo.learning.NodeWorkbenchVO;
import com.yupi.springbootinit.model.vo.learning.PersonalGraphVO;
import com.yupi.springbootinit.model.vo.learning.RecommendationVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.model.vo.learning.WeakNodeVO;
import com.yupi.springbootinit.service.LearningMasteryService;
import com.yupi.springbootinit.service.LearningPathService;
import com.yupi.springbootinit.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 个人图谱与掌握度接口（WP3）：/learning/mastery/*
 */
@RestController
@RequestMapping("/learning/mastery")
public class LearningMasteryController {

    @Resource
    private LearningMasteryService learningMasteryService;

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private UserService userService;
    @Resource
    private com.yupi.springbootinit.learning.NodeIdentityService nodeIdentities;

    /**
     * 补救学习路径（第三阶段 §6.3）：多前置拓扑排序 + 真实资源行动，
     * 图谱路径投影、工作台与课程目录共用
     */
    @GetMapping("/path")
    public BaseResponse<LearningPathVO> path(@RequestParam(value="targetNodeName",required=false) String targetNodeName,
                                             @RequestParam(value="targetNodeCode",required=false) String targetNodeCode,
                                             @RequestParam(value="courseCode",required=false) String courseCode,
                                             HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningPathService.buildPath(user,
                StringUtils.isNotBlank(targetNodeCode) ? nodeIdentities.requireCode(targetNodeCode).nodeCode : targetNodeName, courseCode));
    }

    /**
     * 个人知识图谱（当前登录学生的掌握度叠加视角）
     */
    @GetMapping("/graph")
    public BaseResponse<PersonalGraphVO> personalGraph(@RequestParam(value="courseCode",required=false) String courseCode, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningMasteryService.personalGraph(user, courseCode));
    }

    /**
     * 节点工作台：概念、前置、关联题目、事件证据、推荐
     */
    @GetMapping("/node")
    public BaseResponse<NodeWorkbenchVO> nodeWorkbench(@RequestParam(value="nodeName",required=false) String nodeName,
                                                       @RequestParam(value="nodeCode",required=false) String nodeCode,
                                                       HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        String identity = StringUtils.isNotBlank(nodeCode) ? nodeIdentities.requireCode(nodeCode).nodeCode : nodeName;
        return ResultUtils.success(learningMasteryService.nodeWorkbench(user, identity));
    }

    /**
     * 学习推荐（含原因）
     */
    @GetMapping("/recommend")
    public BaseResponse<List<RecommendationVO>> recommendations(@RequestParam(value="courseCode",required=false) String courseCode, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningMasteryService.recommendations(user, courseCode));
    }

    /**
     * 本人学习时间线（§11.1）：只查当前登录用户，不接收外部 userId。
     * startTime/endTime 格式 yyyy-MM-dd HH:mm:ss。
     */
    @GetMapping("/timeline")
    public BaseResponse<TimelineVO> myTimeline(@RequestParam(value = "nodeName", required = false) String nodeName,
                                               @RequestParam(value = "nodeCode", required = false) String nodeCode,
                                               @RequestParam(value = "eventType", required = false) String eventType,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime,
                                               @RequestParam(value = "current", defaultValue = "1") long current,
                                               @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                               HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningMasteryService.myTimeline(
                user, StringUtils.isNotBlank(nodeCode) ? nodeIdentities.requireCode(nodeCode).storageKey : nodeName, eventType, startTime, endTime, current, pageSize));
    }

    /**
     * 历史快照回放（§11.2）：按某事件时点生成图谱投影；只读，不接收他人事件 id。
     */
    @GetMapping("/snapshot")
    public BaseResponse<MasterySnapshotVO> snapshot(@RequestParam("atEventId") Long atEventId,
                                                    HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningMasteryService.replaySnapshot(user, atEventId));
    }

    /**
     * 待巩固节点（§11.3）：薄弱节点 + 失败归类（当前失误/历史失误/已纠正/无失败）。
     */
    @GetMapping("/weakness")
    public BaseResponse<List<WeakNodeVO>> weakness(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(learningMasteryService.weaknessNodes(user));
    }
}
