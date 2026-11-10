package com.yupi.springbootinit.kernel;

import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * xv6 内核实验：整文件编辑 → 覆盖源码 → 构建 → QEMU 运行 → 按标记判分。
 * 任务按课程章节组织，模板带中文注释。
 */
@RestController
@RequestMapping("/kernel")
public class KernelLabController {

    /* ============ 路径解析：可配置 + 自动探测（原来四处硬编码） ============ */

    /** 可在 application.yml 覆盖：kernellab.root */
    @Value("${kernellab.root:}")
    private String configuredRoot;

    /** 可在 application.yml 覆盖：kernellab.distro（留空则自动探测非 docker-desktop 的发行版） */
    @Value("${kernellab.distro:}")
    private String configuredDistro;

    /** Windows 侧根目录，如 G:\ZhiCore\kernel-lab */
    private static Path ST_ROOT;
    /** WSL 侧根目录，由 ST_ROOT 换算而来，如 /mnt/g/ZhiCore/kernel-lab */
    private static String WSL_ROOT;
    /** WSL 侧 xv6 源码目录 */
    private static String STARTER;
    /** WSL 侧构建工作目录 */
    private static String WORK;
    /** WSL 侧上传落盘目录 */
    private static String UPLOAD_DIR;
    /** WSL 发行版名；默认发行版可能是 docker-desktop，所以必须显式指定 */
    private static String DISTRO = "Ubuntu-24.04";

    @PostConstruct
    public void initKernelPaths() {
        List<String> candidates = new ArrayList<>();
        if (configuredRoot != null && !configuredRoot.trim().isEmpty()) {
            candidates.add(configuredRoot.trim());
        }
        String envRoot = System.getenv("KERNEL_LAB_ROOT");
        if (envRoot != null && !envRoot.trim().isEmpty()) {
            candidates.add(envRoot.trim());
        }
        candidates.add("G:\\ZhiCore\\kernel-lab");
        candidates.add(Paths.get(System.getProperty("user.dir", "."), "kernel-lab").toString());
        candidates.add(Paths.get(System.getProperty("user.dir", "."), "..", "kernel-lab").toString());

        Path picked = null;
        for (String c : candidates) {
            try {
                Path p = Paths.get(c).toAbsolutePath().normalize();
                Path xv6 = p.resolve("xv6-lab").resolve("xv6-riscv");
                if (Files.isDirectory(xv6) && Files.isRegularFile(xv6.resolve("Makefile"))) {
                    picked = p;
                    break;
                }
            } catch (Exception ignored) {
                // 候选路径非法就跳过
            }
        }
        if (picked == null) {
            picked = Paths.get(candidates.get(0)).toAbsolutePath().normalize();
            System.out.println("[kernel-lab] 警告：未找到含 xv6-lab/xv6-riscv/Makefile 的根目录，先按候选值 " + picked + " 启动");
        }
        ST_ROOT = picked;

        if (configuredDistro != null && !configuredDistro.trim().isEmpty()) {
            DISTRO = configuredDistro.trim();
        } else {
            String detected = detectDistro();
            if (detected != null) {
                DISTRO = detected;
            }
        }

        WSL_ROOT = toWslPath(picked);
        STARTER = WSL_ROOT + "/xv6-lab/xv6-riscv";
        WORK = WSL_ROOT + "/web-run";
        UPLOAD_DIR = WSL_ROOT + "/web-upload";

        System.out.println("[kernel-lab] root=" + ST_ROOT + " | wslRoot=" + WSL_ROOT + " | distro=" + DISTRO);
    }

    /** Windows 路径 → WSL 路径。先用 wslpath 换算，失败再按盘符手工换算。 */
    private static String toWslPath(Path winPath) {
        String raw = winPath.toString();
        try {
            Process p = new ProcessBuilder("wsl", "-d", DISTRO, "-u", "root", "bash", "-lc",
                    "wslpath -u " + shellQuote(raw)).redirectErrorStream(true).start();
            String out = readAll(p, StandardCharsets.UTF_8).trim();
            p.waitFor(15, TimeUnit.SECONDS);
            for (String line : out.split("\\r?\\n")) {
                if (line.trim().startsWith("/")) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
            // 没有 wsl 或调用失败，走手工换算
        }
        String s = raw.replace('\\', '/');
        if (s.length() >= 2 && s.charAt(1) == ':') {
            return "/mnt/" + Character.toLowerCase(s.charAt(0)) + s.substring(2);
        }
        return s;
    }

    /** 从 wsl -l -q 里挑第一个非 docker-desktop 的发行版。wsl.exe 输出是 UTF-16LE 且带 NUL。 */
    private static String detectDistro() {
        byte[] raw;
        try {
            Process p = new ProcessBuilder("wsl", "-l", "-q").redirectErrorStream(true).start();
            raw = readAllBytes(p);
            p.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
        for (Charset cs : new Charset[]{StandardCharsets.UTF_16LE, StandardCharsets.UTF_8}) {
            String text = new String(raw, cs);
            for (String line : text.split("\\r?\\n")) {
                String name = line.replace("\u0000", "").replace("\uFEFF", "").trim();
                if (name.isEmpty() || name.toLowerCase().startsWith("docker-desktop")) {
                    continue;
                }
                if (name.matches("[A-Za-z0-9._\\-]+")) {
                    return name;
                }
            }
        }
        return null;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String readAll(Process p, Charset cs) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static byte[] readAllBytes(Process p) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = p.getInputStream().read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    @Resource
    private JdbcTemplate jdbc;

    @Resource
    private UserService userService;

    private static class TaskSpec {
        String code;
        String title;
        String chapter;
        String file;
        String marker;
        String run;
        int wait;
        String setup;
        String summary;
        List<String> points = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        String guide;
    }

    private static final Map<String, TaskSpec> TASKS = new LinkedHashMap<>();

    private static TaskSpec task(String code, String title, String chapter, String file,
                                 String marker, String run, int wait, String setup,
                                 String summary, String guide, String[] points, String[] steps) {
        TaskSpec t = new TaskSpec();
        t.code = code;
        t.title = title;
        t.chapter = chapter;
        t.file = file;
        t.marker = marker;
        t.run = run;
        t.wait = wait;
        t.setup = setup;
        t.summary = summary;
        t.guide = guide;
        for (String p : points) {
            t.points.add(p);
        }
        for (String s : steps) {
            t.steps.add(s);
        }
        return t;
    }

    static {
        TASKS.put("banner", task("banner", "实验 1.1 内核启动横幅", "第一章 启动与硬件 · 系统启动",
                "kernel/main.c", "ZhiCore", "sleep 6", 20, "",
                "xv6 启动后由 main() 初始化控制台、内存、进程和文件系统。本实验在内核启动路径上增加一行输出，确认你改的代码真的在硬件模拟器里跑起来了。",
                "在 main() 中找到 printk(\"xv6 kernel is booting\\n\")，在它后面加一行 printk(\"ZhiCore\\n\")。注意保留换行符。",
                new String[]{"内核启动流程：consoleinit → kinit → procinit → 用户态 init",
                        "printk 直接写串口，不依赖文件系统",
                        "QEMU 串口输出就是判分依据"},
                new String[]{"定位 main() 中的启动打印", "增加一行 printk 输出 ZhiCore", "提交后观察评测结果与内核日志"}));

        TASKS.put("cpus", task("cpus", "实验 1.2 多核启动", "第一章 启动与硬件 · 多核与并发",
                "kernel/main.c", "CPUS=", "sleep 6", 20, "",
                "xv6 启动时每个 hart 都会进入 main()，0 号核负责初始化，其余核等待。本实验打印核数，理解多核启动顺序。",
                "在 procinit() 之后加一行 printk(\"CPUS=%d\\n\", 3)，输出 QEMU 配置的核数。",
                new String[]{"hart 与 CPU 核的对应关系", "started 变量如何同步多核", "启动日志中的 hart 1/2 starting"},
                new String[]{"找到 procinit() 调用位置", "在其后打印 CPUS= 与核数", "提交并核对日志顺序"}));

        TASKS.put("memtop", task("memtop", "实验 1.3 物理内存上界", "第一章 启动与硬件 · 内存布局",
                "kernel/main.c", "MEM=", "sleep 6", 20, "",
                "xv6 用 PHYSTOP 标记物理内存上界，内核之后的内存由页分配器接管。本实验打印该上界，建立内核地址空间的范围感。",
                "在 kvminit() 之后加一行 printk(\"MEM=%d\\n\", PHYSTOP)，PHYSTOP 定义在 memlayout.h。",
                new String[]{"内核地址空间布局", "PHYSTOP 与 KERNBASE", "内核如何限制可用内存"},
                new String[]{"确认 memlayout.h 已包含", "在 kvminit 后打印 PHYSTOP", "提交并核对输出"}));

        TASKS.put("pages", task("pages", "实验 2.1 物理内存页数", "第二章 内存管理 · 分页",
                "kernel/main.c", "PAGES=", "sleep 6", 20, "",
                "xv6 把物理内存按 4KB 分页管理，PHYSTOP 是物理内存上界。本实验打印总页数，建立“内存被切成页”的直观认识。",
                "在 kinit() 之后加一行 printk(\"PAGES=%d\\n\", PHYSTOP / PGSIZE)，PHYSTOP 与 PGSIZE 在 memlayout.h 中定义。",
                new String[]{"PHYSTOP 与 PGSIZE 的含义", "kinit 如何把空闲页串成链表", "页号与物理地址的换算"},
                new String[]{"确认 memlayout.h 已包含", "在 kinit 后打印页数", "提交并检查 PAGES= 数值"}));

        TASKS.put("procs", task("procs", "实验 3.1 进程表容量", "第三章 进程管理 · 进程控制块",
                "kernel/main.c", "PROCS=", "sleep 6", 20, "",
                "xv6 用固定大小的进程表管理进程，NPROC 决定同时存在的进程上限。本实验打印该上限，理解进程控制块的静态分配。",
                "在 procinit() 之后加一行 printk(\"PROCS=%d\\n\", NPROC)，NPROC 在 param.h 中定义。",
                new String[]{"proc 结构与进程状态", "NPROC 静态数组的取舍", "进程表初始化 procinit"},
                new String[]{"确认 param.h 已包含", "在 procinit 后打印 NPROC", "提交并核对输出"}));

        TASKS.put("nfile", task("nfile", "实验 4.1 打开文件上限", "第四章 文件系统 · 文件表",
                "kernel/main.c", "NFILE=", "sleep 6", 20, "",
                "xv6 用全局文件表管理打开的文件，NFILE 是上限。本实验打印该上限，理解“文件描述符”背后的表结构。",
                "在 fileinit() 之后加一行 printk(\"NFILE=%d\\n\", NFILE)，NFILE 在 param.h 中定义。",
                new String[]{"file 结构与引用计数", "每进程 fd 表与全局文件表的关系", "fileinit 初始化时机"},
                new String[]{"确认 param.h 已包含", "在 fileinit 后打印 NFILE", "提交并核对输出"}));

        TASKS.put("ninode", task("ninode", "实验 4.2 inode 表容量", "第四章 文件系统 · inode",
                "kernel/main.c", "NINODE=", "sleep 6", 20, "",
                "inode 表缓存最近使用的磁盘 inode，NINODE 决定缓存大小。本实验打印该值，理解文件系统元数据的内存缓存。",
                "在 iinit() 之后加一行 printk(\"NINODE=%d\\n\", NINODE)，NINODE 在 param.h 中定义。",
                new String[]{"inode 与 dinode 的区别", "inode 缓存与磁盘读取", "iinit 的作用"},
                new String[]{"确认 param.h 已包含", "在 iinit 后打印 NINODE", "提交并核对输出"}));

        TASKS.put("hello", task("hello", "实验 5.1 hello 系统调用", "第五章 系统调用 · 用户态与内核态",
                "kernel/sysproc.c", "HELLO=42", "sleep 4; printf 'echo\\n'; sleep 8", 30,
                "cp -r %WSL_ROOT%/fixtures/hello/. .",
                "系统调用是用户态进入内核的唯一入口。本实验实现 sys_hello() 返回 42，平台已预置系统调用号、分发表、用户态桩和测试程序，你只需要补全内核侧实现。",
                "在 sysproc.c 末尾把 sys_hello 的返回值从 0 改成 42。判分会运行用户程序 echo，检查输出 HELLO=42。",
                new String[]{"系统调用号与 syscall.h", "syscall() 如何按编号分发", "usys.pl 生成用户态桩", "返回值如何从内核传回用户态"},
                new String[]{"阅读 sys_hello 骨架与上方 sys_uptime 写法", "把返回值改为 42", "提交后查看 echo 的输出"}));
    }

    @GetMapping("/stats")
    public List<Map<String, Object>> stats(HttpServletRequest request) {
        long userId = 0L;
        try {
            User user = userService.getLoginUser(request);
            userId = user == null ? 0L : user.getId();
        } catch (Exception ignored) {
            // 未登录返回空统计
        }
        List<Map<String, Object>> rows = userId == 0
                ? new ArrayList<>()
                : jdbc.queryForList("SELECT task, COUNT(*) total, SUM(passed) passed, MAX(createTime) lastTime "
                        + "FROM kernel_submission WHERE userId=? GROUP BY task", userId);
        Map<String, Map<String, Object>> byTask = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byTask.put(String.valueOf(row.get("task")), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskSpec spec : TASKS.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", spec.code);
            item.put("title", spec.title);
            item.put("chapter", spec.chapter);
            item.put("file", spec.file);
            Map<String, Object> stat = byTask.get(spec.code);
            int total = stat == null ? 0 : ((Number) stat.get("total")).intValue();
            int passed = stat == null || stat.get("passed") == null ? 0 : ((Number) stat.get("passed")).intValue();
            item.put("attempts", total);
            item.put("passed", passed);
            item.put("rate", total == 0 ? null : Math.round(passed * 100.0 / total));
            item.put("lastTime", stat == null ? null : stat.get("lastTime"));
            result.add(item);
        }
        return result;
    }

    private static final Map<String, String> COMMENTS = new LinkedHashMap<>();

    static {
        COMMENTS.put("// start() jumps here in supervisor mode on all CPUs.",
                "// 所有 CPU 上，start() 会以 Supervisor 模式跳转到这里");
        COMMENTS.put("// physical page allocator", "// 物理页分配器");
        COMMENTS.put("// create kernel page table", "// 创建内核页表");
        COMMENTS.put("// turn on paging", "// 开启分页");
        COMMENTS.put("// process table", "// 进程表");
        COMMENTS.put("// trap vectors", "// 陷入向量");
        COMMENTS.put("// install kernel trap vector", "// 安装内核陷入向量");
        COMMENTS.put("// set up interrupt controller", "// 初始化中断控制器");
        COMMENTS.put("// ask PLIC for device interrupts", "// 向 PLIC 申请设备中断");
        COMMENTS.put("// buffer cache", "// 缓冲区缓存");
        COMMENTS.put("// inode table", "// inode 表");
        COMMENTS.put("// file table", "// 文件表");
        COMMENTS.put("// emulated hard disk", "// 模拟硬盘");
        COMMENTS.put("// first user process", "// 第一个用户进程");
        COMMENTS.put("// return how many clock tick interrupts have occurred\n// since start.",
                "// 返回自启动以来发生的时钟中断次数");
        COMMENTS.put("// since start.", "// 自启动以来");
    }

    private static String translate(String content) {
        String result = content;
        for (Map.Entry<String, String> entry : COMMENTS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskSpec spec : TASKS.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", spec.code);
            item.put("title", spec.title);
            item.put("chapter", spec.chapter);
            item.put("file", spec.file);
            result.add(item);
        }
        return result;
    }

    @GetMapping("/task/{task}")
    public Map<String, Object> template(@PathVariable String task) {
        TaskSpec spec = TASKS.get(task);
        Map<String, Object> result = new LinkedHashMap<>();
        if (spec == null) {
            result.put("error", "未知任务");
            return result;
        }
        String content;
        try {
            content = new String(Files.readAllBytes(
                    ST_ROOT.resolve("xv6-lab").resolve("xv6-riscv").resolve(spec.file.replace("/", "\\"))),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            content = "// 读取模板失败：" + e.getMessage();
        }
        content = translate(content);
        String header = "/* ===== " + spec.title + " =====\n"
                + " * 章节：" + spec.chapter + "\n"
                + " * 任务：" + spec.summary.replace("*/", "") + "\n"
                + " * 提示：" + spec.guide.replace("*/", "") + "\n"
                + " */\n";
        if (!content.startsWith("/* ===== ")) {
            content = header + content;
        }
        if ("hello".equals(task) && !content.contains("sys_hello")) {
            content = content + "\n/* ===== 在这里实现 hello 系统调用 =====\n"
                    + " * 要求：函数名 sys_hello，返回 uint64，值为 42。\n"
                    + " * 参考上方 sys_uptime 的结构，只改返回值。\n"
                    + " */\n"
                    + "uint64\nsys_hello(void)\n{\n  return 0; // TODO: 把 0 改成 42\n}\n";
        }
        result.put("code", spec.code);
        result.put("title", spec.title);
        result.put("chapter", spec.chapter);
        result.put("path", spec.file);
        result.put("marker", spec.marker);
        result.put("summary", spec.summary);
        result.put("guide", spec.guide);
        result.put("points", spec.points);
        result.put("steps", spec.steps);
        result.put("content", content);
        return result;
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String task = body == null || body.get("task") == null ? "banner" : String.valueOf(body.get("task"));
        String content = body == null || body.get("content") == null ? "" : String.valueOf(body.get("content"));
        Map<String, Object> result = new LinkedHashMap<>();
        TaskSpec spec = TASKS.get(task);
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

        Path tmp = ST_ROOT.resolve("web-upload").resolve(task + ".txt");
        try {
            Files.createDirectories(tmp.getParent());
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", "写入失败：" + e.getMessage());
            return result;
        }
        String upload = UPLOAD_DIR + "/" + task + ".txt";

        List<String> script = new ArrayList<>();
        script.add("set -e");
        script.add("rm -rf " + WORK);
        script.add("cp -r " + STARTER + " " + WORK);
        script.add("cd " + WORK);
        if (!spec.setup.isEmpty()) {
            script.add(spec.setup.replace("%WSL_ROOT%", WSL_ROOT));
        }
        script.add("cp '" + upload + "' '" + spec.file + "'");
        script.add("make TOOLPREFIX=riscv64-linux-gnu- -j2 >/tmp/kbuild.log 2>&1 || { echo BUILD_FAILED; tail -25 /tmp/kbuild.log; exit 3; }");
        script.add("make TOOLPREFIX=riscv64-linux-gnu- fs.img >>/tmp/kbuild.log 2>&1 || { echo FS_FAILED; exit 4; }");
        script.add("( " + spec.run + " ) | timeout " + (spec.wait + 40)
                + " make TOOLPREFIX=riscv64-linux-gnu- qemu > /tmp/krun.log 2>&1 || true");
        script.add("if grep -q '" + spec.marker + "' /tmp/krun.log; then echo RESULT=PASS; else echo RESULT=FAIL; fi");
        script.add("echo '---LOG---'");
        script.add("tail -25 /tmp/krun.log");

        long start = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "-d", DISTRO, "-u", "root", "bash", "-lc",
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
            process.waitFor(spec.wait + 150L, TimeUnit.SECONDS);
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
        result.put("stage", output.contains("BUILD_FAILED") ? "BUILD_ERROR" : pass ? "PASS" : "FAIL");
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
        User user;
        try {
            user = userService.getLoginUser(request);
        } catch (Exception e) {
            return new ArrayList<>();
        }
        if (task != null && !task.isEmpty()) {
            return jdbc.queryForList("SELECT id,task,stage,passed,seconds,createTime FROM kernel_submission "
                    + "WHERE userId=? AND task=? ORDER BY id DESC LIMIT 20", user.getId(), task);
        }
        return jdbc.queryForList("SELECT id,task,stage,passed,seconds,createTime FROM kernel_submission "
                + "WHERE userId=? ORDER BY id DESC LIMIT 20", user.getId());
    }
}
