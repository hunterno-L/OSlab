package com.yupi.springbootinit.ai.evidence;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.learning.ActivityContextService;
import com.yupi.springbootinit.learning.LearningQuestionScopeService;
import com.yupi.springbootinit.learning.SelfTestPolicy;
import com.yupi.springbootinit.mapper.LearningQuestionMapper;
import com.yupi.springbootinit.model.dto.learning.PracticeAttemptCreateRequest;
import com.yupi.springbootinit.model.entity.LearningEvidenceContext;
import com.yupi.springbootinit.model.entity.LearningQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.learning.LearningReviewStatusEnum;
import com.yupi.springbootinit.model.vo.learning.PracticeAttemptVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 普通练习尝试上下文：把题卡、帮助记录与提交串到同一个服务端证据上下文。
 *
 * <p>客户端只提交定位与幂等键；帮助等级、资格与提交键全部由服务端生成。
 */
@Service
public class PracticeAttemptService {

    @Resource
    private LearningQuestionMapper learningQuestionMapper;

    @Resource
    private LearningEvidenceContextService evidenceContexts;

    @Resource
    private SelfTestPolicy selfTestPolicy;

    @Resource
    private ActivityContextService activityContext;

    @Resource
    private LearningQuestionScopeService questionScope;

    /** 创建或复用一次普通练习尝试。 */
    public PracticeAttemptVO start(User user, PracticeAttemptCreateRequest request) {
        ThrowUtils.throwIf(user == null || request == null || request.getQuestionId() == null,
                ErrorCode.PARAMS_ERROR, "缺少题目");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getClientAttemptKey())
                        || request.getClientAttemptKey().length() > 64,
                ErrorCode.PARAMS_ERROR, "缺少尝试标识");
        LearningQuestion question = learningQuestionMapper.selectById(request.getQuestionId());
        ThrowUtils.throwIf(question == null || Integer.valueOf(1).equals(question.getIsDelete())
                        || !LearningReviewStatusEnum.isStudentVisible(question.getReviewStatus()),
                ErrorCode.NOT_FOUND_ERROR, "题目不存在或未发布");
        ThrowUtils.throwIf("TASK".equals(question.getResourceScope()),
                ErrorCode.NOT_FOUND_ERROR, "任务题目请从学习任务入口作答");
        if (selfTestPolicy.isLocked(user.getId(), question.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该题正在整组自测中，交卷前不建立练习尝试；请先交卷再进入辅导");
        }
        validateCourse(request.getCourseCode(), question.getId());
        if (activityContext != null) {
            activityContext.validate(request.getSourceUnitCode(), request.getSourceActivityCode(),
                    question.getId(), false);
        }
        String contextKey = "practice:" + question.getId() + ":" + request.getClientAttemptKey().trim();
        LearningEvidenceContext context = evidenceContexts.begin(user.getId(), contextKey,
                LearningEvidenceContext.KIND_QUESTION, String.valueOf(question.getId()),
                request.getCourseCode(), question.getRevision(), null, null, null);
        if (!String.valueOf(question.getId()).equals(context.getActivityCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "尝试上下文与题目不匹配");
        }
        PracticeAttemptVO vo = new PracticeAttemptVO();
        vo.setAttemptContextId(String.valueOf(context.getId()));
        vo.setQuestionId(String.valueOf(question.getId()));
        vo.setQuestionRevision(question.getRevision());
        vo.setSubmissionKey("attempt:" + context.getId());
        vo.setHelpLevel(context.getHelpLevel());
        vo.setStatus("READY");
        return vo;
    }

    /** 提交前记录帮助：独立事务先落库，再允许生成/展示提示。 */
    public EvidenceQualification recordHelp(User user, Long attemptContextId, String helpLevel, String note) {
        LearningEvidenceContext context = evidenceContexts.findOwn(user.getId(), attemptContextId);
        ThrowUtils.throwIf(context == null, ErrorCode.NO_AUTH_ERROR, "尝试上下文不存在或无权访问");
        Long questionId = parseLong(context.getActivityCode());
        ThrowUtils.throwIf(questionId == null, ErrorCode.PARAMS_ERROR, "尝试上下文未绑定题目");
        if (selfTestPolicy.isLocked(user.getId(), questionId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该题正在整组自测中，交卷前不提供提示或答案；请先交卷再进入辅导");
        }
        return evidenceContexts.recordHelp(user.getId(), context.getId(), helpLevel, null, note);
    }

    private void validateCourse(String courseCode, Long questionId) {
        if (StringUtils.isBlank(courseCode) || questionScope == null) {
            return;
        }
        LearningQuestionScopeService.Scope scope = questionScope.resolve(courseCode, null, null, null);
        if (!scope.isConstrained()) {
            return;
        }
        Set<Long> ids = scope.getQuestionIds();
        ThrowUtils.throwIf(ids == null || !ids.contains(questionId), ErrorCode.NO_AUTH_ERROR,
                "题目不属于该课程");
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
