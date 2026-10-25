package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.dto.task.TaskAnswerRequest;
import com.yupi.springbootinit.model.dto.task.TaskCodeSubmitRequest;
import com.yupi.springbootinit.model.dto.task.TaskSubmitRequest;
import com.yupi.springbootinit.model.dto.task.TaskWorkSaveRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.task.StudentTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** 学生任务接口：只按当前登录用户查询接收记录，不接受客户端指定 userId。 */
@RestController
@RequestMapping("/learning/tasks")
public class LearningTaskController {

    @Resource
    private StudentTaskService studentTaskService;
    @Resource
    private UserService userService;

    @GetMapping
    public BaseResponse<Map<String, Object>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.list(user(request), status, keyword, current, pageSize));
    }

    @GetMapping("/summary")
    public BaseResponse<Map<String, Object>> summary(HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.summary(user(request)));
    }

    @GetMapping("/{taskCode}")
    public BaseResponse<Map<String, Object>> detail(@PathVariable String taskCode,
                                                    HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.detail(user(request), taskCode));
    }

    @PostMapping("/{taskCode}/accept")
    public BaseResponse<Map<String, Object>> accept(@PathVariable String taskCode,
                                                    HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.accept(user(request), taskCode));
    }

    @PutMapping("/{taskCode}/items/{itemCode}/work")
    public BaseResponse<Map<String, Object>> saveWork(@PathVariable String taskCode,
                                                      @PathVariable String itemCode,
                                                      @RequestBody TaskWorkSaveRequest body,
                                                      HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.saveWork(user(request), taskCode, itemCode, body));
    }

    @PostMapping("/{taskCode}/items/{itemCode}/answer")
    public BaseResponse<Map<String, Object>> answer(@PathVariable String taskCode,
                                                    @PathVariable String itemCode,
                                                    @RequestBody TaskAnswerRequest body,
                                                    HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.answer(user(request), taskCode, itemCode, body));
    }

    @PostMapping("/{taskCode}/items/{itemCode}/code-submissions")
    public BaseResponse<Map<String, Object>> codeSubmit(@PathVariable String taskCode,
                                                        @PathVariable String itemCode,
                                                        @RequestBody TaskCodeSubmitRequest body,
                                                        HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.codeSubmit(user(request), taskCode, itemCode, body));
    }

    @GetMapping("/{taskCode}/items/{itemCode}/code-submissions/{id}")
    public BaseResponse<Map<String, Object>> codeStatus(@PathVariable String taskCode,
                                                        @PathVariable String itemCode,
                                                        @PathVariable long id,
                                                        HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.codeStatus(user(request), taskCode, itemCode, id));
    }

    @PostMapping("/{taskCode}/submit")
    public BaseResponse<Map<String, Object>> submit(@PathVariable String taskCode,
                                                    @RequestBody TaskSubmitRequest body,
                                                    HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.submit(user(request), taskCode, body));
    }

    @GetMapping("/{taskCode}/submissions")
    public BaseResponse<List<Map<String, Object>>> history(@PathVariable String taskCode,
                                                           HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.historyFor(user(request), taskCode));
    }

    @GetMapping("/{taskCode}/submissions/{submissionId}")
    public BaseResponse<Map<String, Object>> submission(@PathVariable String taskCode,
                                                        @PathVariable long submissionId,
                                                        HttpServletRequest request) {
        return ResultUtils.success(studentTaskService.studentSubmissionDetail(
                user(request), taskCode, submissionId));
    }

    private User user(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
