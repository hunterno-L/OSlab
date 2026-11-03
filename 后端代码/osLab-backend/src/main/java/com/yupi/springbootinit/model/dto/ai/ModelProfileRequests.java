package com.yupi.springbootinit.model.dto.ai;

import lombok.Data;

import java.io.Serializable;

/** 个人模型配置请求 DTO（任务书 §3 冻结样例）。 */
public final class ModelProfileRequests {

    private ModelProfileRequests() {
    }

    /** {@code POST /ai/model-profiles} */
    @Data
    public static class Create implements Serializable {
        private String name;
        /** OPENAI_COMPATIBLE / ARK；缺省 OPENAI_COMPATIBLE。 */
        private String serviceType;
        private String baseUrl;
        private String model;
        private String apiKey;
    }

    /** {@code POST /ai/model-profiles/test} */
    @Data
    public static class Test implements Serializable {
        /** 个人配置 id 字符串；"0" 表示探测平台默认配置。 */
        private String profileId;
    }

    /** {@code PATCH /ai/model-profiles/{id}} */
    @Data
    public static class Update implements Serializable {
        /** 仅测试通过的候选可激活。 */
        private Boolean active;
        private String name;
        private String baseUrl;
        private String model;
        /** 传入新值即视为更换密钥；留空表示不改。 */
        private String apiKey;
        private String serviceType;
        private Integer rowVersion;
    }
}
