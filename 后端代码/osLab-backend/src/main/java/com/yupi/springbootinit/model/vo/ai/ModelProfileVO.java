package com.yupi.springbootinit.model.vo.ai;

import com.yupi.springbootinit.ai.ModelCapabilities;
import lombok.Data;

import java.io.Serializable;

/**
 * 冻结契约：{@code GET /ai/model-profiles} 列表项（任务书 §3）。
 *
 * <p>只出现脱敏值：{@code baseUrlMasked} 已隐去内网主机与查询串，
 * API Key 本身既不返回明文也不返回密文，浏览器不得存入 localStorage。
 */
@Data
public class ModelProfileVO implements Serializable {

    /** 个人配置 id 字符串；平台默认配置固定为 "0"。 */
    private String profileId;

    private String name;

    private String baseUrlMasked;

    private String model;

    private ModelCapabilities capabilities;

    /** 带明确时区的 ISO-8601；未测试为 null。 */
    private String testedAt;

    private Boolean active;

    /** 平台默认配置为 true，前端据此禁止编辑/删除。 */
    private Boolean platformDefault;

    /** CANDIDATE / TESTED / ACTIVE / REJECTED */
    private String status;

    /** UNTESTED / PASSED / FAILED */
    private String testStatus;

    /** 脱敏后的密钥提示，例如 {@code sk-****c6ee}；平台默认可能为空。 */
    private String apiKeyMasked;
}
