package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.ModelGateway;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.RateLimiter;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局助手旧入口。
 *
 * <p>响应契约保持不变：{@code BaseResponse<String>}、限流提示、未配置提示、失败提示与升级前逐字一致，
 * 前端无需改动。内部改为<b>委托统一模型网关</b>（计划 §6.2「保留 /chat/ask，内部委托统一服务」）：
 * 不再持有第二套 Ark SDK 客户端，因此平台默认模型与个人模型配置只有一个来源。
 *
 * <p>模型不可用时如实返回错误，绝不编造回答。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);

    private static final String ROLE_PROMPT = "你是一个擅长计算机课程操作系统的问答助手";

    @Resource
    private UserService userService;

    @Resource
    private ModelGateway modelGateway;

    @Resource
    private ModelProfileService modelProfileService;

    /**
     * AI 问答限流：同一用户 1 分钟窗口内最多 10 次，防止消耗 API 费用。
     */
    private final RateLimiter chatLimiter = new RateLimiter(10, 60 * 1000L);

    @PostMapping("/ask")
    public BaseResponse<String> askQuestion(@RequestBody String userQuestion, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (!chatLimiter.tryAcquire("chat:" + loginUser.getId())) {
            return ResultUtils.error(ErrorCode.FORBIDDEN_ERROR, "提问过于频繁，请稍后再试");
        }

        ModelEndpoint endpoint;
        try {
            // 旧入口没有模型配置参数，固定走平台默认端点（ai.ark.*）
            endpoint = modelProfileService.resolveEndpoint(loginUser.getId(), null);
        } catch (BusinessException e) {
            LOGGER.info("[chat] platform model unavailable: {}", e.getMessage());
            return ResultUtils.error(ErrorCode.API_REQUEST_ERROR, "AI 助手尚未配置，请联系管理员");
        }
        if (!modelGateway.available(endpoint)) {
            return ResultUtils.error(ErrorCode.API_REQUEST_ERROR, "AI 助手尚未配置，请联系管理员");
        }

        try {
            ModelGateway.ChatResult result = modelGateway.chat(endpoint,
                    ModelGateway.ChatRequest.of(ROLE_PROMPT, userQuestion));
            return ResultUtils.success(result.getContent() == null ? "" : result.getContent());
        } catch (RuntimeException e) {
            LOGGER.warn("Ark chat completion request failed: {}", e.getClass().getSimpleName());
            return ResultUtils.error(ErrorCode.API_REQUEST_ERROR, "AI 助手暂时不可用，请稍后再试");
        }
    }
}
