package com.yupi.springbootinit.judge.codesandbox;

import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSandboxProxy implements CodeSandbox{
    private final CodeSandbox codeSandbox;


    public CodeSandboxProxy(CodeSandbox codeSandbox) {
        this.codeSandbox = codeSandbox;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        // Source code and test input are private. Log only safe request metadata.
        log.info("code sandbox request: language={}, cases={}",
                executeCodeRequest.getLanguage(),
                executeCodeRequest.getInputList() == null ? 0 : executeCodeRequest.getInputList().size());
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        log.info("code sandbox response: status={}, judgeMessage={}",
                executeCodeResponse == null ? null : executeCodeResponse.getStatus(),
                executeCodeResponse == null || executeCodeResponse.getJudgeInfo() == null
                        ? null : executeCodeResponse.getJudgeInfo().getMessage());
        return executeCodeResponse;
    }
}
