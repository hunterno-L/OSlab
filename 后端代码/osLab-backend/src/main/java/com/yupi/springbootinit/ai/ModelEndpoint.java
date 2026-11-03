package com.yupi.springbootinit.ai;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 一次模型调用的解析结果端点：平台默认或个人配置。
 *
 * <p>{@code apiKey} 是进程内明文，只用于构造 Authorization 头：
 * 不进 toString、不进日志、不进任何响应体。
 */
@Getter
@Setter
@ToString(exclude = {"apiKey"})
public class ModelEndpoint implements Serializable {

    /** 个人配置 id；平台默认为 null。 */
    private Long profileId;

    /** 展示标签（已脱敏），例如 {@code deepseek-flash}。 */
    private String label;

    private String baseUrl;

    private String model;

    private String apiKey;

    private ModelCapabilities capabilities = ModelCapabilities.unknown();

    /** 是否为平台默认配置（来自 ai.ark.*）。 */
    private boolean platformDefault;

    public static ModelEndpoint platform(String baseUrl, String model, String apiKey) {
        ModelEndpoint endpoint = new ModelEndpoint();
        endpoint.setPlatformDefault(true);
        endpoint.setBaseUrl(baseUrl);
        endpoint.setModel(model);
        endpoint.setApiKey(apiKey);
        endpoint.setLabel(model);
        endpoint.setCapabilities(ModelCapabilities.unknown());
        return endpoint;
    }

    /** 日志与用量记录只允许出现这个脱敏标签。 */
    public String safeLabel() {
        return label == null ? model : label;
    }
}
