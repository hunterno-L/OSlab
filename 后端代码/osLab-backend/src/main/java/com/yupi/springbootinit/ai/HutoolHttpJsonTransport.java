package com.yupi.springbootinit.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/** hutool 实现：项目已有依赖，不引入新的 HTTP 客户端或常驻组件。 */
@Component
public class HutoolHttpJsonTransport implements HttpJsonTransport {

    @Override
    public Response post(String url, Map<String, String> headers, String body,
                         int connectTimeoutMs, int readTimeoutMs) {
        HttpRequest request = HttpRequest.post(url)
                .setConnectionTimeout(Math.max(1000, connectTimeoutMs))
                .setReadTimeout(Math.max(1000, readTimeoutMs))
                .body(body);
        if (headers != null) {
            headers.forEach(request::header);
        }
        try (HttpResponse response = request.execute()) {
            return new Response(response.getStatus(), response.body());
        } catch (RuntimeException e) {
            // 网络层异常统一转成 0 状态，由网关翻译成可解释的失败原因
            return new Response(0, e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "" : e.getMessage()));
        }
    }
}
