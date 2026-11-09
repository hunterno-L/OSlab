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

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        String patch = body == null || body.get("patch") == null ? "" : String.valueOf(body.get("patch"));
        String test = body == null || body.get("test") == null ? "banner" : String.valueOf(body.get("test"));
        Map<String, Object> result = new LinkedHashMap<>();
        if (patch.contains("../../") || patch.contains("Makefile")) {
            result.put("ok", false);
            result.put("error", "提交内容不允许修改 Makefile 或越界路径");
            return result;
        }
        if (!patch.isEmpty() && !patch.endsWith("\n")) {
            patch = patch + "\n";
        }
        String marker = "usertests".equals(test) ? "ALL TESTS PASSED" : "ZhiCore";
        String runCmd = "usertests".equals(test)
                ? "printf 'usertests -q\\n'; sleep 250"
                : "sleep 6";
        int wait = "usertests".equals(test) ? 260 : 20;

        String patchB64 = java.util.Base64.getEncoder()
                .encodeToString(patch.getBytes(StandardCharsets.UTF_8));
        String script = String.join("\n",
                "set -e",
                "rm -rf /mnt/g/ZhiCore/kernel-lab/web-run",
                "cp -r " + STARTER + " /mnt/g/ZhiCore/kernel-lab/web-run",
                "cd /mnt/g/ZhiCore/kernel-lab/web-run",
                "echo '" + patchB64 + "' | base64 -d > /tmp/sub.patch",
                "if [ -s /tmp/sub.patch ]; then patch -p1 --batch --forward < /tmp/sub.patch || { echo PATCH_FAILED; exit 5; }; fi",
                "make TOOLPREFIX=riscv64-linux-gnu- -j2 >/tmp/kbuild.log 2>&1 || { echo BUILD_FAILED; tail -20 /tmp/kbuild.log; exit 3; }",
                "make TOOLPREFIX=riscv64-linux-gnu- fs.img >>/tmp/kbuild.log 2>&1",
                "( " + runCmd + " ) | timeout " + (wait + 40) + " make TOOLPREFIX=riscv64-linux-gnu- qemu > /tmp/krun.log 2>&1 || true",
                "if grep -q '" + marker + "' /tmp/krun.log; then echo RESULT=PASS; else echo RESULT=FAIL; fi",
                "echo '---LOG---'",
                "tail -25 /tmp/krun.log");

        long start = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
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
            process.waitFor(wait + 120L, TimeUnit.SECONDS);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "执行失败：" + e.getMessage());
            return result;
        }
        String output = String.join("\n", lines);
        boolean pass = output.contains("RESULT=PASS");
        result.put("ok", pass);
        result.put("test", test);
        result.put("seconds", Math.round((System.currentTimeMillis() - start) / 100.0) / 10.0);
        result.put("stage", output.contains("BUILD_FAILED") ? "BUILD_ERROR"
                : output.contains("PATCH_FAILED") ? "PATCH_ERROR"
                : pass ? "PASS" : "FAIL");
        result.put("log", output.length() > 4000 ? output.substring(output.length() - 4000) : output);
        return result;
    }

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
