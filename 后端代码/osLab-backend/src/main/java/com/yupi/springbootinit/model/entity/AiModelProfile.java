package com.yupi.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 个人模型配置（v12）。
 *
 * <p>密钥只以 {@code apiKeyCipher}（AES-GCM，主密钥来自环境变量）保存；
 * {@code apiKeyMasked} 是供界面展示的脱敏值。明文密钥不落库、不进日志、不进响应。
 *
 * <p>候选配置必须先真实调用通过（{@code testStatus=PASSED}）才能激活，
 * 无效候选不覆盖原有可用配置。
 *
 * @TableName ai_model_profile
 */
@TableName(value = "ai_model_profile")
@Data
public class AiModelProfile implements Serializable {

    public static final String STATUS_CANDIDATE = "CANDIDATE";
    public static final String STATUS_TESTED = "TESTED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String TEST_UNTESTED = "UNTESTED";
    public static final String TEST_PASSED = "PASSED";
    public static final String TEST_FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    /** OPENAI_COMPATIBLE / ARK —— 均走 OpenAI 兼容协议。 */
    private String serviceType;

    private String baseUrl;

    private String model;

    private String apiKeyCipher;

    private String apiKeyMasked;

    /** {chat,structured,nativeTools} JSON。 */
    private String capabilitiesJson;

    private String status;

    private String testStatus;

    private Long testLatencyMs;

    private String testMessage;

    private Date testedAt;

    private Integer active;

    private Integer rowVersion;

    private Integer isDelete;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public boolean isActive() {
        return active != null && active == 1;
    }

    public boolean isTestPassed() {
        return TEST_PASSED.equals(testStatus);
    }
}
