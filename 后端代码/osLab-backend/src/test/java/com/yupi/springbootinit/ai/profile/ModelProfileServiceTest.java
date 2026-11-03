package com.yupi.springbootinit.ai.profile;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人模型配置回归（P0-7 / 任务书 §4）：
 * 密钥不落明文、只测试通过的候选可激活、无效候选不覆盖原配置、
 * 跨用户读取一律拒绝、平台默认配置不回显任何密钥片段。
 */
class ModelProfileServiceTest {

    private static final String MASTER_KEY = "unit-test-master-key";
    private static final long OWNER = 1001L;
    private static final long OTHER = 2002L;

    private final AiModelProfileMapper mapper = mock(AiModelProfileMapper.class);
    private final ModelGateway gateway = mock(ModelGateway.class);
    private final ModelProfileService service = new ModelProfileService();
    private final AtomicLong idSequence = new AtomicLong(900000L);

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setMasterKey(MASTER_KEY);
        properties.getArk().setApiKey("sk-platform-key");
        properties.getArk().setBaseUrl("https://ark.cn-beijing.volces.com/api/v3");
        properties.getArk().setModel("deepseek-flash");
        ReflectionTestUtils.setField(service, "profileMapper", mapper);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "modelGateway", gateway);
        service.configure(new SecretCipher(MASTER_KEY), new BaseUrlGuard(false, host -> {
            // 固定解析：公网地址通过，内网与回环拒绝
            if (host.contains("private") || host.contains("localhost") || host.matches("[0-9.]+")
                    || host.contains(":")) {
                return new java.net.InetAddress[]{java.net.InetAddress.getByName("127.0.0.1")};
            }
            return new java.net.InetAddress[]{java.net.InetAddress.getByName("23.44.55.66")};
        }));
        when(mapper.insert(any(AiModelProfile.class))).thenAnswer(invocation -> {
            AiModelProfile profile = invocation.getArgument(0);
            profile.setId(idSequence.incrementAndGet());
            return 1;
        });
        when(gateway.platformEndpoint()).thenAnswer(invocation ->
                ModelEndpoint.platform(properties.getArk().getBaseUrl(),
                        properties.getArk().getModel(), properties.getArk().getApiKey()));
    }

    private static ModelProfileRequests.Create createRequest() {
        ModelProfileRequests.Create request = new ModelProfileRequests.Create();
        request.setName("个人 Qwen 兼容接口");
        request.setServiceType("openai_compatible");
        request.setBaseUrl("https://dashscope.example.com/compatible-mode/v1");
        request.setModel("qwen-plus");
        request.setApiKey("sk-personal-secret-value");
        return request;
    }

    private AiModelProfile persisted(long id, long userId, String testStatus, boolean active) {
        AiModelProfile profile = new AiModelProfile();
        profile.setId(id);
        profile.setUserId(userId);
        profile.setName("已存配置");
        profile.setServiceType("OPENAI_COMPATIBLE");
        profile.setBaseUrl("https://dashscope.example.com/compatible-mode/v1");
        profile.setModel("qwen-plus");
        profile.setApiKeyCipher(new SecretCipher(MASTER_KEY).encrypt("sk-personal-secret-value"));
        profile.setApiKeyMasked(SecretCipher.mask("sk-personal-secret-value"));
        profile.setStatus(AiModelProfile.STATUS_TESTED);
        profile.setTestStatus(testStatus);
        profile.setTestedAt(new Date());
        profile.setCapabilitiesJson("{\"chat\":true,\"structured\":true,\"nativeTools\":false}");
        profile.setActive(active ? 1 : 0);
        profile.setRowVersion(0);
        profile.setIsDelete(0);
        return profile;
    }

    // region 创建

    @Test
    void createStoresCipherTextAndNeverPlainText() {
        String profileId = service.create(OWNER, createRequest());
        assertNotNull(profileId);
        ArgumentCaptor<AiModelProfile> captor = ArgumentCaptor.forClass(AiModelProfile.class);
        verify(mapper).insert(captor.capture());
        AiModelProfile saved = captor.getValue();
        assertFalse(saved.getApiKeyCipher().contains("sk-personal-secret-value"), "不得明文落库");
        assertTrue(saved.getApiKeyCipher().startsWith("v1:"));
        assertEquals("sk-****alue", saved.getApiKeyMasked());
        assertEquals(AiModelProfile.STATUS_CANDIDATE, saved.getStatus());
        assertEquals(AiModelProfile.TEST_UNTESTED, saved.getTestStatus());
        assertEquals(0, saved.getActive().intValue(), "新候选默认不激活");
        assertEquals("OPENAI_COMPATIBLE", saved.getServiceType());
        // 密文可解回明文供真实调用
        assertEquals("sk-personal-secret-value", new SecretCipher(MASTER_KEY).decrypt(saved.getApiKeyCipher()));
    }

    @Test
    void createRejectsInternalBaseUrl() {
        ModelProfileRequests.Create request = createRequest();
        request.setBaseUrl("http://localhost:11434/v1");
        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(OWNER, request));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), failure.getCode());
        verify(mapper, never()).insert(any(AiModelProfile.class));
    }

    @Test
    void createRejectsMissingApiKeyAndBlankFields() {
        ModelProfileRequests.Create noKey = createRequest();
        noKey.setApiKey("  ");
        assertThrows(BusinessException.class, () -> service.create(OWNER, noKey));

        ModelProfileRequests.Create noModel = createRequest();
        noModel.setModel(null);
        assertThrows(BusinessException.class, () -> service.create(OWNER, noModel));

        assertThrows(BusinessException.class, () -> service.create(OWNER, null));
        verify(mapper, never()).insert(any(AiModelProfile.class));
    }

    @Test
    void createFailsLoudlyWhenMasterKeyMissing() throws Exception {
        // 地址校验先通过（固定解析为公网），确保失败原因确实来自主密钥缺失
        service.configure(new SecretCipher(""),
                new BaseUrlGuard(false, host -> new java.net.InetAddress[]{
                        java.net.InetAddress.getByName("23.44.55.66")}));
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.create(OWNER, createRequest()));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), failure.getCode());
        assertTrue(failure.getMessage().contains("OSLAB_AI_MASTER_KEY"),
                "必须指出缺失的环境变量，便于演示前准备");
        verify(mapper, never()).insert(any(AiModelProfile.class));
    }

    // endregion

    // region 测试与激活

    @Test
    void testPersistsCapabilitiesAndLatency() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_UNTESTED, false);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelGateway.CapabilityProbe probe = new ModelGateway.CapabilityProbe();
        probe.setOk(true);
        probe.setLatencyMs(420L);
        probe.setCapabilities(new ModelCapabilities(true, true, false));
        when(gateway.probe(any(ModelEndpoint.class))).thenReturn(probe);

        ModelGateway.CapabilityProbe result = service.test(OWNER, "1");
        assertTrue(result.isOk());
        assertEquals(420L, result.getLatencyMs());
        assertEquals(AiModelProfile.TEST_PASSED, profile.getTestStatus());
        assertEquals(AiModelProfile.STATUS_TESTED, profile.getStatus());
        assertTrue(profile.getCapabilitiesJson().contains("\"structured\":true"));
        assertFalse(profile.getCapabilitiesJson().contains("\"nativeTools\":true"));
        verify(mapper).updateById(profile);
    }

    @Test
    void failedTestDeactivatesPreviouslyActiveProfile() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelGateway.CapabilityProbe probe = new ModelGateway.CapabilityProbe();
        probe.setOk(false);
        probe.setError("模型鉴权失败（API Key 无效或无该模型权限）");
        probe.setCapabilities(ModelCapabilities.none());
        when(gateway.probe(any(ModelEndpoint.class))).thenReturn(probe);

        ModelGateway.CapabilityProbe result = service.test(OWNER, "1");
        assertFalse(result.isOk());
        assertEquals(AiModelProfile.TEST_FAILED, profile.getTestStatus());
        assertEquals(AiModelProfile.STATUS_REJECTED, profile.getStatus());
        assertEquals(0, profile.getActive().intValue(), "测不通的配置必须取消激活，退回平台默认");
    }

    @Test
    void activationRequiresPassedTest() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_UNTESTED, false);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setActive(true);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.update(OWNER, "1", request));
        assertTrue(failure.getMessage().contains("测试通过"), "必须说明只有测试通过的候选可激活");
        assertEquals(0, profile.getActive().intValue());
        verify(mapper, never()).updateById(any(AiModelProfile.class));
    }

    @Test
    void activationDeactivatesOtherProfilesOfSameUser() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, false);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setActive(true);
        ModelProfileVO vo = service.update(OWNER, "1", request);
        assertTrue(vo.getActive());
        assertEquals(AiModelProfile.STATUS_ACTIVE, profile.getStatus());
        assertEquals(1, profile.getActive().intValue());
        // 同一用户只保留一个激活配置
        verify(mapper, times(1)).deactivateAllForUser(OWNER);
        verify(mapper).updateById(profile);
    }

    @Test
    void changingConnectionDetailsResetsTestAndDeactivates() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setApiKey("sk-rotated-key-value");
        ModelProfileVO vo = service.update(OWNER, "1", request);
        assertFalse(vo.getActive(), "换密钥后必须取消激活，避免用未验证配置推理");
        assertEquals(AiModelProfile.TEST_UNTESTED, profile.getTestStatus());
        assertEquals(AiModelProfile.STATUS_CANDIDATE, profile.getStatus());
        assertEquals("sk-****alue", profile.getApiKeyMasked());
        assertFalse(profile.getApiKeyCipher().contains("sk-rotated-key-value"));
        assertNull(profile.getTestedAt());
    }

    @Test
    void renamingDoesNotInvalidateAPassedTest() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setName("换个名字");
        service.update(OWNER, "1", request);
        assertEquals(AiModelProfile.TEST_PASSED, profile.getTestStatus());
        assertEquals(1, profile.getActive().intValue());
        assertEquals("换个名字", profile.getName());
    }

    @Test
    void staleRowVersionIsRejected() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, false);
        profile.setRowVersion(3);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setRowVersion(2);
        request.setName("并发写");
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.update(OWNER, "1", request));
        assertTrue(failure.getMessage().contains("刷新"));
    }

    // endregion

    // region 权限与脱敏

    @Test
    void crossUserAccessIsDeniedForTestUpdateAndResolve() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);

        BusinessException testFailure = assertThrows(BusinessException.class, () -> service.test(OTHER, "1"));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), testFailure.getCode());

        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setActive(true);
        BusinessException updateFailure = assertThrows(BusinessException.class,
                () -> service.update(OTHER, "1", request));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), updateFailure.getCode());

        BusinessException resolveFailure = assertThrows(BusinessException.class,
                () -> service.resolveEndpoint(OTHER, 1L));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), resolveFailure.getCode());
        verify(gateway, never()).probe(any(ModelEndpoint.class));
    }

    @Test
    void missingProfileIsNotFound() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.test(OWNER, "404"));
        assertThrows(BusinessException.class, () -> service.resolveEndpoint(OWNER, 404L));
    }

    @Test
    void listIsMaskedAndIncludesPlatformDefault() {
        AiModelProfile own = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(own));
        List<ModelProfileVO> list = service.list(OWNER);
        assertEquals(2, list.size());

        ModelProfileVO platform = list.get(0);
        assertEquals(ModelProfileService.PLATFORM_PROFILE_ID, platform.getProfileId());
        assertEquals("deepseek-flash", platform.getModel());
        assertTrue(platform.getPlatformDefault());
        assertEquals("", platform.getApiKeyMasked(), "平台密钥不回显任何片段");
        assertFalse(platform.getActive(), "已有激活的个人配置时平台默认不再是当前生效项");
        assertTrue(platform.getBaseUrlMasked().startsWith("https://"));

        ModelProfileVO personal = list.get(1);
        assertEquals("1", personal.getProfileId(), "长整型 ID 在 JSON 中为字符串");
        assertTrue(personal.getActive());
        assertEquals("sk-****alue", personal.getApiKeyMasked());
        assertTrue(personal.getCapabilities().isStructured());
        assertFalse(personal.getCapabilities().isNativeTools());
        assertNotNull(personal.getTestedAt());
        assertTrue(personal.getTestedAt().contains("+08:00"), "新增时间必须带明确时区");
    }

    @Test
    void platformDefaultIsActiveWhenNoPersonalProfileIsActive() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        List<ModelProfileVO> list = service.list(OWNER);
        assertEquals(1, list.size());
        assertTrue(list.get(0).getActive());
    }

    @Test
    void baseUrlMaskerStripsCredentialsQueryAndMasksIpLiterals() {
        assertEquals("https://dashscope.example.com/compatible-mode/v1",
                BaseUrlMasker.mask("https://dashscope.example.com/compatible-mode/v1?api-key=SECRET#frag"));
        assertEquals("https://dashscope.example.com/v1",
                BaseUrlMasker.mask("https://user:pass@dashscope.example.com/v1"));
        assertEquals("http://192.168.***.***:11434/v1", BaseUrlMasker.mask("http://192.168.1.20:11434/v1"));
        assertEquals("***", BaseUrlMasker.mask("not a url"));
        assertEquals("", BaseUrlMasker.mask(null));
        assertFalse(BaseUrlMasker.mask("https://a.com/v1?k=SECRET").contains("SECRET"));
    }

    // endregion

    // region 端点解析

    @Test
    void resolveEndpointUsesPlatformDefaultForNullOrZero() {
        ModelEndpoint endpoint = service.resolveEndpoint(OWNER, null);
        assertTrue(endpoint.isPlatformDefault());
        assertEquals("deepseek-flash", endpoint.getModel());
        assertEquals("deepseek-flash", service.resolveEndpoint(OWNER, 0L).getModel());
    }

    @Test
    void resolveEndpointRejectsUntestedProfileInsteadOfSilentlySwitching() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_UNTESTED, false);
        when(mapper.selectById(1L)).thenReturn(profile);
        BusinessException failure = assertThrows(BusinessException.class, () -> service.resolveEndpoint(OWNER, 1L));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), failure.getCode());
        assertTrue(failure.getMessage().contains("测试通过"), "切换模型必须可见，不能悄悄替换");
    }

    @Test
    void resolveEndpointDecryptsKeyOnlyInMemory() {
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);
        ModelEndpoint endpoint = service.resolveEndpoint(OWNER, 1L);
        assertEquals("sk-personal-secret-value", endpoint.getApiKey());
        assertFalse(endpoint.toString().contains("sk-personal-secret-value"),
                "toString 不得包含明文密钥，避免误入日志");
        assertTrue(endpoint.getCapabilities().isStructured());
    }

    @Test
    void resolveEndpointFailsWhenPlatformKeyMissing() {
        when(gateway.platformEndpoint()).thenReturn(null);
        BusinessException failure = assertThrows(BusinessException.class, () -> service.resolveEndpoint(OWNER, null));
        assertTrue(failure.getMessage().contains("AI_ARK_API_KEY"));
    }

    @Test
    void validateProfileForSessionAcceptsPlatformAndRejectsForeign() {
        assertNull(service.validateProfileForSession(OWNER, null));
        assertNull(service.validateProfileForSession(OWNER, "0"));
        assertNull(service.validateProfileForSession(OWNER, "  "));
        assertThrows(BusinessException.class, () -> service.validateProfileForSession(OWNER, "abc"));
        AiModelProfile profile = persisted(1L, OWNER, AiModelProfile.TEST_PASSED, true);
        when(mapper.selectById(1L)).thenReturn(profile);
        assertEquals(1L, service.validateProfileForSession(OWNER, "1").longValue());
        assertThrows(BusinessException.class, () -> service.validateProfileForSession(OTHER, "1"));
    }

    @Test
    void platformProfileCannotBePatched() {
        ModelProfileRequests.Update request = new ModelProfileRequests.Update();
        request.setActive(true);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.update(OWNER, ModelProfileService.PLATFORM_PROFILE_ID, request));
        assertTrue(failure.getMessage().contains("环境变量"));
    }

    @Test
    void testPlatformDefaultProbesRealEndpoint() {
        ModelGateway.CapabilityProbe probe = new ModelGateway.CapabilityProbe();
        probe.setOk(true);
        probe.setLatencyMs(120L);
        probe.setCapabilities(new ModelCapabilities(true, false, false));
        when(gateway.probe(any(ModelEndpoint.class))).thenReturn(probe);
        ModelGateway.CapabilityProbe result = service.test(OWNER, "0");
        assertTrue(result.isOk());
        verify(mapper, never()).updateById(any(AiModelProfile.class));
        // 探测结果进入缓存，列表可展示平台能力
        when(mapper.selectList(any())).thenReturn(Arrays.asList());
        List<ModelProfileVO> list = service.list(OWNER);
        assertTrue(list.get(0).getCapabilities().isChat());
        assertFalse(list.get(0).getCapabilities().isStructured());
    }

    // endregion
}
