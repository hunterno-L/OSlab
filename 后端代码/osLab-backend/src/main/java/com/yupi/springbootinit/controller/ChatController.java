package com.yupi.springbootinit.controller;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.service.UserService;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
@RestController
@RequestMapping("/chat")
public class ChatController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);

    private static final String ROLE_PROMPT = "你是一个擅长计算机课程操作系统的问答助手";

    private final ArkService service;
    private final String model;

    @Resource
    private UserService userService;

    public ChatController(@Value("${ai.ark.api-key:}") String apiKey,
                          @Value("${ai.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                          @Value("${ai.ark.model:deepseek-v3-250324}") String model) {
        this.model = model;
        this.service = StringUtils.hasText(apiKey)
                ? ArkService.builder().baseUrl(baseUrl).apiKey(apiKey).build()
                : null;
    }

    @PostMapping("/ask")
    public BaseResponse<String> askQuestion(@RequestBody String userQuestion, HttpServletRequest request) {
        userService.getLoginUser(request);
        if (service == null) {
            return ResultUtils.error(ErrorCode.API_REQUEST_ERROR, "AI 助手尚未配置，请联系管理员");
        }

        // 创建消息
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(ROLE_PROMPT).build();
        ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userQuestion).build();
        messages.add(systemMessage);
        messages.add(userMessage);

        // 创建请求
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();

        try {
            // 调用 Ark API 获取回答
            StringBuilder response = new StringBuilder();
            service.createChatCompletion(chatRequest).getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            return ResultUtils.success(response.toString());
        } catch (RuntimeException e) {
            LOGGER.warn("Ark chat completion request failed: {}", e.getClass().getSimpleName());
            return ResultUtils.error(ErrorCode.API_REQUEST_ERROR, "AI 助手暂时不可用，请稍后再试");
        }
    }
}
