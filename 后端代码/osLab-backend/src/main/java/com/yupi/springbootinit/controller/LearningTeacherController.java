package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.vo.learning.ChapterCoverageVO;
import com.yupi.springbootinit.model.vo.learning.ClassOptionVO;
import com.yupi.springbootinit.model.vo.learning.NodeDrillVO;
import com.yupi.springbootinit.model.vo.learning.TeacherDashboardVO;
import com.yupi.springbootinit.model.vo.learning.TimelineVO;
import com.yupi.springbootinit.model.vo.learning.TeacherFollowUpsVO;
import com.yupi.springbootinit.service.LearningTeacherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 教师班级看板接口（契约 §3.5 / Phase 8 §12）：/learning/teacher/*（全部要求管理员权限）
 * 班级归属读取现有 students 表（userId/className），P0 不维护班级成员表。
 * 说明：AuthCheck AOP 仅支持方法级切点，因此逐方法标注。
 */
@RestController
@RequestMapping("/learning/teacher")
public class LearningTeacherController {

    @Resource
    private LearningTeacherService learningTeacherService;

    /**
     * 班级大屏（热力、薄弱点、题目统计均来自真实聚合）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/dashboard")
    public BaseResponse<TeacherDashboardVO> dashboard(@RequestParam("className") String className) {
        return ResultUtils.success(learningTeacherService.dashboard(className));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/follow-ups")
    public BaseResponse<TeacherFollowUpsVO> followUps(@RequestParam("className") String className,
                                                      @RequestParam("courseCode") String courseCode) {
        return ResultUtils.success(learningTeacherService.followUps(className, courseCode));
    }

    /**
     * 成长时间轴（className 必填；可按学生/节点/类型筛选，学生须属于该班级）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/timeline")
    public BaseResponse<TimelineVO> timeline(@RequestParam("className") String className,
                                             @RequestParam(value = "userId", required = false) Long userId,
                                             @RequestParam(value = "nodeName", required = false) String nodeName,
                                             @RequestParam(value = "eventType", required = false) String eventType,
                                             @RequestParam(value = "current", defaultValue = "1") long current,
                                             @RequestParam(value = "pageSize", defaultValue = "50") long pageSize) {
        return ResultUtils.success(learningTeacherService.timeline(userId, className, nodeName, eventType, current, pageSize));
    }

    /**
     * 真实可选班级列表（不硬编码）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/classes")
    public BaseResponse<List<ClassOptionVO>> classes() {
        return ResultUtils.success(learningTeacherService.classes());
    }

    /**
     * 节点下钻（映射表/标签精确关联；首次正确率与实验通过率真实口径）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/node/detail")
    public BaseResponse<NodeDrillVO> nodeDetail(@RequestParam("className") String className,
                                                @RequestParam("nodeName") String nodeName) {
        return ResultUtils.success(learningTeacherService.nodeDetail(className, nodeName));
    }

    /**
     * 章节资源完整度（节点数、已发布素材、测评覆盖、实验覆盖、未关联资源）
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/chapters/coverage")
    public BaseResponse<List<ChapterCoverageVO>> chapterCoverage() {
        return ResultUtils.success(learningTeacherService.chapterCoverage());
    }
}
