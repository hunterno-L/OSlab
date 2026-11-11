package com.yupi.springbootinit.ai.tutor;

import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import org.apache.commons.lang3.StringUtils;

/**
 * 默认导师守卫：B 的 {@code TutorAssessmentGuard} 未接入时的保守兜底。
 *
 * <p>只处理协议层可判断的部分：题目提示必须带服务端建立的
 * {@code attemptContextId} 或 {@code assessmentRunId}，否则拒绝——避免前端
 * 通过“切到全局问答”把提示请求降级成无记录的普通问答。是否处于
 * ACTIVE 自测、是否需要记录帮助等级，必须由 B 的实现判断。
 */
public final class DefaultTutorAssessmentGuard {

    public static final String HELP_UNVERIFIED = "HINT_UNVERIFIED";

    private DefaultTutorAssessmentGuard() {
    }

    public static TutorAssessmentGuardPort.Decision check(User user, AiLearningSession session,
                                                          AiCourseContext context, String intent) {
        if (!"HINT".equalsIgnoreCase(StringUtils.trimToEmpty(intent))) {
            return TutorAssessmentGuardPort.Decision.allow("NONE");
        }
        boolean hasAttempt = context != null && StringUtils.isNotBlank(context.getAttemptContextId());
        boolean hasRun = context != null && StringUtils.isNotBlank(context.getAssessmentRunId());
        if (!hasAttempt && !hasRun) {
            return TutorAssessmentGuardPort.Decision.deny(
                    "提示需要关联有效的题目尝试；请先在题目卡片中开始作答，再请求提示。");
        }
        // 未接入 B 的守卫时不能宣称已经持久化帮助等级，只如实标记“未核验的提示”。
        TutorAssessmentGuardPort.Decision decision = TutorAssessmentGuardPort.Decision
                .allow(HELP_UNVERIFIED);
        decision.setAttemptContextId(context.getAttemptContextId());
        decision.setAssessmentRunId(context.getAssessmentRunId());
        return decision;
    }
}
