package com.yupi.springbootinit.ai.tutor;

import com.yupi.springbootinit.ai.evidence.TutorAssessmentGuard;
import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 把 B 的 {@link TutorAssessmentGuard} 适配成 A 的守卫端口。
 *
 * <p>B 的实现绑定真实尝试/测评数据并在允许提示前持久化帮助等级；
 * A 只做协议转换（字符串 ID → 服务端 Long，公开原因字段名映射）。
 * B 的 Bean 不存在时回退到 {@link DefaultTutorAssessmentGuard} 的保守规则。
 */
@Slf4j
@Component
public class TutorAssessmentGuardAdapter implements TutorAssessmentGuardPort {

    @Autowired(required = false)
    private TutorAssessmentGuard delegate;

    @Override
    public Decision check(User user, AiLearningSession session, AiCourseContext context,
                          String intent, String message) {
        if (delegate == null) {
            return DefaultTutorAssessmentGuard.check(user, session, context, intent);
        }
        Long attemptContextId = parseLong(context == null ? null : context.getAttemptContextId());
        Long assessmentRunId = parseLong(context == null ? null : context.getAssessmentRunId());
        if (StringUtils.isNotBlank(context == null ? null : context.getAttemptContextId())
                && attemptContextId == null) {
            return Decision.deny("尝试上下文不存在或无权访问");
        }
        if (StringUtils.isNotBlank(context == null ? null : context.getAssessmentRunId())
                && assessmentRunId == null) {
            return Decision.deny("测评会话不存在或无权访问");
        }
        try {
            TutorAssessmentGuard.Decision decision = delegate.check(user,
                    TutorAssessmentGuard.Request.of(questionId(context), attemptContextId,
                            assessmentRunId, intent));
            if (decision == null) {
                return Decision.deny("导师帮助校验未返回结果，请稍后重试。");
            }
            if (!decision.isAllowed()) {
                return Decision.deny(StringUtils.defaultIfBlank(decision.getReason(),
                        "当前题目暂不提供辅导，请先完成当前学习步骤。"));
            }
            Decision mapped = Decision.allow(StringUtils.defaultIfBlank(decision.getHelpLevel(), "NONE"));
            mapped.setAttemptContextId(StringUtils.defaultIfBlank(
                    decision.getAttemptContextId() == null ? null
                            : String.valueOf(decision.getAttemptContextId()),
                    context == null ? null : context.getAttemptContextId()));
            mapped.setAssessmentRunId(StringUtils.defaultIfBlank(
                    decision.getAssessmentRunId() == null ? null
                            : String.valueOf(decision.getAssessmentRunId()),
                    context == null ? null : context.getAssessmentRunId()));
            mapped.setPublicReason(decision.getReason());
            return mapped;
        } catch (RuntimeException e) {
            log.warn("[ai-tutor] assessment guard adapter failed: {}", e.getClass().getSimpleName());
            return Decision.deny("导师帮助校验暂时不可用，请稍后重试。");
        }
    }

    /** 从 resourceRef（question:123）或纯数字解析题目 id；非题目场景返回 null。 */
    private static Long questionId(AiCourseContext context) {
        if (context == null) {
            return null;
        }
        String ref = StringUtils.trimToEmpty(context.getResourceRef());
        if (StringUtils.isBlank(ref)) {
            return null;
        }
        String value = ref.startsWith("question:") ? ref.substring("question:".length()) : ref;
        if (!value.matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (StringUtils.isBlank(value) || !value.trim().matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
