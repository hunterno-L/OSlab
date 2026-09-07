package com.yupi.springbootinit.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.model.dto.question.JudgeCase;
import com.yupi.springbootinit.model.dto.question.JudgeConfig;
import com.yupi.springbootinit.judge.codesandbox.model.JudgeInfo;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.enums.JudgeInfoMessageEnum;

import java.util.List;
import java.util.Optional;

/**
 * 默认判题策略
 */
public class DefaultJudgeStrategy implements JudgeStrategy{
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
        // 先判断沙箱执行的结果输出数量是否和预期输出数量相等
        if (outputList.size() != inputList.size()) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 依次判断每一项输出和预期输出是否相等
        for (int i = 0; i < judgeCaseList.size(); i++) {
            JudgeCase judgeCase = judgeCaseList.get(i);
            if (!normalizeOutput(judgeCase.getOutput()).equals(normalizeOutput(outputList.get(i)))) {
                judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
                judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
                return judgeInfoResponse;
            }
        }
        // 判断题目限制
        String judgeConfigStr = question.getJudgeConfig();
        JudgeConfig judgeConfig = JSONUtil.toBean(judgeConfigStr, JudgeConfig.class);
        Long needMemoryLimit = Optional.ofNullable(judgeConfig.getMemoryLimit()).orElse(Long.MAX_VALUE);
        Long needTimeLimit = Optional.ofNullable(judgeConfig.getTimeLimit()).orElse(Long.MAX_VALUE);
        if (memory > needMemoryLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        if (time > needTimeLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
        return judgeInfoResponse;
    }

    /**
     * OJ answers should not fail merely because an operating system emitted a
     * different line ending or the final line has trailing whitespace.
     */
    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+(?=\\n)", "")
                .replaceAll("\\n+$", "");
    }
}
