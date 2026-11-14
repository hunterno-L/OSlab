package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.ai.ModelGateway;
import com.yupi.springbootinit.ai.profile.ModelProfileService;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.model.dto.ai.ModelProfileRequests;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.ModelProfileVO;
import com.yupi.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人模型配置接口（任务书 §3 冻结契约）：{@code /ai/model-profiles}。
 *
 * <p>鉴权沿用既有登录态；所有读写都限定为本人配置。
 * 响应只出现脱敏值，API Key 明文与密文都不出网。
 */
@Slf4j
@RestController
@RequestMapping("/ai/model-profiles")
public class AiModelProfileController {

    @Resource
    private ModelProfileService modelProfileService;

    @Resource
    private UserService userService;

    /** 脱敏读取：平台默认 + 本人候选/已激活配置。 */
    @GetMapping
    public BaseResponse<List<ModelProfileVO>> list(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(modelProfileService.list(user.getId()));
    }

    /** 新建候选配置（未测试不可激活）。 */
    @PostMapping
    public BaseResponse<Map<String, Object>> create(@RequestBody ModelProfileRequests.Create body,
                                                    HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        String profileId = modelProfileService.create(user.getId(), body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profileId", profileId);
        data.put("status", "CANDIDATE");
        return ResultUtils.success(data);
    }

    /**
     * 真实调用测试：探测 chat / structured / nativeTools 三项能力。
     * 失败以 {@code ok=false} + {@code error} 返回，不抛异常，界面可直接展示原因。
     */
    @PostMapping("/test")
    public BaseResponse<Map<String, Object>> test(@RequestBody ModelProfileRequests.Test body,
                                                  HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        String profileId = body == null ? null : body.getProfileId();
        ModelGateway.CapabilityProbe probe = modelProfileService.test(user.getId(), profileId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", probe.isOk());
        data.put("latencyMs", probe.getLatencyMs());
        data.put("capabilities", probe.getCapabilities());
        if (probe.getError() != null) {
            data.put("error", probe.getError());
        }
        return ResultUtils.success(data);
    }

    /** 删除个人配置（逻辑删除）；平台默认配置不可删。 */
    @DeleteMapping("/{id}")
    public BaseResponse<String> delete(@PathVariable("id") String id,
                                       HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        modelProfileService.delete(user.getId(), id);
        return ResultUtils.success("已删除");
    }

    /** 更新 / 激活：仅测试通过的候选可激活。 */
    @PatchMapping("/{id}")
    public BaseResponse<ModelProfileVO> update(@PathVariable("id") String id,
                                               @RequestBody ModelProfileRequests.Update body,
                                               HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(modelProfileService.update(user.getId(), id, body));
    }
}
