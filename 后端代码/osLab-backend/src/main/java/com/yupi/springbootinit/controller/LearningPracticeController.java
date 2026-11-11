package com.yupi.springbootinit.controller;

import com.yupi.springbootinit.ai.evidence.EvidenceQualification;
import com.yupi.springbootinit.ai.evidence.PracticeAttemptService;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.model.dto.learning.PracticeAttemptCreateRequest;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.learning.PracticeAttemptVO;
import com.yupi.springbootinit.service.UserService;
import lombok.Data;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 普通练习尝试（B）：题卡 → 帮助 → 提交 的统一证据接线。
 * 端点只接受定位与幂等键；帮助等级/资格/提交键由服务端生成。
 */
@RestController
@RequestMapping("/learning/practice")
public class LearningPracticeController {

    @Resource
    private PracticeAttemptService practiceAttempts;

    @Resource
    private UserService userService;

    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/attempts")
    public BaseResponse<PracticeAttemptVO> start(@RequestBody PracticeAttemptCreateRequest request,
                                                 HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        return ResultUtils.success(practiceAttempts.start(user, request));
    }

    /** 提交前记录帮助；失败时不返回成功提示，前端不得先展示提示再补记录。 */
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @PostMapping("/attempts/{id}/help")
    public BaseResponse<Map<String, Object>> help(@PathVariable("id") String id,
                                                  @RequestBody HelpRequest body,
                                                  HttpServletRequest servletRequest) {
        User user = userService.getLoginUser(servletRequest);
        if (body == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        Long contextId;
        try {
            contextId = Long.valueOf(id);
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "尝试上下文 id 不合法");
        }
        EvidenceQualification qualification = practiceAttempts.recordHelp(user, contextId,
                body.getHelpLevel(), body.getNote());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attemptContextId", id);
        data.put("helpLevel", qualification.getHelpLevel());
        data.put("selfReportedHelp", qualification.getSelfReportedHelp());
        data.put("eligibleForObservation", qualification.isEligibleForObservation());
        data.put("eligibilityReason", qualification.getEligibilityReason());
        return ResultUtils.success(data);
    }

    @Data
    public static class HelpRequest implements Serializable {
        /** HINT / ANSWER_REVEALED / NONE */
        private String helpLevel;
        private String note;
    }
}
