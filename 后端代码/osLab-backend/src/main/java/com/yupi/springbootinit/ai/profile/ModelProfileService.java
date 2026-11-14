package com.yupi.springbootinit.ai.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.ai.AiJson;
import com.yupi.springbootinit.ai.AiProperties;
import com.yupi.springbootinit.ai.BaseUrlGuard;
import com.yupi.springbootinit.ai.ModelCapabilities;
import com.yupi.springbootinit.ai.ModelEndpoint;
import com.yupi.springbootinit.ai.ModelGateway;
import com.yupi.springbootinit.ai.SecretCipher;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.mapper.AiModelProfileMapper;
import com.yupi.springbootinit.model.dto.ai.ModelProfileRequests;
import com.yupi.springbootinit.model.entity.AiModelProfile;
import com.yupi.springbootinit.model.vo.ai.ModelProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户模型配置服务（计划 §5.7 / P0-7）。
 *
 * <p>硬性要求：
 * <ul>
 *   <li>密钥后端加密保存，主密钥来自环境变量；响应与日志只出现脱敏值；</li>
 *   <li>候选配置先真实调用与能力测试，<b>只有测试通过的候选可激活</b>；</li>
 *   <li>无效候选不覆盖原有可用配置：改动地址/模型/密钥即回到 CANDIDATE 并取消激活；</li>
 *   <li>baseUrl 校验协议与解析目标，默认禁止本机与内网；</li>
 *   <li>只能读写本人配置，绝不返回他人密钥或地址。</li>
 * </ul>
 */
@Slf4j
@Service
public class ModelProfileService {

    /** 平台默认配置在契约中的固定 id。 */
    public static final String PLATFORM_PROFILE_ID = "0";
    private static final Long PLATFORM_PROFILE_KEY = 0L;

    @Resource
    private AiModelProfileMapper profileMapper;

    @Resource
    private AiProperties properties;

    @Resource
    private ModelGateway modelGateway;

    private SecretCipher cipher;
    private BaseUrlGuard baseUrlGuard;

    /** 平台默认配置的能力探测结果只缓存在内存（无对应数据行）。 */
    private final Map<String, ModelCapabilities> platformCapabilityCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.cipher = new SecretCipher(properties.getMasterKey());
        this.baseUrlGuard = new BaseUrlGuard(properties.isAllowPrivateBaseUrl());
    }

    /** 测试注入 seam。 */
    public void configure(SecretCipher cipher, BaseUrlGuard guard) {
        this.cipher = cipher;
        this.baseUrlGuard = guard;
    }

    // region 查询

    /**
     * 列表：平台默认 + 本人配置。平台默认永远排第一，
     * 且在用户没有激活个人配置时标记为 active，让界面显示"当前真正在用哪一个"。
     */
    public List<ModelProfileVO> list(Long userId) {
        List<AiModelProfile> own = profileMapper.selectList(new LambdaQueryWrapper<AiModelProfile>()
                .eq(AiModelProfile::getUserId, userId)
                // 逻辑删除的配置不再出现在列表里（软删标记为 1）
                .and(w -> w.isNull(AiModelProfile::getIsDelete)
                        .or()
                        .ne(AiModelProfile::getIsDelete, 1))
                .orderByAsc(AiModelProfile::getCreateTime)
                .orderByAsc(AiModelProfile::getId));
        boolean hasActivePersonal = own.stream().anyMatch(AiModelProfile::isActiveFlag);
        List<ModelProfileVO> out = new ArrayList<>();
        out.add(platformVo(hasActivePersonal));
        for (AiModelProfile profile : own) {
            out.add(toVo(profile));
        }
        return out;
    }

    private ModelProfileVO platformVo(boolean hasActivePersonal) {
        AiProperties.Ark ark = properties.getArk();
        ModelProfileVO vo = new ModelProfileVO();
        vo.setProfileId(PLATFORM_PROFILE_ID);
        vo.setName("平台默认");
        vo.setBaseUrlMasked(BaseUrlMasker.mask(ark.getBaseUrl()));
        vo.setModel(ark.getModel());
        vo.setPlatformDefault(true);
        vo.setActive(!hasActivePersonal && StringUtils.isNotBlank(ark.getApiKey()));
        vo.setStatus(StringUtils.isNotBlank(ark.getApiKey())
                ? AiModelProfile.STATUS_ACTIVE : AiModelProfile.STATUS_CANDIDATE);
        vo.setTestStatus(StringUtils.isNotBlank(ark.getApiKey())
                ? AiModelProfile.TEST_PASSED : AiModelProfile.TEST_UNTESTED);
        ModelCapabilities cached = platformCapabilityCache.get(PLATFORM_PROFILE_ID);
        vo.setCapabilities(cached != null ? cached
                : (StringUtils.isNotBlank(ark.getApiKey())
                ? ModelCapabilities.unknown() : ModelCapabilities.none()));
        // 平台密钥来自环境变量，不回显任何脱敏片段，避免日志/截图泄露
        vo.setApiKeyMasked("");
        return vo;
    }

    private ModelProfileVO toVo(AiModelProfile profile) {
        ModelProfileVO vo = new ModelProfileVO();
        vo.setProfileId(AiJson.id(profile.getId()));
        vo.setName(profile.getName());
        vo.setBaseUrlMasked(BaseUrlMasker.mask(profile.getBaseUrl()));
        vo.setModel(profile.getModel());
        vo.setPlatformDefault(false);
        vo.setActive(profile.isActiveFlag());
        vo.setStatus(profile.getStatus());
        vo.setTestStatus(profile.getTestStatus());
        vo.setTestedAt(AiJson.iso(profile.getTestedAt()));
        vo.setApiKeyMasked(StringUtils.defaultString(profile.getApiKeyMasked()));
        ModelCapabilities capabilities = AiJson.read(profile.getCapabilitiesJson(), ModelCapabilities.class);
        vo.setCapabilities(capabilities == null ? ModelCapabilities.none() : capabilities);
        return vo;
    }

    // endregion

    // region 创建 / 测试 / 激活

    /**
     * 新建候选配置。密钥立即加密；未测试的配置不可激活，因此不会影响现有可用选择。
     *
     * @return profileId 字符串
     */
    public String create(Long userId, ModelProfileRequests.Create request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        String name = StringUtils.trimToNull(request.getName());
        String model = StringUtils.trimToNull(request.getModel());
        String baseUrl = StringUtils.trimToNull(request.getBaseUrl());
        if (name == null || name.length() > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配置名称必填且不超过 128 字符");
        }
        if (model == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型名称必填");
        }
        requireValidBaseUrl(baseUrl);
        String apiKey = StringUtils.trimToNull(request.getApiKey());
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "API Key 必填（个人配置需要真实调用测试）");
        }
        if (!cipher.available()) {
            // 绝不退回明文存储
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "服务端未配置 OSLAB_AI_MASTER_KEY，无法安全保存 API Key");
        }
        AiModelProfile profile = new AiModelProfile();
        profile.setUserId(userId);
        profile.setName(name);
        profile.setServiceType(normalizeServiceType(request.getServiceType()));
        profile.setBaseUrl(baseUrl);
        profile.setModel(model);
        profile.setApiKeyCipher(cipher.encrypt(apiKey));
        profile.setApiKeyMasked(SecretCipher.mask(apiKey));
        profile.setCapabilitiesJson(AiJson.write(ModelCapabilities.none()));
        profile.setStatus(AiModelProfile.STATUS_CANDIDATE);
        profile.setTestStatus(AiModelProfile.TEST_UNTESTED);
        profile.setActive(0);
        profile.setRowVersion(0);
        profile.setIsDelete(0);
        profileMapper.insert(profile);
        log.info("[ai-profile] created candidate profile user={} name={} model={}", userId, name, model);
        return AiJson.id(profile.getId());
    }

    /**
     * 真实调用测试并记录能力。失败不抛异常：把原因返回给界面，
     * 且不改动已有激活配置。
     */
    public ModelGateway.CapabilityProbe test(Long userId, String profileId) {
        if (PLATFORM_PROFILE_ID.equals(StringUtils.trimToEmpty(profileId))) {
            return testPlatform();
        }
        AiModelProfile profile = requireOwn(userId, profileId);
        ModelEndpoint endpoint = toEndpoint(profile);
        ModelGateway.CapabilityProbe probe = modelGateway.probe(endpoint);
        profile.setTestStatus(probe.isOk() ? AiModelProfile.TEST_PASSED : AiModelProfile.TEST_FAILED);
        profile.setStatus(probe.isOk() ? AiModelProfile.STATUS_TESTED : AiModelProfile.STATUS_REJECTED);
        profile.setTestLatencyMs(probe.getLatencyMs());
        profile.setTestMessage(StringUtils.abbreviate(probe.getError(), 1000));
        profile.setTestedAt(new Date());
        profile.setCapabilitiesJson(AiJson.write(probe.getCapabilities()));
        if (!probe.isOk() && profile.isActiveFlag()) {
            // 原本可用的配置突然测不通：取消激活并退回平台默认，切换必须可见
            profile.setActive(0);
        }
        profileMapper.updateById(profile);
        return probe;
    }

    private ModelGateway.CapabilityProbe testPlatform() {
        ModelEndpoint endpoint = modelGateway.platformEndpoint();
        ModelGateway.CapabilityProbe probe = modelGateway.probe(endpoint);
        if (probe.isOk()) {
            platformCapabilityCache.put(PLATFORM_PROFILE_ID, probe.getCapabilities());
        }
        return probe;
    }

    /**
     * 更新 / 激活。
     *
     * <p>激活前置条件：{@code testStatus=PASSED}。
     * 改动地址、模型或密钥会让配置回到未测试候选并取消激活，
     * 保证"无效候选不覆盖原配置"。
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelProfileVO update(Long userId, String profileId, ModelProfileRequests.Update request) {
        if (PLATFORM_PROFILE_ID.equals(StringUtils.trimToEmpty(profileId))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "平台默认配置由部署环境变量维护，不可在此修改");
        }
        AiModelProfile profile = requireOwn(userId, profileId);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        if (request.getRowVersion() != null
                && !request.getRowVersion().equals(profile.getRowVersion())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "配置已被其他操作更新，请刷新后重试");
        }
        boolean connectionChanged = false;
        if (StringUtils.isNotBlank(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > 128) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "配置名称不超过 128 字符");
            }
            profile.setName(name);
        }
        if (StringUtils.isNotBlank(request.getServiceType())) {
            profile.setServiceType(normalizeServiceType(request.getServiceType()));
        }
        if (StringUtils.isNotBlank(request.getBaseUrl())
                && !request.getBaseUrl().trim().equals(profile.getBaseUrl())) {
            requireValidBaseUrl(request.getBaseUrl().trim());
            profile.setBaseUrl(request.getBaseUrl().trim());
            connectionChanged = true;
        }
        if (StringUtils.isNotBlank(request.getModel())
                && !request.getModel().trim().equals(profile.getModel())) {
            profile.setModel(request.getModel().trim());
            connectionChanged = true;
        }
        if (StringUtils.isNotBlank(request.getApiKey())) {
            if (!cipher.available()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "服务端未配置 OSLAB_AI_MASTER_KEY，无法安全保存 API Key");
            }
            String apiKey = request.getApiKey().trim();
            profile.setApiKeyCipher(cipher.encrypt(apiKey));
            profile.setApiKeyMasked(SecretCipher.mask(apiKey));
            connectionChanged = true;
        }
        if (connectionChanged) {
            profile.setTestStatus(AiModelProfile.TEST_UNTESTED);
            profile.setStatus(AiModelProfile.STATUS_CANDIDATE);
            profile.setCapabilitiesJson(AiJson.write(ModelCapabilities.none()));
            profile.setTestedAt(null);
            profile.setTestLatencyMs(null);
            profile.setTestMessage("连接信息已变更，需重新测试");
            profile.setActive(0);
        }
        if (request.getActive() != null) {
            if (request.getActive()) {
                if (!profile.isTestPassed()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "仅测试通过的候选配置可以激活，请先执行测试");
                }
                // 同一用户只保留一个激活配置
                profileMapper.deactivateAllForUser(userId);
                profile.setActive(1);
                profile.setStatus(AiModelProfile.STATUS_ACTIVE);
            } else {
                profile.setActive(0);
                profile.setStatus(profile.isTestPassed()
                        ? AiModelProfile.STATUS_TESTED : AiModelProfile.STATUS_CANDIDATE);
            }
        }
        profile.setRowVersion((profile.getRowVersion() == null ? 0 : profile.getRowVersion()) + 1);
        profileMapper.updateById(profile);
        return toVo(profile);
    }

    // endregion

    // region 供编排器解析端点

    /**
     * 解析一次调用应使用的端点。
     *
     * @param modelProfileId null 或 0 表示平台默认
     * @throws BusinessException 指定的个人配置不可用（不存在、非本人、未测试通过或已失效）。
     *                           不静默替换模型：切换必须对用户可见。
     */
    public ModelEndpoint resolveEndpoint(Long userId, Long modelProfileId) {
        if (modelProfileId == null || PLATFORM_PROFILE_KEY.equals(modelProfileId)) {
            ModelEndpoint platform = modelGateway.platformEndpoint();
            if (platform == null) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR,
                        "平台默认模型未配置（缺少 AI_ARK_API_KEY）");
            }
            return platform;
        }
        AiModelProfile profile = profileMapper.selectById(modelProfileId);
        if (profile == null || (profile.getIsDelete() != null && profile.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型配置不存在");
        }
        if (!profile.getUserId().equals(userId)) {
            // 不泄露他人配置是否存在，统一按无权限处理
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权使用该模型配置");
        }
        if (!profile.isTestPassed()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "模型配置「" + profile.getName() + "」尚未测试通过，请重新测试或改用其他配置");
        }
        return toEndpoint(profile);
    }

    /** 会话创建时校验 modelProfileId 可用，避免把不可用配置写进会话。 */
    public Long validateProfileForSession(Long userId, String modelProfileId) {
        if (StringUtils.isBlank(modelProfileId) || PLATFORM_PROFILE_ID.equals(modelProfileId.trim())) {
            return null;
        }
        Long id;
        try {
            id = AiJson.parseId(modelProfileId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "modelProfileId 非法");
        }
        resolveEndpoint(userId, id);
        return id;
    }

    /** 当前用户实际生效的模型标签（脱敏），用于轮次 usage 记录。 */
    public String labelOf(ModelEndpoint endpoint) {
        if (endpoint == null) {
            return "unconfigured";
        }
        return StringUtils.defaultIfBlank(endpoint.getModel(), "unknown");
    }

    private ModelEndpoint toEndpoint(AiModelProfile profile) {
        ModelEndpoint endpoint = new ModelEndpoint();
        endpoint.setProfileId(profile.getId());
        endpoint.setBaseUrl(profile.getBaseUrl());
        endpoint.setModel(profile.getModel());
        endpoint.setPlatformDefault(false);
        endpoint.setLabel(profile.getModel());
        endpoint.setApiKey(cipher.decrypt(profile.getApiKeyCipher()));
        ModelCapabilities capabilities = AiJson.read(profile.getCapabilitiesJson(), ModelCapabilities.class);
        endpoint.setCapabilities(capabilities == null ? ModelCapabilities.unknown() : capabilities);
        return endpoint;
    }

    /**
     * 删除个人模型配置（逻辑删除）。
     *
     * <p>平台默认配置由部署环境变量维护，不可删除；只能删自己的配置。
     * 若删除的是当前激活配置，先取消该用户的激活状态，避免会话继续引用已删配置。
     */
    public void delete(Long userId, String profileId) {
        if (PLATFORM_PROFILE_ID.equals(StringUtils.trimToEmpty(profileId))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "平台默认配置由部署环境变量维护，不可删除");
        }
        AiModelProfile profile = requireOwn(userId, profileId);
        if (profile.isActiveFlag()) {
            profileMapper.deactivateAllForUser(userId);
            profile.setActive(0);
        }
        // 全局配置了 logic-delete-field: isDelete，deleteById 会转成
        // UPDATE ... SET isDelete=1 WHERE id=? AND isDelete=0；
        // updateById 不会写这个字段，所以这里必须用 deleteById。
        profileMapper.deleteById(profile.getId());
    }

    private AiModelProfile requireOwn(Long userId, String profileId) {
        Long id;
        try {
            id = AiJson.parseId(profileId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profileId 非法");
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profileId 不能为空");
        }
        AiModelProfile profile = profileMapper.selectById(id);
        if (profile == null || (profile.getIsDelete() != null && profile.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型配置不存在");
        }
        if (!profile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该模型配置");
        }
        return profile;
    }

    private void requireValidBaseUrl(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "服务地址必填");
        }
        String reason = baseUrlGuard.validate(baseUrl);
        if (reason != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, reason);
        }
    }

    private static String normalizeServiceType(String serviceType) {
        String value = StringUtils.upperCase(StringUtils.trimToEmpty(serviceType));
        if ("ARK".equals(value) || "OPENAI_COMPATIBLE".equals(value)) {
            return value;
        }
        return "OPENAI_COMPATIBLE";
    }

    // endregion
}
