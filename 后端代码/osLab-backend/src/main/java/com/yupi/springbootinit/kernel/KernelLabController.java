package com.yupi.springbootinit.kernel;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * xv6 真实内核实验：在 WSL 里构建并运行 QEMU，返回判题结果与日志。
 * 只接受白名单测试，不接收任意命令。
 */
@RestController
@RequestMapping("/kernel")
public class KernelLabController {

    private static final String STARTER = "/mnt/g/ZhiCore/kernel-lab/xv6-lab/xv6-riscv";
    private static final Map<String, String[]> TESTS = Map.of(
            "boot", new String[]{"echo kernel_ok", "kernel_ok", "15"},
            "copyin", new String[]{"usertests copyin", "OK", "90"},
            "usertests", new String[]{"usertests -q", "ALL TESTS PASSED", "260"}
    );

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody(required = false) Map<String, Object> body) {
        String test = body == null || body.get("test") == null ? "boot" : String.valueOf(body.get("test"));
        String[] spec = TESTS.get(test);
        Map<String, Object> result = new LinkedHashMap<>();
        if (spec == null) {
            result.put("ok", false);
            result.put("error", "未知测试：" + test);
            return result;
        }
        String marker = spec[1];
        int wait = Integer.parseInt(spec[2]);
        String script = "cd " + STARTER + " || exit 2\n"
                + "make TOOLPREFIX=riscv64-linux-gnu- -j2 >/tmp/kbuild.log 2>&1 || { echo BUILD_FAILED; tail -20 /tmp/kbuild.log; exit 3; }\n"
                + "make TOOLPREFIX=riscv64-linux-gnu- fs.img >>/tmp/kbuild.log 2>&1 || { echo FS_FAILED; exit 4; }\n"
                + "(sleep 4; printf '" + spec[0] + "\\n'; sleep " + wait + ") | timeout " + (wait + 40)
                + " make TOOLPREFIX=riscv64-linux-gnu- qemu > /tmp/krun.log 2>&1\n"
                + "if grep -q '" + marker + "' /tmp/krun.log; then echo RESULT=PASS; else echo RESULT=FAIL; fi\n"
                + "echo '---LOG---'\n"
                + "tail -25 /tmp/krun.log\n";

        long start = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        int exit = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu-24.04", "-u", "root", "bash", "-lc", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (!process.waitFor(wait + 120L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            exit = process.exitValue();
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "执行失败：" + e.getMessage());
            return result;
        }

        String output = String.join("\n", lines);
        result.put("ok", output.contains("RESULT=PASS"));
        result.put("test", test);
        result.put("seconds", Math.round((System.currentTimeMillis() - start) / 100.0) / 10.0);
        result.put("exit", exit);
        result.put("log", output.length() > 4000 ? output.substring(output.length() - 4000) : output);
        return result;
    }
}
