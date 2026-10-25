package com.yupi.springbootinit.learning;

import com.yupi.springbootinit.model.enums.JudgeInfoMessageEnum;
import com.yupi.springbootinit.model.enums.learning.LearningResultEnum;
import com.yupi.springbootinit.model.enums.QuestionSubmitStatusEnum;

/** One interpretation of sandbox terminal results for evidence and course progress. */
public final class JudgeResultClassifier {
    private JudgeResultClassifier() { }

    public static boolean isTerminal(int status) {
        return status == QuestionSubmitStatusEnum.SUCCEED.getValue()
                || status == QuestionSubmitStatusEnum.FAILED.getValue();
    }

    public static LearningResultEnum classify(int status, String judgeMessage) {
        if (status == QuestionSubmitStatusEnum.FAILED.getValue()) return LearningResultEnum.SYSTEM_ERROR;
        String error = LearningAttributionService.attribute("JUDGE", judgeMessage, "").getErrorType();
        if (LearningAttributionService.ERROR_SYSTEM.equals(error)) return LearningResultEnum.SYSTEM_ERROR;
        if (LearningAttributionService.ERROR_COMPILE.equals(error)) return LearningResultEnum.COMPILE_ERROR;
        String message = judgeMessage == null ? "" : judgeMessage.trim();
        boolean accepted = status == QuestionSubmitStatusEnum.SUCCEED.getValue()
                && (JudgeInfoMessageEnum.ACCEPTED.getValue().equalsIgnoreCase(message)
                || JudgeInfoMessageEnum.ACCEPTED.getText().equalsIgnoreCase(message));
        return accepted ? LearningResultEnum.PASS : LearningResultEnum.FAIL;
    }
}
