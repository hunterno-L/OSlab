package com.yupi.springbootinit.judge;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.judge.codesandbox.CodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.CodeSandboxFactory;
import com.yupi.springbootinit.judge.codesandbox.CodeSandboxProxy;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeResponse;
import com.yupi.springbootinit.judge.codesandbox.model.JudgeInfo;
import com.yupi.springbootinit.judge.strategy.JudgeContext;
import com.yupi.springbootinit.model.dto.question.JudgeCase;
import com.yupi.springbootinit.model.dto.question.JudgeConfig;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.enums.JudgeInfoMessageEnum;
import com.yupi.springbootinit.model.enums.QuestionSubmitStatusEnum;
import com.yupi.springbootinit.service.LearningEventService;
import com.yupi.springbootinit.service.QuestionService;
import com.yupi.springbootinit.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coordinates a submitted solution with the isolated code sandbox and the
 * platform's answer-comparison strategy.
 */
@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final int SANDBOX_SUCCESS = 1;
    private static final long DEFAULT_TIME_LIMIT_MS = 1000L;
    private static final long DEFAULT_MEMORY_LIMIT_MB = 256L;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private LearningEventService learningEventService;

    @Resource
    private JudgeManager judgeManager;

    @Resource
    private CodeSandboxFactory codeSandboxFactory;

    @Value("${codesandbox.type:remote}")
    private String type;

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Question question = questionService.getById(questionSubmit.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 原子地抢占判题权：仅当状态仍为 WAITING 时更新为 RUNNING，
        // 防止同一提交被并发触发导致重复判题与 acceptedNum 重复计数
        QuestionSubmit statusClaim = new QuestionSubmit();
        statusClaim.setId(questionSubmitId);
        statusClaim.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean claimed = questionSubmitService.update(statusClaim,
                new UpdateWrapper<QuestionSubmit>()
                        .eq("id", questionSubmitId)
                        .eq("status", QuestionSubmitStatusEnum.WAITING.getValue()));
        if (!claimed) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该提交已进入判题流程");
        }
        try {
            List<JudgeCase> judgeCases = JSONUtil.toList(question.getJudgeCase(), JudgeCase.class);
            if (CollectionUtils.isEmpty(judgeCases)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目没有可用的判题用例");
            }
            JudgeConfig judgeConfig = JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class);
            long timeLimit = Optional.ofNullable(judgeConfig)
                    .map(JudgeConfig::getTimeLimit).filter(value -> value > 0).orElse(DEFAULT_TIME_LIMIT_MS);
            long memoryLimit = Optional.ofNullable(judgeConfig)
                    .map(JudgeConfig::getMemoryLimit).filter(value -> value > 0).orElse(DEFAULT_MEMORY_LIMIT_MB);
            List<String> inputList = judgeCases.stream().map(JudgeCase::getInput).collect(Collectors.toList());

            ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                    .code(questionSubmit.getCode())
                    .language(questionSubmit.getLanguage())
                    .inputList(inputList)
                    .timeLimit(timeLimit)
                    .memoryLimit(memoryLimit)
                    .build();
            CodeSandbox codeSandbox = new CodeSandboxProxy(codeSandboxFactory.getCodeSandbox(type));
            ExecuteCodeResponse response = codeSandbox.executeCode(request);

            JudgeInfo judgeInfo;
            if (response == null || !Integer.valueOf(SANDBOX_SUCCESS).equals(response.getStatus())) {
                // Compile/runtime errors are completed judgements, not platform
                // outages. Persist the sandbox's precise message for the user.
                judgeInfo = sandboxFailure(response);
            } else {
                JudgeContext judgeContext = new JudgeContext();
                judgeContext.setJudgeInfo(Optional.ofNullable(response.getJudgeInfo()).orElseGet(JudgeInfo::new));
                judgeContext.setInputList(inputList);
                judgeContext.setOutputList(Optional.ofNullable(response.getOutputList()).orElse(Collections.emptyList()));
                judgeContext.setJudgeCaseList(judgeCases);
                judgeContext.setQuestion(question);
                judgeContext.setQuestionSubmit(questionSubmit);
                judgeInfo = judgeManager.doJudge(judgeContext);
            }
            if (JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())) {
                boolean acceptedCountUpdated = questionService.update(new UpdateWrapper<Question>()
                        .eq("id", question.getId())
                        .setSql("acceptedNum = acceptedNum + 1"));
                if (!acceptedCountUpdated) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "通过题目计数更新失败");
                }
            }
            updateSubmission(questionSubmitId, QuestionSubmitStatusEnum.SUCCEED.getValue(), judgeInfo);
            // WP2：判题终态写入学习事件（幂等；系统故障不计学生知识失误）
            learningEventService.recordJudgeEvents(questionSubmitId,
                    QuestionSubmitStatusEnum.SUCCEED.getValue(), judgeInfo.getMessage());
            return questionSubmitService.getById(questionSubmitId);
        } catch (Exception e) {
            log.error("judge infrastructure failed for submissionId={}", questionSubmitId, e);
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            judgeInfo.setTime(0L);
            judgeInfo.setMemory(0L);
            updateSubmission(questionSubmitId, QuestionSubmitStatusEnum.FAILED.getValue(), judgeInfo);
            learningEventService.recordJudgeEvents(questionSubmitId,
                    QuestionSubmitStatusEnum.FAILED.getValue(), JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            return questionSubmitService.getById(questionSubmitId);
        }
    }

    private JudgeInfo sandboxFailure(ExecuteCodeResponse response) {
        JudgeInfo judgeInfo = response == null ? null : response.getJudgeInfo();
        if (judgeInfo == null) {
            judgeInfo = new JudgeInfo();
        }
        if (StringUtils.isBlank(judgeInfo.getMessage())) {
            judgeInfo.setMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue());
        }
        judgeInfo.setTime(Optional.ofNullable(judgeInfo.getTime()).orElse(0L));
        judgeInfo.setMemory(Optional.ofNullable(judgeInfo.getMemory()).orElse(0L));
        return judgeInfo;
    }

    private void updateSubmission(long questionSubmitId, int status, JudgeInfo judgeInfo) {
        QuestionSubmit update = new QuestionSubmit();
        update.setId(questionSubmitId);
        update.setStatus(status);
        if (judgeInfo != null) {
            update.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        }
        if (!questionSubmitService.updateById(update)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
    }
}
