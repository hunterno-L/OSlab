package com.yupi.springbootinit.judge;

import com.yupi.springbootinit.judge.strategy.*;
import com.yupi.springbootinit.judge.codesandbox.model.JudgeInfo;
import com.yupi.springbootinit.model.entity.QuestionSubmit;
import org.springframework.stereotype.Service;

/**
 * 判题管理（简化调用）
 */
@Service
public class JudgeManager {
    /**
     * 执行判题
     *
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext) {
        // All supported languages must be judged against the same test-case
        // contract. Language-specific strategies in the demo either bypassed
        // output checking or compared source code, so they cannot be used.
        JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
        return judgeStrategy.doJudge(judgeContext);
    }
}
