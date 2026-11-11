package com.yupi.springbootinit.ai.evidence;

import com.yupi.springbootinit.learning.SelfTestPolicy;
import com.yupi.springbootinit.mapper.LearningAssessmentRunMapper;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.model.entity.LearningAssessmentRun;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;

/**
 * 题目辅导准入守卫（B 定义，A 在调度题目辅导前调用）。
 *
 * <p>职责：
 * <ol>
 *   <li>整组自测进行中：本组题一律禁止辅导/取答案（服务端 ACTIVE + SELF_TEST + 队列关联）；</li>
 *   <li>普通未提交题：允许辅导，但先建立/复用尝试上下文，并按意图先持久化帮助等级；</li>
 *   <li>返回已核验题目定位、帮助等级与可公开原因，A 不需要重复实现这些判断。</li>
 * </ol>
 *
 * <p>守卫不生成答案，也不依赖模型自述；它只决定“能不能辅导、记什么帮助等级”。
 */
@Service
public class TutorAssessmentGuard {

    @Resource
    private SelfTestPolicy selfTestPolicy;

    @Resource
    private LearningAssessmentRunMapper assessmentRunMapper;

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningEvidenceContextService evidenceContexts;

    /**
     * 校验一次题目辅导请求。
     *
     * @param user    登录用户（服务端会话）
     * @param request 已解析的 questionId / attemptContextId / assessmentRunId / intent
     */
    public Decision check(User user, Request request) {
        Decision decision = new Decision();
        decision.setAllowed(true);
        if (user == null || request == null) {
            return deny(decision, "缺少用户或辅导上下文");
        }
        decision.setAttemptContextId(request.getAttemptContextId());
        decision.setAssessmentRunId(request.getAssessmentRunId());
        Long questionId = request.getQuestionId();

        // 1) 明确携带了测评 run：ACTIVE SELF_TEST 一律阻断
        if (request.getAssessmentRunId() != null) {
            LearningAssessmentRun run = assessmentRunMapper.selectById(request.getAssessmentRunId());
            if (run == null || !run.getUserId().equals(user.getId())) {
                return deny(decision, "测评会话不存在或无权访问");
            }
            if (SelfTestPolicy.isSelfTest(run) && "ACTIVE".equals(run.getStatus())) {
                return deny(decision, "整组自测进行中，本组题不提供提示或答案；请先交卷再进入辅导");
            }
        }

        // 2) 从尝试上下文解析题目归属（A 可以只传 attemptContextId）
        LearningEvidenceContext context = null;
        if (request.getAttemptContextId() != null) {
            context = evidenceContexts.findOwn(user.getId(), request.getAttemptContextId());
            if (context == null) {
                return deny(decision, "尝试上下文不存在或无权访问");
            }
            Long contextQuestionId = parseLong(context.getActivityCode());
            if (questionId == null) {
                questionId = contextQuestionId;
            } else if (!questionId.equals(contextQuestionId)) {
                return deny(decision, "尝试上下文与当前题目不匹配");
            }
        }

        // 3) 即使前端删掉 runId，也按服务端 ACTIVE 自测队列阻断
        if (questionId != null) {
            LearningAssessmentRun active = selfTestPolicy.activeSelfTestContaining(user.getId(), questionId);
            if (active != null) {
                decision.setAssessmentRunId(active.getId());
                return deny(decision, "该题正在整组自测中，交卷前不提供提示或答案；可先交卷再进入辅导");
            }
        }

        if (questionId == null) {
            // 非题目场景（全局/单元/节点/实验）：不阻断，也不记录题目帮助
            decision.setQuestionId(null);
            decision.setHelpLevel(EvidenceQualification.HELP_UNKNOWN);
            return decision;
        }

        decision.setQuestionId(String.valueOf(questionId));
        LearningQuestion question = learningQuestionMapper.selectById(questionId);
        if (question == null || Integer.valueOf(1).equals(question.getIsDelete())
                || !"PUBLISHED".equals(question.getReviewStatus())) {
            return deny(decision, "题目不存在或已下线");
        }
        decision.setQuestionRevision(question.getRevision());

        // 4) 普通题：需要提示/解释时先把帮助等级写入尝试上下文（独立事务，答题事务之前生效）
        String helpLevel = context == null ? EvidenceQualification.HELP_UNKNOWN : context.getHelpLevel();
        boolean consumed = context != null && context.getConsumed() != null && context.getConsumed() == 1;
        if (context != null && !consumed && isHelpIntent(request.getIntent())) {
            EvidenceQualification qualification = evidenceContexts.recordHelp(user.getId(), context.getId(),
                    EvidenceQualification.HELP_HINT, null, "导师入口请求题目提示");
            helpLevel = qualification.getHelpLevel();
        }
        decision.setHelpLevel(helpLevel);
        if (context == null) {
            decision.setReason("普通题辅导未绑定尝试上下文：不改变证据资格（历史行为）");
        } else if (consumed) {
            decision.setReason("该尝试已提交：可以解释，不影响已产生的证据");
        } else {
            decision.setReason("允许辅导；提示请求已记录在尝试上下文中");
        }
        return decision;
    }

    /** HINT/EXPLAIN 在未提交题目上都按“查看提示”处理；DEBUG 属于实验场景。 */
    private boolean isHelpIntent(String intent) {
        String value = StringUtils.upperCase(StringUtils.trimToEmpty(intent));
        return "HINT".equals(value) || "EXPLAIN".equals(value);
    }

    private Decision deny(Decision decision, String reason) {
        decision.setAllowed(false);
        decision.setReason(reason);
        return decision;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Data
    public static class Request implements Serializable {
        private Long questionId;
        private Long attemptContextId;
        private Long assessmentRunId;
        /** ASK / EXPLAIN / HINT / DEBUG / PLAN */
        private String intent;

        public static Request of(Long questionId, Long attemptContextId, Long assessmentRunId, String intent) {
            Request request = new Request();
            request.setQuestionId(questionId);
            request.setAttemptContextId(attemptContextId);
            request.setAssessmentRunId(assessmentRunId);
            request.setIntent(intent);
            return request;
        }
    }

    @Data
    public static class Decision implements Serializable {
        /** 是否允许继续调用模型/返回辅导。 */
        private boolean allowed;
        /** 可公开原因（可直接展示给学生；不含内部细节）。 */
        private String reason;
        /** 已核验的题目定位（字符串 id）。 */
        private String questionId;
        private Integer questionRevision;
        /** 记录后的帮助等级：NONE / HINT / ANSWER_REVEALED / UNKNOWN。 */
        private String helpLevel = EvidenceQualification.HELP_UNKNOWN;
        /** 服务端已核验的尝试/测评定位。 */
        private Long attemptContextId;
        private Long assessmentRunId;
    }
}
