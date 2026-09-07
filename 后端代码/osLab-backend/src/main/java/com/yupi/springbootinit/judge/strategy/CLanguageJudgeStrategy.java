package com.yupi.springbootinit.judge.strategy;

import com.yupi.springbootinit.judge.codesandbox.model.JudgeInfo;
import com.yupi.springbootinit.model.dto.question.JudgeCase;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import com.yupi.springbootinit.model.enums.JudgeInfoMessageEnum;

import java.util.List;
import java.util.Optional;

/**
 * Java 程序的判题策略
 */
public class CLanguageJudgeStrategy implements JudgeStrategy{
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        JudgeInfo judgeInfo = judgeContext.getJudgeInfo();
        Long memory = Optional.ofNullable(judgeInfo.getMemory()).orElse(0L);
        Long time = Optional.ofNullable(judgeInfo.getTime()).orElse(0L);
        List<String> inputList = judgeContext.getInputList();
        List<String> outputList = judgeContext.getOutputList();
        Question question = judgeContext.getQuestion();
        List<JudgeCase> judgeCaseList = judgeContext.getJudgeCaseList();
        JudgeInfoMessageEnum judgeInfoMessageEnum = JudgeInfoMessageEnum.ACCEPTED;
        JudgeInfo judgeInfoResponse = new JudgeInfo();
        judgeInfoResponse.setMemory(memory);
        judgeInfoResponse.setTime(time);
        QuestionSubmit questionSubmit=judgeContext.getQuestionSubmit();
        if(!question.getAnswer().equals(questionSubmit.getCode()))
        {
            judgeInfoMessageEnum=JudgeInfoMessageEnum.WRONG_ANSWER;
        }
        judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
        return judgeInfoResponse;
    }
}
