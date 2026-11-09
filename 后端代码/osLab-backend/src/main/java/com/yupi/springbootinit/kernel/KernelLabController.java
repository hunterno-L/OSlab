package com.yupi.springbootinit.kernel;

import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * xv6 内核实验：整文件编辑 → 覆盖源码 → 构建 → QEMU 运行 → 按标记判分。
 */
@RestController
@RequestMapping("/kernel")
public class KernelLabController {

    private static final String ST_ROOT = "G:\\ZhiCore\\kernel-lab";
    private static final String STARTER = "/mnt/g/ZhiCore/kernel-lab/xv6-lab/xv6-riscv";
    private static final String WORK = "/mnt/g/ZhiCore/kernel-lab/web-run";

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private UserService userService;

    /** 任务：判分标记、运行命令、等待秒数、脚手架脚本、学生编辑的文件 */
    private static final Map<String, String[]> TASKS = new LinkedHashMap<>();

    static {
        TASKS.put("banner", new String[]{"ZhiCore", "sleep 6", "20", "", "kernel/main.c"});
        TASKS.put("cpus", new String[]{"CPUS=", "sleep 6", "20", "", "kernel/main.c"});
        TASKS.put("hello", new String[]{"HELLO=42", "sleep 4; printf 'echo\\n'; sleep 8", "30",
                "cp -r /mnt/g/ZhiCore/kernel-lab/fixtures/hello/. .", "kernel/sysproc.c"});
    }

    @GetMapping("/task/{task}")
    public Map<String, Object> template(@PathVariable String task) {
        String[] spec = TASKS.get(task);
        Map<String, Object> result = new LinkedHashMap<>();
        if (spec == null) {
            result.put("error", "未知任务");
            return result;
        }
        String rel = spec[4];
        String content;
        try {
            content = new String(Files.readAllBytes(Paths.get(ST_ROOT, "xv6-lab", "xv6-riscv", rel.replace("/", "\\"))),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            content = "// 读取模板失败：" + e.getMessage();
        }
        if ("hello".equals(task) && !content.contains("sys_hello")) {
            content = content + "\n/* ===== 在此实现 hello 系统调用 =====\n"
                    + " * 要求：函数名 sys_hello，返回 uint64，值为 42。\n"
                    + " * 提示：照抄上面的 sys_uptime 结构，返回值改成 42。\n"
                    + " */\n"
                    + "uint64\nsys_hello(void)\n{\n  return 0; // TODO: 改成 42\n}\n";
        }
        result.put("task", task);
        result.put("path", rel);
        result.put("content", content);
        return result;
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String task = body == null || body.get("task") == null ? "banner" : String.valueOf(body.get("task"));
        String content = body == null || body.get("content") == null ? "" : String.valueOf(body.get("content"));
        Map<String, Object> result = new LinkedHashMap<>();
        String[] spec = TASKS.get(task);
        if (spec == null) {
            result.put("ok", false);
            result.put("error", "未知任务：" + task);
            return result;
        }
        if (content.contains("Makefile") || content.contains("../../")) {
            result.put("ok", false);
            result.put("error", "不允许修改 Makefile 或越界路径");
            return result;
        }

        String marker = spec[0];
        String runCmd = spec[1];
        int wait = Integer.parseInt(spec[2]);
        String setup = spec[3];
        String relPath = spec[4];

        Path tmp = Paths.get(ST_ROOT, "web-upload", task + ".txt");
        try {
            Files.createDirectories(tmp.getParent());
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "写入失败：" + e.getMessage());
            return result;
        }
        String upload = "/mnt/g/ZhiCore/kernel-lab/web-upload/" + task + ".txt";

        List<String> script = new ArrayList<>();
        script.add("set -e");
        script.add("rm -rf " + WORK);
        script.add("cp -r " + STARTER + " " + WORK);
        script.add("cd " + WORK);
        if (!setup.isEmpty()) {
            script.add(setup);
        }
        script.add("cp '" + upload + "' '" + relPath + "'");
        script.add("make TOOLPREFIX=riscv64-linux-gnu- -j2 >/tmp/kbuild.log 2>&1 || { echo BUILD_FAILED; tail -25 /tmp/kbuild.log; exit 3; }");
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
        result.put("stage", output.contains("BUILD_FAILED") ? "BUILD_ERROR"
                : pass ? "PASS" : "FAIL");
        result.put("log", output.length() > 4000 ? output.substring(output.length() - 4000) : output);

        long userId = 0L;
        try {
            User user = userService.getLoginUser(request);
            userId = user == null ? 0L : user.getId();
        } catch (Exception ignored) {
            // 未登录不记录
        }
        if (userId > 0) {
            try {
                jdbc.update("INSERT INTO kernel_submission (id,userId,task,stage,passed,seconds,patchHash,patchText,logTail) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)",
                        System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000),
                        userId, task, (String) result.get("stage"), pass ? 1 : 0,
                        new java.math.BigDecimal(String.valueOf(result.get("seconds"))),
                        Integer.toHexString(content.hashCode()), content,
                        String.valueOf(result.get("log")));
            } catch (Exception ignored) {
                // 记录失败不影响判分
            }
        }
        return result;
    }

    @GetMapping("/submissions")
    public List<Map<String, Object>> submissions(@RequestParam(value = "task", required = false) String task,
                                                 HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (task != null && !task.isEmpty()) {
            return jdbc.queryForList("SELECT id,task,stage,passed,seconds,createTime FROM kernel_submission "
                    + "WHERE userId=? AND task=? ORDER BY id DESC LIMIT 20", user.getId(), task);
        }
        return jdbc.queryForList("SELECT id,task,stage,passed,seconds,createTime FROM kernel_submission "
                + "WHERE userId=? ORDER BY id DESC LIMIT 20", user.getId());
    }
}
