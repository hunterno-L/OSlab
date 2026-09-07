package com.yupi.springbootinit.judge.codesandbox;

import com.yupi.springbootinit.judge.codesandbox.impl.RemoteCodeSandbox;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.springbootinit.judge.codesandbox.model.ExecuteCodeResponse;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.enums.QuestionSubmitLanguageEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
class CodeSandboxTest {

    @Test
    void unconfiguredRemoteSandboxIsRejected() {
        CodeSandbox codeSandbox = new RemoteCodeSandbox();
        Assertions.assertThrows(BusinessException.class, () -> codeSandbox.executeCode(ExecuteCodeRequest.builder()
                .code("public class Main { public static void main(String[] args) {} }")
                .language(QuestionSubmitLanguageEnum.JAVA.getValue())
                .inputList(Arrays.asList(""))
                .build()));
    }

    @Test
    void executeCodeByValue() {
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance("example");
        String code = "int main() { }";
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
        List<String> inputList = Arrays.asList("1 2", "3 4");
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        Assertions.assertNotNull(executeCodeResponse);
    }

    @Test
    void executeCodeByProxy() {
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance("osLab");
        codeSandbox = new CodeSandboxProxy(codeSandbox);
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = Integer.parseInt(args[0]);\n" +
                "        int b = Integer.parseInt(args[1]);\n" +
                "        System.out.println(\"结果:\" + (a + b));\n" +
                "    }\n" +
                "}";
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
        List<String> inputList = Arrays.asList("1 2", "3 4");
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        Assertions.assertNotNull(executeCodeResponse);
    }
}
