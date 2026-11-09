package com.yupi.springbootinit.ai;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
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
import java.util.Map;

/**
 * 统一 AI 入口：会话、模型配置、问答、下一步、诊断、帮助记录。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiPlatformService aiPlatformService;

    @Resource
    private UserService userService;

    @PostMapping("/session/start")
    public BaseResponse<Map<String, Object>> startSession(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.startSession(login(request).getId(), body));
    }

    @GetMapping("/session/{sessionId}")
    public BaseResponse<Map<String, Object>> getSession(@PathVariable String sessionId,
                                                        HttpServletRequest request) {
        Map<String, Object> session = aiPlatformService.getSession(login(request).getId(), sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        return ResultUtils.success(session);
    }

    @GetMapping("/config")
    public BaseResponse<Map<String, Object>> getConfig(HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.getConfig(login(request).getId()));
    }

    @PutMapping("/config")
    public BaseResponse<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.saveConfig(login(request).getId(), body));
    }

    @PostMapping("/config/test")
    public BaseResponse<Map<String, Object>> testConfig(HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.testConfig(login(request).getId()));
    }

    @PostMapping("/ask")
    public BaseResponse<Map<String, Object>> ask(@RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        try {
            return ResultUtils.success(aiPlatformService.ask(login(request).getId(), body));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    @PostMapping("/plan")
    public BaseResponse<Map<String, Object>> plan(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.plan(login(request).getId(), body));
    }

    @GetMapping("/diagnose")
    public BaseResponse<Map<String, Object>> diagnose(@RequestParam("nodeCode") String nodeCode,
                                                      @RequestParam(value = "courseCode", required = false) String courseCode,
                                                      HttpServletRequest request) {
        return ResultUtils.success(aiPlatformService.diagnose(login(request).getId(), nodeCode, courseCode));
    }

    @PostMapping("/help")
    public BaseResponse<Boolean> recordHelp(@RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        aiPlatformService.recordHelp(login(request).getId(), body);
        return ResultUtils.success(true);
    }

    private User login(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
