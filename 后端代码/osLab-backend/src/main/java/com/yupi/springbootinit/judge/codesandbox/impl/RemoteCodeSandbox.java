package com.yupi.springbootinit.judge.codesandbox.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.judge.codesandbox.CodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 远程代码沙箱（实际调用接口的沙箱）
 */
@Component
public class RemoteCodeSandbox implements CodeSandbox {
    /** The sandbox stays on the private Compose network; it is never browser-facing. */
    @Value("${codesandbox.remote.url:}")
    private String url;

    @Value("${codesandbox.remote.auth-secret:}")
    private String authSecret;

    @Value("${codesandbox.remote.timeout-ms:30000}")
    private int timeoutMs;

    private static final String AUTH_REQUEST_HEADER = "auth";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(authSecret)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码沙箱未配置");
        }
        String json = JSONUtil.toJsonStr(executeCodeRequest);
        try (HttpResponse response = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER, authSecret)
                .body(json)
                .timeout(timeoutMs)
                .execute()) {
            String responseStr = response.body();
            if (!response.isOk() || StringUtils.isBlank(responseStr)) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱执行失败");
            }
            return JSONUtil.toBean(responseStr, ExecuteCodeResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱不可用");
        }
    }
}
