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
 * xv6 真实内核实验：补丁提交 → 构建 → QEMU 运行 → 按任务标记判分。
 * 只接受白名单任务与测试，拒绝改动 Makefile 或越界路径的补丁。
 */
@RestController
@RequestMapping("/kernel")
public class KernelLabController {

    private static final String STARTER = "/mnt/g/ZhiCore/kernel-lab/xv6-lab/xv6-riscv";
    private static final String WORK = "/mnt/g/ZhiCore/kernel-lab/web-run";

    /** marker, 运行命令, 等待秒数, 预置脚本 */
    private static final Map<String, String[]> TASKS = new LinkedHashMap<>();

    static {
        TASKS.put("banner", new String[]{"ZhiCore", "sleep 6", "20", ""});
        TASKS.put("cpus", new String[]{"CPUS=", "sleep 6", "20", ""});
        TASKS.put("hello", new String[]{"HELLO=42", "printf 'hello\\n'; sleep 6", "30",
                "cat > user/hello.c <<'EOF'\n"
                        + "#include \"kernel/types.h\"\n"
                        + "#include \"user/user.h\"\n"
                        + "int main(){ printf(\"HELLO=%d\\n\", hello()); exit(0); }\n"
                        + "EOF\n"
                        + "sed -i 's|^UPROGS=\\\\|UPROGS=\\\\\n\t$U/_hello\\\\|' Makefile"});
    }

    private static final Map<String, String[]> TESTS = Map.of(
            "boot", new String[]{"echo kernel_ok", "kernel_ok", "15"},
            "copyin", new String[]{"usertests copyin", "OK", "90"},
            "usertests", new String[]{"usertests -q", "ALL TESTS PASSED", "260"}
    );

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        String patch = body == null || body.get("patch") == null ? "" : String.valueOf(body.get("patch"));
        String task = body == null || body.get("task") == null ? "banner" : String.valueOf(body.get("task"));
        Map<String, Object> result = new LinkedHashMap<>();
        String[] spec = TASKS.get(task);
        if (spec == null) {
            result.put("ok", false);
            result.put("error", "未知任务：" + task);
            return result;
        }
        if (patch.contains("../../") || patch.contains("Makefile")) {
            result.put("ok", false);
            result.put("error", "提交内容不允许修改 Makefile 或越界路径");
            return result;
        }
        if (!patch.isEmpty() && !patch.endsWith("\n")) {
            patch = patch + "\n";
        }
        String marker = spec[0];
        String runCmd = spec[1];
        int wait = Integer.parseInt(spec[2]);
        String setup = spec[3];
        String patchB64 = java.util.Base64.getEncoder()
                .encodeToString(patch.getBytes(StandardCharsets.UTF_8));

        List<String> script = new ArrayList<>();
        script.add("set -e");
        script.add("rm -rf " + WORK);
        script.add("cp -r " + STARTER + " " + WORK);
        script.add("cd " + WORK);
        if (!setup.isEmpty()) {
            script.add(setup);
        }
        script.add("echo '" + patchB64 + "' | base64 -d > /tmp/sub.patch");
        script.add("if [ -s /tmp/sub.patch ]; then patch -p1 --batch --forward < /tmp/sub.patch || { echo PATCH_FAILED; exit 5; }; fi");
        script.add("make TOOLPREFIX=riscv64-linux-gnu- -j2 >/tmp/kbuild.log 2>&1 || { echo BUILD_FAILED; tail -20 /tmp/kbuild.log; exit 3; }");
        script.add("make TOOLPREFIX=riscv64-linux-gnu- fs.img >>/tmp/kbuild.log 2>&1 || { echo FS_FAILED; exit 4; }");
        script.add("( " + runCmd + " ) | timeout " + (wait + 40) + " make TOOLPREFIX=riscv64-linux-gnu- qemu > /tmp/krun.log 2>&1 || true");
        script.add("if grep -q '" + marker + "' /tmp/krun.log; then echo RESULT=PASS; else echo RESULT=FAIL; fi");
        script.add("echo '---LOG---'");
        script.add("tail -25 /tmp/krun.log");

        long start = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", "Ubuntu-24.04", "-u", "root", "bash", "-lc",
                    String.join("\n", script));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            process.waitFor(wait + 150L, TimeUnit.SECONDS);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "执行失败：" + e.getMessage());
            return result;
        }
        String output = String.join("\n", lines);
        boolean pass = output.contains("RESULT=PASS");
        result.put("ok", pass);
        result.put("task", task);
        result.put("seconds", Math.round((System.currentTimeMillis() - start) / 100.0) / 10.0);
        result.put("stage", output.contains("PATCH_FAILED") ? "PATCH_ERROR"
                : output.contains("BUILD_FAILED") ? "BUILD_ERROR"
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
