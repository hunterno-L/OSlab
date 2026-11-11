package com.yupi.springbootinit.ai.tutor;

import com.yupi.springbootinit.model.entity.AiLearningSession;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ai.AiCourseContext;
import lombok.Data;

/**
 * 导师帮助守卫端口（01 契约 §4.2）。
 *
 * <p>A 在调度题目辅导前调用；B 的 {@code TutorAssessmentGuard} 以实现该接口的
 * Spring Bean 接入（同名或 {@code @Primary} 均可）。没有 Bean 时 A 使用
 * {@link DefaultTutorAssessmentGuard} 的保守规则：无上下文提示直接拒绝。
 *
 * <p>实现方必须在允许本轮提示前，把帮助等级写入所属尝试（HINT / ANSWER_REVEALED），
 * 自测 ACTIVE 中本组题必须拒绝；拒绝原因只能包含可公开的信息。
 */
public interface TutorAssessmentGuardPort {

    Decision check(User user, AiLearningSession session, AiCourseContext context,
                   String intent, String message);

    @Data
    class Decision {
        private boolean allowed;
        /** 可公开给学生的原因（拒绝时必填；允许时可为空）。 */
        private String publicReason;
        /** 已核验的尝试上下文 id（可回填）。 */
        private String attemptContextId;
        /** 已核验的评估运行 id（可回填）。 */
        private String assessmentRunId;
        /** 已记录的帮助等级：NONE / HINT / ANSWER_REVEALED / HINT_UNVERIFIED。 */
        private String helpLevel;

        public static Decision allow(String helpLevel) {
            Decision decision = new Decision();
            decision.setAllowed(true);
            decision.setHelpLevel(helpLevel);
            return decision;
        }

        public static Decision deny(String reason) {
            Decision decision = new Decision();
            decision.setAllowed(false);
            decision.setPublicReason(reason);
            return decision;
        }
    }
}
