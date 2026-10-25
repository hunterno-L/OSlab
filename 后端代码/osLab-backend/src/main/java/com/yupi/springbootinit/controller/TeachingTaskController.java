package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.dto.task.TaskAudienceRequest;
import com.yupi.springbootinit.model.dto.task.TaskReturnRequest;
import com.yupi.springbootinit.model.dto.task.TaskReviewRequest;
import com.yupi.springbootinit.model.dto.task.TaskSaveRequest;
import com.yupi.springbootinit.model.dto.task.TaskSettingsRequest;
import com.yupi.springbootinit.model.dto.task.TaskVersionRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.task.TaskContentService;
import com.yupi.springbootinit.task.TeacherTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** 教师学习任务接口（ADMIN 承载教师能力，服务层再校验创建者）。 */
@RestController
@RequestMapping("/teaching/tasks")
public class TeachingTaskController {

    @Resource
    private TeacherTaskService teacherTaskService;
    @Resource
    private TaskContentService taskContentService;
    @Resource
    private UserService userService;

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping
    public BaseResponse<Map<String, Object>> list(@RequestParam(required = false) String title,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String className,
                                                  @RequestParam(defaultValue = "false") boolean archived,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.list(user(request), title, status, className,
                archived, current, pageSize));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping
    public BaseResponse<Map<String, Object>> create(@RequestBody TaskSaveRequest body,
                                                    HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.create(user(request), body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/{taskCode}")
    public BaseResponse<Map<String, Object>> get(@PathVariable String taskCode, HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.get(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PutMapping("/{taskCode}")
    public BaseResponse<Map<String, Object>> save(@PathVariable String taskCode,
                                                  @RequestBody TaskSaveRequest body,
                                                  HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.save(user(request), taskCode, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/audience-preview")
    public BaseResponse<Map<String, Object>> audiencePreview(@PathVariable String taskCode,
                                                             @RequestBody(required = false) TaskAudienceRequest body,
                                                             HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.audiencePreview(user(request), taskCode, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/preview")
    public BaseResponse<Map<String, Object>> preview(@PathVariable String taskCode,
                                                     HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.preview(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/publish")
    public BaseResponse<Map<String, Object>> publish(@PathVariable String taskCode,
                                                     @RequestBody(required = false) TaskVersionRequest body,
                                                     HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.publish(user(request), taskCode, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PatchMapping("/{taskCode}/settings")
    public BaseResponse<Map<String, Object>> settings(@PathVariable String taskCode,
                                                      @RequestBody TaskSettingsRequest body,
                                                      HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.settings(user(request), taskCode, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/recipients")
    public BaseResponse<Map<String, Object>> addRecipients(@PathVariable String taskCode,
                                                           @RequestBody TaskAudienceRequest body,
                                                           HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.addRecipients(user(request), taskCode, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/close")
    public BaseResponse<Map<String, Object>> close(@PathVariable String taskCode, HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.close(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/duplicate")
    public BaseResponse<Map<String, Object>> duplicate(@PathVariable String taskCode, HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.duplicate(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/archive")
    public BaseResponse<Map<String, Object>> archive(@PathVariable String taskCode, HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.archive(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @DeleteMapping("/{taskCode}")
    public BaseResponse<Map<String, Object>> delete(@PathVariable String taskCode, HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.deleteDraft(user(request), taskCode));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/{taskCode}/recipients")
    public BaseResponse<Map<String, Object>> recipients(@PathVariable String taskCode,
                                                        @RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                        HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.recipients(user(request), taskCode, current, pageSize));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/{taskCode}/submissions/{submissionId}")
    public BaseResponse<Map<String, Object>> submission(@PathVariable String taskCode,
                                                        @PathVariable long submissionId,
                                                        HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.submissionDetail(user(request), taskCode, submissionId));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/submissions/{submissionId}/review")
    public BaseResponse<Map<String, Object>> review(@PathVariable String taskCode,
                                                    @PathVariable long submissionId,
                                                    @RequestBody TaskReviewRequest body,
                                                    HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.review(user(request), taskCode, submissionId, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/{taskCode}/submissions/{submissionId}/return")
    public BaseResponse<Map<String, Object>> returnSubmission(@PathVariable String taskCode,
                                                              @PathVariable long submissionId,
                                                              @RequestBody TaskReturnRequest body,
                                                              HttpServletRequest request) {
        return ResultUtils.success(teacherTaskService.returnSubmission(user(request), taskCode, submissionId, body));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/resources/questions")
    public BaseResponse<Map<String, Object>> resourceQuestions(@RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "1") long current,
                                                               @RequestParam(defaultValue = "10") long pageSize) {
        return ResultUtils.success(taskContentService.listQuestions(keyword, current, pageSize));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/resources/questions/{id}")
    public BaseResponse<Object> resourceQuestion(@PathVariable long id) {
        return ResultUtils.success(taskContentService.getQuestionResource(id));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/resources/experiments")
    public BaseResponse<Map<String, Object>> resourceExperiments(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(defaultValue = "1") long current,
                                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return ResultUtils.success(taskContentService.listExperiments(keyword, current, pageSize));
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/resources/experiments/{id}")
    public BaseResponse<Object> resourceExperiment(@PathVariable long id) {
        return ResultUtils.success(taskContentService.getExperimentResource(id));
    }

    private User user(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
