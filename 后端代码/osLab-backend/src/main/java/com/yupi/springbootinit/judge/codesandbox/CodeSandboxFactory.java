package com.yupi.springbootinit.judge.codesandbox;

import com.yupi.springbootinit.judge.codesandbox.impl.ExampleCodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.impl.OSLabCodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.impl.RemoteCodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.impl.ThirdPartyCodeSandbox;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 代码沙箱工厂（根据字符串参数创建指定的代码沙箱实例）
 */
@Component
public class CodeSandboxFactory {
    @Resource
    private RemoteCodeSandbox remoteCodeSandbox;

    /**
     * Production resolver. Demo implementations must never be selected by a
     * production profile because they do not execute untrusted code.
     */
    public CodeSandbox getCodeSandbox(String type) {
        if ("remote".equals(type)) {
            return remoteCodeSandbox;
        }
        throw new IllegalStateException("unsupported production code sandbox type: " + type);
    }

    /**
     * 创建代码沙箱示例
     *
     * @param type 沙箱类型
     * @return
     */
    public static CodeSandbox newInstance(String type) {
        switch (type) {
            case "example":
                return new ExampleCodeSandbox();
            case "remote":
                return new RemoteCodeSandbox();
            case "thirdParty":
                return new ThirdPartyCodeSandbox();
            case "osLab":
                return new OSLabCodeSandbox();
            default:
                return new ExampleCodeSandbox();
        }
    }
}
