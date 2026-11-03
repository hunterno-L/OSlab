package com.yupi.springbootinit.ai;

import java.util.Map;

/**
 * 极简 JSON HTTP 传输 seam。
 *
 * <p>抽出来的唯一目的是让模型网关可单元测试：
 * 测试注入固定响应即可覆盖"模型失败/超时/能力缺失/降级"，不必触网，
 * 也绝不用模拟回复冒充真实模型验收。
 */
public interface HttpJsonTransport {

    Response post(String url, Map<String, String> headers, String body,
                  int connectTimeoutMs, int readTimeoutMs);

    class Response {
        private final int status;
        private final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }
}
