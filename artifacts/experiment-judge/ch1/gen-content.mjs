#!/usr/bin/env node
/**
 * 基础组 16 篇 content.md 的生成器。
 * （第一章 5 + 第二章 8 + 3.12 / 3.13 / 4.1）
 *
 * 唯一内容来源：artifacts/experiment-judge/current-content.tsv
 *   （mysql -N -B "SELECT id,title,content FROM question WHERE title LIKE '实验%' AND isDelete=0"）
 * 保留「任务描述 / 知识要点 / 实验任务（含实验步骤）」原文，只补充说明。
 * 判题契约由脚本从各实验 cases.json 的 description **逐字嵌入**，杜绝手抄走样。
 *
 * 用法：node artifacts/experiment-judge/ch1/gen-content.mjs
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ART = join(HERE, "..");
const REPO = join(ART, "..", "..");
const EXP_ROOT = join(REPO, "content", "operating-systems", "wangdao-2025", "experiments");
const CH = { 1: "A-第一章-实验判题-001", 2: "A-第二章-实验判题-001", 3: "A-第三章-实验判题-001", 4: "A-第四章-实验判题-001" };

/* ---------- MySQL -B 反转义 ---------- */
function unescape(s) {
  let out = "";
  for (let i = 0; i < s.length; i++) {
    if (s[i] !== "\\") { out += s[i]; continue; }
    const c = s[++i];
    if (c === "n") out += "\n";
    else if (c === "t") out += "\t";
    else if (c === "r") out += "\r";
    else if (c === "0") out += "\0";
    else if (c === "Z") out += "\x1a";
    else if (c === "b") out += "\b";
    else if (c === "\\") out += "\\";
    else if (c === "%") out += "\\%";
    else if (c === "_") out += "\\_";
    else if (c === undefined) out += "\\";
    else out += c;
  }
  return out;
}

const ORIG = new Map();
const TITLE = new Map();
for (const line of readFileSync(join(ART, "current-content.tsv"), "utf8").split(/\r?\n/)) {
  if (!line) continue;
  const p = line.split("\t");
  if (p.length < 3) continue;
  ORIG.set(p[0], unescape(p.slice(2).join("\t")));
  TITLE.set(p[0], p[1]);
}

/** 把原文按 ### 小节切开，任务描述→背景，知识要点→知识要点，其余→实验任务 */
function splitOriginal(text) {
  const parts = text.split(/^###\s*/m).filter((s) => s.trim());
  const bg = [], kp = [], task = [];
  for (const p of parts) {
    const nl = p.indexOf("\n");
    const head = (nl < 0 ? p : p.slice(0, nl)).replace(/[：:]\s*$/, "").trim();
    const body = (nl < 0 ? "" : p.slice(nl + 1)).trim();
    if (/^任务描述/.test(head)) bg.push(body);
    else if (/^知识要点/.test(head)) kp.push(body);
    else task.push(`**${head}**\n\n${body}`);
  }
  return { bg: bg.join("\n\n"), kp: kp.join("\n\n"), task: task.join("\n\n") };
}

/* ================= 基础组补充内容 ================= */
const D = [
  /* ---------------- 1.1 ---------------- */
  {
    id: "1917131319528452098", ch: 1, no: "1.1", tier: "C",
    bg: "内核不是 `gcc *.c` 就能出来的程序：各模块先编译成 `.o`，再由链接器按 `tools/scse0_3.lds` 拼成 `gzemul/vmlinuz`。谁保证「被依赖者先编译、链接在所有 `.o` 就绪之后」？答案是 make 的依赖图。工具链前缀配错，代码再对也编不出 MIPS 镜像。",
    kp: "**扩写（make 公开语义）**：make 用时间戳判定「要不要重建」，用依赖图的**拓扑序**判定「按什么顺序重建」。`modules` 是递归 make 的子目录列表，`objects` 是链接进内核的 `.o` 集合，二者不能混。有环时 make 报 `circular dependency` 并停止，对应子任务的 `CYCLE` 分支。`CC=$(CROSS_COMPILE)gcc`、`LD=$(CROSS_COMPILE)ld`。",
    src: [
      ["Makefile（顶层）", "定义目标与依赖", "modules、objects、vmlinuz"],
      ["include.mk", "交叉工具链与编译选项", "CROSS_COMPILE、CC、CFLAGS、LD"],
      ["tools/scse0_3.lds", "链接脚本，决定段加载地址", "SECTIONS、. = 0x80000000、_end"],
      ["gzemul/vmlinuz", "构建产物", "ELF header e_machine = MIPS"],
    ],
    srcNote: "**内容来源缺口**：顶层 Makefile 与 `include.mk` 的**原指导 Listing 未随工作区提供**（`originalCode` 实测 43 字节、`answer` 48 字节，均无效）。子任务依赖图是**按 make 公开语义构造的等价示例**，非北航原 Makefile 转录。",
    task: "**每步验证**：步骤 1–2 能口述 vmlinuz 依赖谁；步骤 3 改好 `include.mk`，`which ${CROSS_COMPILE}gcc` 验证；步骤 4 `make -n` 看命令、`make -p` 看依赖图；步骤 5 `readelf -h` 确认 Machine=MIPS。",
    judge: {
      mode: "**Tier C**：概念子任务自动判分。**平台自动判分的是 make 依赖图的合法重建顺序（拓扑序）；AC 不等于内核构建成功。** 真实构建见「本地完整验证」。",
      fill: "`int build_order(int n, int dep[MAXT][MAXT], int order[MAXT], int *built)`（`starter.c` 填写区）。`dep[i][j]=1` 即 i 依赖 j；顺序写进 `order[0..*built-1]`，无环返回 0，有环返回负值并把**已构建的部分进度**写进 `*built`。",
      input: "首行目标数 n（1..64），随后 n 行「目标名 依赖个数k 依赖下标...」（目标名 ≤15 字符，0<=k<=n，下标 0..n-1）。",
      output: "已重建目标名空格分隔，后接 `OK`；有环接 `CYCLE`。同时就绪时**取编号最小者**，非名字字典序。",
      host: "无 MIPS 依赖（`MAXT=64`）。驱动 `order[MAXT] = {0}`，把 `*built` 钳到 `[0,MAXT]`、校验 `order[i] ∈ [0,n)`，越界打印 `?`，使错误实现判答案错误而非 Runtime Error。",
      real: "判的量就是 `make -p` 的依赖图拓扑序；工具链、链接脚本、镜像与 gxemul 启动仍须本地完成。",
      source: "原指导 Listing **未随工作区提供**，依赖图为按 make 公开语义构造的**等价示例**。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① `which mips-linux-gnu-gcc` 确认工具链；② 改 `include.mk` 的 `CROSS_COMPILE` 与 `LD`；③ `make clean && make`；④ `readelf -h` 确认 Machine=MIPS；⑤ `gxemul -E oldtestmips -d gzemul/vmlinuz` 看串口输出。",
    errs: [
      "按输入顺序输出、不看依赖（`wrong.c`）：`mos-module-chain` 输出 `vmlinuz kernel.o lib.o modules objects`，链接早于 `.o` 就绪。",
      "有环仍返回成功：`three-cycle` 须输出 `CYCLE`，`self-loop-with-buildable-prefix` 须输出 `objects CYCLE`——裸 `CYCLE` 丢掉部分进度。",
      "就绪集合按名字字典序挑：`all-independent` 应答 `all boot mm lib`（下标序），按名字排得 `all boot lib mm`。",
      "用 DFS 后序冒充拓扑序：`kahn-min-index-vs-dfs-postorder` 上 Kahn 得 `c d b all`、DFS 得 `d b c all`。",
    ],
  },
  /* ---------------- 1.2 ---------------- */
  {
    id: "1930153941795131394", ch: 1, no: "1.2", tier: "A",
    bg: "实验 1.1 产出的 `vmlinuz` 是一个 ELF 文件。内核为什么能被加载到 `0x80000000` 一带、`.bss` 为什么不占文件体积、入口地址存在哪里——答案全在 header 与两张表里。本实验要你亲手写一个迷你 `readelf`，把「链接器做了什么」变成可观测的数字。",
    kp: "**扩写（ELF32 公开规范）**：**Section** 是链接视角，**Segment** 是加载视角（`p_memsz − p_filesz` 即 `.bss`）。ELF32 header 52 字节、Section Header 40 字节（`e_shentsize` 可更大），小端。`sh_addr` 在记录内**偏移 12 字节**；第 i 条记录位于 `e_shoff + i * e_shentsize`——**乘法不能漏**。",
    src: [
      ["readelf.c", "本实验要补全的解析器", "readelf(u_char *binary, int size)"],
      ["kerelf.h", "ELF32 结构体定义", "Elf32_Ehdr、Elf32_Shdr、e_shoff、e_shentsize、e_shnum、sh_addr"],
      ["main.c", "读入镜像并调用 readelf", "binary、size"],
    ],
    srcNote: "**内容来源**：`readelf.c`/`kerelf.h` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节）。结构体按公开 ELF32 规范定义，是**等价**实现；输出格式 `\"%x:d:0x%x\\n\"` 逐字采用北航原文实验任务 3 的格式。",
    task: "**每步验证**：步骤 1 能说出 ELF 五部分的作用；步骤 2 记录 `readelf -h/-S/-l` 输出；步骤 3 补全 `readelf.c`；步骤 4 用 `diff` 比对工具输出与自己代码的输出（含地址为 0 的节）。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判真实目标函数 `readelf()`，不是代理题。",
      fill: "`static void readelf(u_char *binary, int size)`（`starter.c` 填写区）。把 `binary` 解释为 `Elf32_Ehdr*`，按 `e_shoff + i*e_shentsize` 定位每条 `Elf32_Shdr` 并打印。",
      input: "首行「e_shoff e_shentsize e_shnum」（52<=e_shoff<=4096、40<=e_shentsize<=64，均 4 字节对齐，1<=e_shnum<=16），随后 e_shnum 行 hex 字节流（每行 2*e_shentsize 个字符，小端，sh_addr 偏移 12 字节）。驱动合成 ELF32 映像后调用 `readelf(binary,size)`。",
      output: "每个 Section Header 一行「%x:d:0x%x」，即「序号:d:0x地址」：序号十六进制小写、地址 0x 前缀十六进制小写。**地址为 0 的节也必须输出。**",
      host: "32 位地址用 `uint32_t`（MinGW LLP64 / Linux LP64，`long` 宽度不同，禁用 `%ld`/`%p`）；多字节字段用 `memcpy`；映像放在 `static uint32_t img_store[]` 上保证对齐。",
      real: "合成的是合法 ELF32 映像，逻辑与解析真实 `vmlinuz` 一致；本地仍应用 `mips-linux-gnu-readelf -S` 比对。",
      source: "原指导 Listing **未随工作区提供**，结构体与合成映像为按公开规范构造的**等价**环境。",
    },
    local: "本地完整验证（选填）：`make` 出 `gzemul/vmlinuz` 后，`mips-linux-gnu-readelf -S` 记下每节 Addr，再运行自己写的 `readelf` 解析同一镜像，两份输出逐行 `diff`。真实镜像 `e_shentsize` 通常是 40，但代码不能硬编码 40。",
    errs: [
      "偏移漏乘 `e_shentsize`（`wrong.c`：`off = e_shoff + i`）：第 1 节起错位，`shnum8-nonzero-shoff256` 输出 8 行 `0x0`。",
      "记录长度硬编码成 40：`shnum16-stride48-shoff128` 用 `e_shentsize=48`，必然读错。",
      "跳过 `sh_addr == 0` 的节：`zero-addr-sections-not-skipped` 要求 4 行全输出。",
      "序号用 `%d`：`shnum16` 的第 10–15 项必须是 `a`–`f`。",
    ],
  },
  /* ---------------- 1.3 ---------------- */
  {
    id: "1930154079598989313", ch: 1, no: "1.3", tier: "C",
    bg: "实验 1.2 让你读懂了 ELF 里的地址，本实验让你**决定**这些地址。内核必须落在 MIPS 的 `kseg0`（`0x80000000` 起，不经 TLB 的固定映射区），否则页表还没建好就会异常。Linker Script 的 location counter `.` 就是那支「摆地址的笔」。",
    kp: "**扩写（ld 公开语义）**：`. = ALIGN(n)` 把当前地址向上抬到 n 的倍数；`.text : { *(.text) }` 收集同名输入段并把 `.` 前进该段大小。`.bss` 是 `NOBITS`，不占文件体积但占内存。`_end` 必须按页对齐：`page_init` 从 `_end` 之后挂空闲页，不对齐就会把内核占用的半页分出去，内核被自己覆盖。",
    src: [
      ["tools/scse0_3.lds", "本实验要补全的链接脚本", "ENTRY、SECTIONS、. = 0x80000000、ALIGN(4096)、_end"],
      ["Makefile / include.mk", "用 $(LD) -T 指定脚本", "LD、-T tools/scse0_3.lds"],
      ["mm/pmap.c", "_end 的消费者", "page_init、freemem"],
    ],
    srcNote: "**内容来源缺口**：`tools/scse0_3.lds` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节，原文只要求「阅读该文件」）。子任务采用 ld `SECTIONS` 的通用布局规则并在题面显式给出，是**按 ld 公开语义构造的等价示例**，非 MOS 私有 lds 转录。",
    task: "**每步验证**：步骤 1 能解释 ENTRY/SECTIONS/ALIGN/_end；步骤 2 补全 lds；步骤 3 `make` 出镜像；步骤 4 用 `readelf -h/-S/-l/-s` 核对：`.text` Addr = 基址、其余段按 4096 对齐递增、`.bss` 为 NOBITS、`_end` 取整到页。",
    judge: {
      mode: "**Tier C**：概念子任务自动判分。**平台自动判分的是 ld SECTIONS 的段加载地址计算；AC 不等于内核链接成功。** 真实链接见「本地完整验证」。",
      fill: "`uint32_t layout_sections(uint32_t base, const struct Sec *secs, int n, uint32_t vma[])`（`starter.c` 填写区）。`Sec` 含 `name[16]`/`size`/`align`；每段 VMA 写进 `vma[i]`，返回按页对齐的 `_end`。",
      input: "首行「起始VMA 段数n」（n 为 1..16），随后 n 行「段名 大小 对齐」（段名 ≤15 字符，大小与对齐为十进制无符号 32 位）。",
      output: "每段一行「段名 0x%08x」，末行「_end 0x%08x」（8 位十六进制小写）。每段先向上对齐到该段对齐值（**为 0 时不对齐**）得 VMA，再前进该段大小；末尾向上对齐到 4096 得 `_end`。",
      host: "地址与大小全用 `uint32_t`（避免 LLP64/LP64 分歧）；驱动缓冲 `vma[MAXS] = {0}` 已初始化，避免 `-Werror=maybe-uninitialized`。",
      real: "**判题观测量（各段 VMA 与 `_end`）就是 `readelf -S`/`-s` 核对的那组数**；真实 lds 还要处理 `ENTRY`、`PHDRS`、`NOBITS`、`PROVIDE`，子任务不覆盖。",
      source: "`tools/scse0_3.lds` 原文**未随工作区提供**，布局规则为按 ld 公开语义构造的**等价**示例。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① 先完成 1.1；② 补全 lds（`.text`→`.rodata`→`.data`→`.bss`，段间 `ALIGN(4096)`，`_end = .;` 前再对齐，别漏 `*(COMMON)`）；③ `make clean && make`；④ `readelf -S` 核对 Addr、`readelf -s | grep _end`；⑤ gxemul 启动。报 `location counter backwards` 即段顺序或对齐写错。",
    errs: [
      "忽略段对齐值顺序累加（`wrong.c`）：`kernel-typical` 的 `.bss` 落在 `0x800023e8` 而非 `0x80003000`。",
      "`_end` 不按页取整：`single-section` 会得 `0x80000001` 而非 `0x80001000`。",
      "把 align=0 当特殊值或死循环：契约明写为 0 时不对齐，`zero-align-and-small-align-on-unaligned` 覆盖。",
      "非对齐起始地址不抬首段：`unaligned-base` 的 base=`0x80000100`，首段须抬到 `0x80001000`。",
    ],
  },
  /* ---------------- 1.4 (Tier B) ---------------- */
  {
    id: "1930154331844431873", ch: 1, no: "1.4", tier: "B",
    bg: "C 代码不会自己跑起来：必须有人先把栈指针设好，`jal main` 才有意义。本实验先看 `fib` 递归与 `main` 循环被交叉编译器翻译成什么指令，再亲手补 `boot/start.S`，让内核从上电的第一条指令走到 C 世界。",
    kp: "**扩写（MIPS o32 公开约定）**：`jal` 把返回地址写进 `$ra` 并跳转，`jr $ra` 返回；递归函数必须把 `$ra` 与 `$fp` 压栈，否则第二层调用会覆盖返回地址。`$a0–$a3` 传前 4 个参数，第 5 个及以后走栈；`$v0` 存返回值。`slti`+`bnez`/`beq` 是 C 的 `if`/`for` 条件在汇编层的形态。`$sp` 必须指向合法可写内存（`bootstacktop`），MIPS 栈向下生长。",
    src: [
      ["boot/start.S", "本实验要补全的启动汇编", "$sp、bootstacktop、$fp、jal main、nop"],
      ["（分析对象）fib.c / main.c", "观察 C→汇编的对应关系", "fib 递归、main 循环"],
      ["tools/scse0_3.lds", "保证启动代码排在最前", ".text.boot、ENTRY"],
      ["include.mk", "提供交叉编译器", "CROSS_COMPILE、CFLAGS、-S"],
    ],
    srcNote: "**内容来源**：`boot/start.S` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。`local-build.md` 给出的启动骨架是**按 MIPS o32 与内核启动公开语义构造的等价示例**，不是北航原文转录；`starter.c` 里的 `fib`/`main` 是本地分析对象，**不参与平台判分**。",
    task: "**每步验证**：步骤 1 能说出 fib 递归深度与栈帧大小的关系；步骤 2 用 `-S -O0` 与 `-O2` 各出一份汇编并对比优化对栈操作的影响；步骤 3 标注 `jal`/`jr`/`slti`/`bnez`/`sw`/`lw`；步骤 4 补全 `boot/start.S`，`make` 后 gxemul 能打出 `main` 的输出。",
    judge: {
      mode: "**Tier B：本地构建（`judgeMode: \"LOCAL_BUILD\"`），平台不自动判分**，完成规则为 `SUBMIT`。原因：目标是 `boot/start.S` 里的 MIPS 汇编与特权启动时序，沙箱只有 x86-64 gcc、无 MIPS 交叉编译器、无 gxemul，单文件 C 无法承载 `.S`。",
      fill: "本地在 `boot/start.S` 中设置 `$sp` 指向 `bootstacktop`、清 `$fp`，再 `jal main`（后接 `nop` 填延迟槽）。平台侧 `starter.c` 只是可本地编译的 C 分析对象，**明确标注不参与判分**。",
      input: "无。`cases.json` 为 `{\"cases\":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。",
      output: "无平台判题输出。真实产出是交叉编译得到的汇编文件、补全的 `boot/start.S`，以及 gxemul 的串口日志。",
      host: "不适用于沙箱。本地需 MIPS 交叉工具链与 gxemul；用宿主 gcc 编出的 x86 汇编**不能**替代本实验的观察对象。",
      real: "完整验证途径就是本地 MIPS 交叉编译环境，见下方「本地完整验证」与同目录 `local-build.md`。",
      source: "`boot/start.S` 原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例，非北航原文转录。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① `mips-linux-gnu-gcc -S -O0` 与 `-O2` 各出一份汇编；② 定位栈帧建立（`addiu $sp,$sp,-N`、`sw $ra,...`）、条件跳转（`slti`/`bnez`）、调用返回（`jal`/`jr $ra`）；③ 补 `boot/start.S`：`la $sp, bootstacktop` → `move $fp,$zero` → `jal main` → `nop`；④ `objdump -d` 确认启动代码在 `0x80000000`；⑤ gxemul 启动看到 `main` 输出即成功。",
    errs: [
      "不设 `$sp` 就 `jal main`：C 函数一压栈就踩到未初始化内存，gxemul 立刻异常或无输出。",
      "`jal` 后不填延迟槽 `nop`：延迟槽里的指令会被执行，可能把刚设好的寄存器改坏。",
      "递归函数不保存 `$ra`：`fib` 第二层调用覆盖返回地址，`jr $ra` 跳回错误位置。",
      "用宿主 x86 gcc 生成的汇编冒充 MIPS 汇编：调用约定、寄存器名、延迟槽语义全都不同，观察结论无效。",
    ],
  },
  /* ---------------- 1.5 ---------------- */
  {
    id: "1930154460768948226", ch: 1, no: "1.5", tier: "A",
    bg: "内核里没有 libc，打印一句调试信息就得自己解析格式串、自己把整数转成字符、自己送进控制台驱动。`_Printf()` 是后续所有实验的调试基础设施——它写错了，你会以为别的代码错了。",
    kp: "**扩写**：转换说明形如 `%[flags][width][.precision]conv`。`width` 是**最少**字段宽度，`precision` 对 `d/x` 是**最少数字位数**、对 `s` 是**最多字符数**。`0` 与 `-` 冲突时 `-` 优先；指定 precision 时 `0` 对整数失效。",
    src: [
      ["lib/print.c", "本实验要补全的格式化输出", "_Printf、print_char、arg_int/arg_str"],
      ["drivers/gzconsole/console.c", "字符落到串口设备", "控制台 putchar 路径"],
    ],
    srcNote: "**内容来源**：`lib/print.c`/`console.c` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节）。harness 用 `struct ArgList` 等价替代 `va_list`、有界 `print_char` 替代控制台 sink，是**按公开语义构造的等价环境**；参考解另与宿主 libc `snprintf` 交叉校验（7 MATCH / 1 SKIP）。",
    task: "**每步验证**：步骤 1 能说明 print.c 与 console.c 的分工；步骤 2 补全 `_Printf()`；步骤 3 在内核里 `printf` 测试串，与宿主 `printf` 输出逐字比对。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判真实目标函数 `_Printf()`。",
      fill: "`static void _Printf(const char *fmt, struct ArgList *ap)`（`starter.c` 填写区）；`arg_int`/`arg_str` 取参、`print_char` 输出。",
      input: "格式串（≤200 字符）、参数个数 n（0..8）、n 个参数：「i <int32>」整数、「s <无空格字符串>」字符串（≤63 字符）。其余约束见契约原文。",
      output: "字符流原样输出，驱动末尾补一个换行。conv 支持 `d`/`x`/`c`/`s`/`%`，语义与 C 标准 printf 子集一致。",
      host: "`va_list`→`struct ArgList`；整数用 `int32_t`；输出走有界 `out_buf[4096]`；`%x` 按无符号 32 位，`-1` 打印 `ffffffff`。",
      real: "格式解析逻辑与真实内核一致，只有取参与字符 sink 是主机替身；本地仍应在 MIPS 环境用 `printf` 验证。",
      source: "`lib/print.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境。",
    },
    local: "本地完整验证（选填）：`make` 后在 `init/init.c` 里加 `printf(\"[%08d][%-8s][%.3s][%x]\\n\", -42, \"ab\", \"abcdef\", -1);`，gxemul 串口应打出 `[-0000042][ab      ][abc][ffffffff]`。",
    errs: [
      "解析出 width 却不用（`wrong.c`：`width = 0;`）：`%08d` 打成 `-42` 而非 `-0000042`。",
      "零填充补在符号之前：`-0000042` 才对；`%08.5d` 应得 `   00042`（precision 使 `0` 失效）。",
      "把普通数字文本当宽度：`100%% done %d` 里的 `100` 是字面文本，须原样输出。",
    ],
  },
  /* ---------------- 2.1 ---------------- */
  {
    id: "1908449571747745793", ch: 2, no: "2.1", tier: "A",
    bg: "MIPS 以 4KB 为单位管理内存，每一页用一个 `Page` 结构体记录。内核启动后要把成千上万个 `Page` 挂成空闲链表，之后 `page_alloc`/`page_free` 全在这条链上操作。链表宏写错一个指针，整个物理内存管理就会在某个随机时刻崩掉——所以本实验先把 `include/queue.h` 的两个宏写对。",
    kp: "**扩写**：`LIST_ENTRY(Page)` 展开成 `{ struct Page *le_next; struct Page **le_prev; }`。`le_prev` 是**指向前驱节点 next 字段的指针**（不是前驱节点指针），这个二级指针设计让「头插/删除」不需要特判头节点。`do{...}while(0)` 把多条语句包成一个语法单元，使宏在 `if(...) MACRO(); else ...` 里不会断裂。尾插必须从头遍历到最后一个节点，因此是 O(n)；头插是 O(1)。",
    src: [
      ["include/queue.h", "**工作区提供真实源码**（originalCode 8292 B）", "LIST_ENTRY、LIST_HEAD、LIST_INSERT_AFTER、LIST_INSERT_TAIL、LIST_INSERT_HEAD、LIST_REMOVE"],
      ["mm/pmap.c", "宏的使用者与官方自检", "struct Page、pp_link、pp_ref、page_free_list、page_check"],
    ],
    srcNote: "**内容来源**：本实验是全库仅有的两个有真实源码的实验之一——`originalCode` 就是 `include/queue.h`（8292 字节）、`answer` 是其完整实现（8493 字节），已导出为 `artifacts/experiment-judge/reference-queue.h` / `reference-answer-2_1.txt`。harness 直接驱动这两个宏，**不是构造的等价示例**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能解释 pp_link 与 pp_ref 的分工」；步骤 2 产出对 `do{}while(0)` 与 `le_prev` 二级指针的书面解释；步骤 3 产出补全的两个宏，验证方式是跑官方 `page_check`（它依赖头插/尾插的确切顺序）。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是 `include/queue.h` 的真实宏。",
      fill: "`LIST_INSERT_AFTER(listelm, elm, field)` 与 `LIST_INSERT_TAIL(head, elm, field)` 两个宏，在 `starter.c` 的「学生填写区：实现 include/queue.h 中的两个宏」内。",
      input: "首行操作数 n（0..256），随后 n 行操作。元素用编号标识，合法范围 0..63（`MAXP=64`）；驱动令 `pp_ref = 编号`，故输出序列即元素编号序列。",
      output: "只有一行：遍历链表得到的 `pp_ref` 序列（空格分隔）。四个操作码都**没有独立输出行**，只改变链表；宏写错时序列的顺序或元素个数立刻不同。",
      ops: [
        ["T", "`T x`：把元素 x 尾插（`LIST_INSERT_TAIL`，**学生填写**）；x 已在链表中则忽略。"],
        ["A", "`A a b`：在元素 a 之后插入 b（`LIST_INSERT_AFTER`，**学生填写**）；要求 a 在链表中且 b 不在，否则忽略。"],
        ["H", "`H x`：把元素 x 头插（`LIST_INSERT_HEAD`，驱动提供的对照实现，不在填写区）。"],
        ["R", "`R x`：移除元素 x（`LIST_REMOVE`，驱动提供的对照实现，不在填写区）。"],
      ],
      host: "`Page` 用固定宽度字段建模，物理内存是 `static unsigned char phys[]` 数组、物理地址即数组下标；链表指针是宿主指针，但**判题观测量是 pp_ref 序列而非指针值**，因此与 MIPS 32 位地址宽度无关。",
      real: "宏展开后的指针操作与真实内核逐字一致；本地仍应在 MIPS 环境跑 `page_check` 做完整验证。",
      source: "`include/queue.h` 为工作区提供的**真实源码**，非构造示例。",
    },
    local: "本地完整验证（选填）：在 `mm/pmap.c` 的 `mips_init` 中恢复 `page_check()` 调用，`make` 后 gxemul 启动，官方自检会逐项断言链表顺序与 `pp_ref`；任何宏写错都会在这里 panic。",
    errs: [
      "把尾插直接复用头插（`wrong.c` 的做法，学生最常见错误）：链表变成 LIFO，`page_alloc` 先分出高地址页，与真实内核行为相反。",
      "`le_prev` 写成前驱节点指针而非「前驱 next 字段的地址」：删除节点时前驱的 next 不会被更新，链表断裂。",
      "插入后不维护被插节点的 `le_prev`：下一次 `LIST_REMOVE` 会写坏内存。",
      "宏体不加 `do{}while(0)`：在 `if/else` 中展开后 `else` 悬空，编译报错或语义错。",
    ],
  },
  /* ---------------- 2.2 ---------------- */
  {
    id: "1908532874240593922", ch: 2, no: "2.2", tier: "A",
    bg: "内核要先知道自己有多少内存，才能谈分配。MIPS 机器上这份信息来自 CMOS 的两个读数（1MB 以下与 1MB 以上各多少 KB）。`mips_detect_memory()` 把它们换算成字节并算出页框总数，之后 `page_init`、`page_alloc`、`boot_map_segment` 全都以这四个全局变量为边界。",
    kp: "**扩写**：`basemem` 是 1MB 以下的常规内存字节数，`extmem` 是 1MB 以上的扩展内存字节数，`maxpa = basemem + extmem` 是物理地址上限，`npage = maxpa / BY2PG` 是页框总数（`BY2PG = 4096`）。**CMOS 读数的单位是 KB，必须乘 1024**；漏乘会让整个内存管理系统缩小 1024 倍，而且不会立刻报错——这是最难查的一类缺陷。",
    src: [
      ["mm/pmap.c", "本实验要补全的函数与全局变量", "mips_detect_memory、maxpa、npage、basemem、extmem、BY2PG"],
      ["include/mmu.h", "页大小与地址常量", "BY2PG、KERNBASE"],
      ["mm/pmap.c 的 page_init", "npage 的第一个消费者", "for (i = 0; i < npage; i++)"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 仅 18 字节、`answer` 仅 6 字节，均无效）。harness 用两个 stdin 读数等价替代 CMOS 端口读取，其余全局变量名与语义按北航原文与公开 MOS 语义构造，是**等价环境**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能说出四个变量各自的单位与含义」；步骤 2 产出补全的 `mips_detect_memory()`，输入是 CMOS 的 KB 读数、输出是四个全局变量，验证方式是官方 `page_check` 与后续 `page_init` 的空闲页计数。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `mips_detect_memory()`。",
      fill: "`void mips_detect_memory(void)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 mips_detect_memory()」内。从驱动提供的 `cmos_basemem_kb`/`cmos_extmem_kb` 读数算出 `basemem`、`extmem`、`maxpa`、`npage`。",
      input: "两个 KB 读数模拟 CMOS：`basemem_kb extmem_kb`（一行，十进制）。",
      output: "一行四个十进制数：`maxpa basemem extmem npage`（前三者单位字节，npage 单位页）。",
      host: "物理内存大小用 `uint32_t` 承载（MIPS 32 位物理地址），不用 `size_t`/`long`；CMOS 端口读取用两个 stdin 整数等价替代，因为沙箱是 x86-64 用户态进程、无法访问 MIPS 的 CMOS 端口。",
      real: "换算逻辑与真实内核逐字一致；本地仍应在 gxemul 里跑真实 CMOS 探测路径验证。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：`make` 后 gxemul 启动，在 `mips_init` 里 `printf(\"maxpa=%x npage=%d\\n\", maxpa, npage);`，与 gxemul 命令行指定的内存大小（如 `-m 64`）核对：64MB 应对应 `npage = 16384`。",
    errs: [
      "把 KB 读数直接当字节写进 basemem/extmem（`wrong.c` 的做法）：maxpa 与 npage 全部缩小 1024 倍。",
      "`maxpa` 只算了 extmem 漏掉 basemem：1MB 以下的内存凭空消失。",
      "`npage` 用 `maxpa / 4096` 之外的粒度（如按字节或按 KB）：后续所有页号越界。",
      "用 `int` 承载物理地址：大于 2GB 时溢出为负，必须用 `uint32_t`。",
    ],
  },
  /* ---------------- 2.3 ---------------- */
  {
    id: "1908538371526131713", ch: 2, no: "2.3", tier: "A",
    bg: "知道有多少内存之后，要把「内核自己占用的」与「可以分出去的」分开。`page_init()` 遍历 `[0, npage)`，把内核映像与 `pages` 数组占用的页标为已用，其余全部挂进 `page_free_list`。这条链表是之后整个物理内存分配的唯一来源。",
    kp: "**扩写**：`freemem` 是内核映像结束（`_end`）之后的第一个可用物理地址，先 `ROUND(freemem, BY2PG)` 向上取整到页边界。`pages[i].pp_ref` 非 0 表示该页已被引用，不得进空闲链表。**真实源码用 `LIST_INSERT_HEAD`（头插）**，所以空闲链表是页号**递减**序，`page_alloc` 因此先分出高地址页（LIFO）——这个顺序被官方 `page_check` 依赖，改成尾插就错。",
    src: [
      ["mm/pmap.c", "本实验要补全的函数", "page_init、page_free_list、freemem、pages、npage、pp_ref"],
      ["include/queue.h", "链表宏（实验 2.1 已实现）", "LIST_INIT、LIST_INSERT_HEAD、LIST_INSERT_TAIL"],
      ["tools/scse0_3.lds", "提供 _end，决定 freemem 起点", "_end（实验 1.3）"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 的物理内存用 `static unsigned char phys[]` 建模、物理地址即下标，`page_init` 的头插语义按公开 MOS 源码语义构造，是**等价环境**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能说明已分配/未分配页如何区分、为什么内核占用上限重要」；步骤 2 产出补全的 `page_init()`，输入是 `npage` 与 `freemem`、输出是空闲链表与各类计数，验证方式是官方 `page_check` 的空闲页总数断言。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_init()`。",
      fill: "`static void page_init(void)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 page_init()」内。用 `include/queue.h` 的宏把可用页挂进 `page_free_list`。",
      input: "首行 `npage freemem_bytes`（页框总数与内核映像结束后的第一个可用物理地址，十进制）。",
      output: "第一行为空闲链表页号序列（**真实源码头插，故为递减序**），第二行为 `freemem used_count free_count other_count`。",
      host: "物理内存是 `static unsigned char phys[NPAGE_MAX*BY2PG]`，物理地址 = 数组下标；`Page` 元数据用固定宽度字段；所有地址/页号用 `uint32_t`，避免宿主 `long` 宽度差异。",
      real: "头插顺序、`pp_ref` 判定与 `freemem` 取整语义与真实内核一致；本地仍应跑官方 `page_check` 完整验证。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：恢复 `mips_init` 中的 `page_check()`，gxemul 启动后它会断言空闲页总数等于 `npage - 内核占用页数`，并逐项检查链表顺序。",
    errs: [
      "用 `LIST_INSERT_TAIL` 代替 `LIST_INSERT_HEAD`（`wrong.c` 的做法）：空闲链表变成递增序，`page_alloc` 先分低地址页，与真实内核 LIFO 相反。",
      "忘记 `ROUND(freemem, BY2PG)`：内核占用的半页被当成整页分出去，内核被自己覆盖。",
      "把 `pp_ref != 0` 的页也挂进空闲链表：同一页被两个使用者同时持有。",
      "遍历上界写成 `freemem/BY2PG` 而不是 `npage`：高地址内存全部丢失。",
    ],
  },
  /* ---------------- 2.4 ---------------- */
  {
    id: "1908539177407119361", ch: 2, no: "2.4", tier: "A",
    bg: "有了空闲链表，分配与释放就是链表摘取与回挂。但内核页分配有一条硬要求：**分出去的页必须清零**，否则上一个进程的数据会泄漏给下一个使用者。`page_alloc`/`page_free` 还要维护 `pp_ref`，保证共享页不被提前回收。",
    kp: "**扩写**：`page_alloc` 从 `page_free_list` 摘第一个节点，`bzero(page2kva(pp), BY2PG)` 清零，`pp_ref` 置 0，返回 0；链表空时返回 `-E_NO_MEM`。`page_free` 要求 `pp_ref == 0`（否则 panic），把页挂回链表。`pp_ref` 即「有多少个页表项指向这一页」，由 `page_insert`/`page_remove` 增减。",
    src: [
      ["mm/pmap.c", "本实验要补全的两个函数", "page_alloc、page_free、page_free_list、pp_ref、bzero、E_NO_MEM"],
      ["mm/pmap.c 的 mips_init", "需注释掉 page_check 以便逐步验证", "page_check"],
      ["include/queue.h", "链表摘取与回挂", "LIST_FIRST、LIST_REMOVE、LIST_INSERT_HEAD"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 用 `phys[]` 数组等价替代真实物理内存，`page2kva` 等价替代为「物理地址 → 数组下标」，是**按公开语义构造的等价环境**。",
    task: "**每步验证**：步骤 1–2 补全两个函数；步骤 3 自测边界（链表空时分配、`pp_ref>0` 时释放）；步骤 4 在 `mips_init` 中注释掉 `page_check`，改为逐步验证。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_alloc()`/`page_free()`。",
      fill: "`static int page_alloc(struct Page **pp)` 与 `static void page_free(struct Page *pp)`（`starter.c` 填写区）。",
      input: "首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。",
      output: "每个 `A`/`F` 各输出一行，末行额外输出空闲链表页号序列（递减序＝头插）。`S` 无输出行。",
      ops: [
        ["A", "`A`：分配一页，输出「A 页号 首字」；失败输出「A -4」（`-E_NO_MEM`，2 字段）。「首字」验证清零：驱动每次分配后写入 `0xDEADBEEF`，故同一页回收再分配时首字必须为 0。"],
        ["S", "`S k r`：把第 k 次成功分配的页 `pp_ref` 设为 r。**无输出行**，只改状态，供后续 `F` 观察。"],
        ["F", "`F k`：释放第 k 次成功分配的页，输出「F 页号 pp_ref」。`pp_ref>0` 时不得真正回收。"],
      ],
      host: "物理内存是 `static unsigned char phys[]`，`page2kva` 等价替代为下标换算；错误码 `-E_NO_MEM` 即 `-4`，与真实内核一致。",
      real: "摘链/回挂/清零/引用计数语义与真实内核一致；本地仍应跑官方 `page_check` 的「free 后重新 alloc，`*temp` 必须为 0」断言。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：恢复 `page_check()`，它会反复 alloc/free 并断言清零与 `pp_ref`；再在 gxemul 里观察长时间运行后空闲页总数是否守恒。",
    errs: [
      "漏掉 `bzero`（`wrong.c` 的做法）：页被回收再分配时残留上一个使用者的数据，官方「`*temp` 必须为 0」断言失败。",
      "链表空时不返回 `-E_NO_MEM` 而是解引用 NULL：沙箱会判 Runtime Error 而非答案错误。",
      "`page_free` 不检查 `pp_ref == 0`：共享页被提前回收，另一个使用者的映射指向已重分配的页。",
      "释放时用尾插而真实源码头插：末态空闲链表序列与参考不符。",
    ],
  },
  /* ---------------- 2.5 ---------------- */
  {
    id: "1908539875976839170", ch: 2, no: "2.5", tier: "A",
    bg: "物理页有了，接下来要把虚拟地址翻译成物理地址。MIPS 用两级页表：页目录（PDE）指向一张页表，页表项（PTE）指向一页物理内存。`pgdir_walk` 就是沿这两级走下去、必要时新建一张页表的函数——它是 `page_insert`、`boot_map_segment`、`load_icode_mapper` 的共同底座。",
    kp: "**扩写**：`PDX(va)` 取 va 的高 10 位作页目录下标，`PTX(va)` 取中间 10 位作页表下标，低 12 位是页内偏移。`boot_pgdir_walk` 用于启动期（此时 `page_alloc` 还不可用，改用 `alloc`），`pgdir_walk` 用于运行期。`create == 1` 且页目录项无效时要新建一页作页表、清零、置 `PTE_V`、并令新页表的 `pp_ref++`；**忘记置 `PTE_V` 是最典型的缺陷**。",
    src: [
      ["mm/pmap.c", "本实验要补全的两个函数", "boot_pgdir_walk、pgdir_walk、PDX、PTX、PTE_ADDR、PTE_V、KADDR"],
      ["include/mmu.h", "两级索引与页表项标志", "PDX、PTX、PTE_V、PTE_ADDR、BY2PG"],
      ["mm/pmap.c 的 page_alloc", "运行期新建页表的页来源", "page_alloc、pp_ref"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 为 0 字节、`answer` 仅 6 字节）。harness 的两级页表、`PDX`/`PTX` 位段与 `PTE_V` 语义按公开 MOS/ MIPS 语义构造，是**等价环境**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能画出 va → PDX → PDE → PTX → PTE → pa 的翻译链」；步骤 2 产出补全的两个函数，输入是 (va, create)、输出是页表项指针或返回码，验证方式是官方 `page_check` 的 walk 断言。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `boot_pgdir_walk()`/`pgdir_walk()`。",
      fill: "`static Pte *boot_pgdir_walk(Pde *pgdir, uint32_t va, int create)` 与 `static int pgdir_walk(Pde *pgdir, uint32_t va, int create, Pte **ppte)`，在 `starter.c` 的「学生填写区」内。",
      input: "首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。",
      output: "每个操作各输出一行，末行为空闲链表页号序列。",
      ops: [
        ["B", "`B va create`：启动期步行 `boot_pgdir_walk`，输出「B 页表物理地址 PTX」；取不到页表项时两个字段均为 `-1`。"],
        ["W", "`W va create`：运行期步行 `pgdir_walk`，输出「W 返回码 页表物理地址 PTX」；取不到页表项时后两个字段均为 `-1`（页表分配失败时返回码为 `-4`，否则为 `0`）。"],
        ["Q", "`Q pageidx`：查询该页引用计数，输出「Q 页号 pp_ref」。新建页表必须使该页 `pp_ref` 加一。"],
      ],
      host: "`Pde`/`Pte` 用 `uint32_t`（MIPS 32 位页表项），不用 `uintptr_t` 或 `long`；`KADDR` 等价替代为「物理地址 → `phys[]` 下标」；页表物理地址是数组下标而非宿主指针，故与宿主地址宽度无关。",
      real: "两级索引、create 语义与 `pp_ref` 维护与真实内核一致；本地仍应跑官方 `page_check` 完整验证。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：恢复 `page_check()`，它会 walk 一系列 va 并断言返回的 PTE 指针与 `pp_ref`；再在 gxemul 里确认内核能正常访问 `UENVS`/`UPAGES` 区域。",
    errs: [
      "新建页表后忘记置 `PTE_V`（`wrong.c` 的做法）：下次 `create==0` 的步行返回 NULL，`create==1` 的步行再分配一张页表，造成泄漏与映射丢失。",
      "新建页表不清零：残留字节被当成有效 PTE，随机映射到错误物理页。",
      "新页表的 `pp_ref` 不递增：它可能被 `page_free` 提前回收。",
      "`create==0` 时也去分配页表：查询操作产生副作用，空闲页计数不符。",
      "用 `PDX` 取低位、`PTX` 取高位：两级索引颠倒，所有映射错位。",
    ],
  },
  /* ---------------- 2.6 ---------------- */
  {
    id: "1908540360305704962", ch: 2, no: "2.6", tier: "A",
    bg: "会走页表之后，就要会批量填页表。内核启动时必须把整段物理内存（内核映像、`pages` 数组、之后的 `envs` 数组）映射到 `kseg0` 的对应虚拟地址，否则 C 代码一访问全局变量就异常。`boot_map_segment` 就是这个批量映射器。",
    kp: "**扩写**：`boot_map_segment(pgdir, va, size, pa, perm)` 要求 `size` 是 `BY2PG` 的整数倍（否则直接返回），然后逐页 `boot_pgdir_walk(..., create=1)` 取 PTE 并写入 `pa | perm | PTE_V`。**`PTE_V` 不能漏**：物理地址与权限都对但有效位为 0 时，MMU 认为该页不存在。`kseg0` 的映射满足 `pa = va - 0x80000000`，这是内核早期不经 TLB 的固定映射。",
    src: [
      ["mm/pmap.c", "本实验要补全的函数", "boot_map_segment、boot_pgdir_walk、PTE_V、perm"],
      ["mm/pmap.c 的 mips_vm_init", "调用者", "boot_map_segment(pgdir, KERNBASE, ...), pages, envs"],
      ["include/mmu.h", "地址常量与权限位", "KERNBASE、PTE_V、PTE_R、BY2PG"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。harness 的页表项编码（`pa|perm|PTE_V`）与「页表不存在读作 ~0、已存在未映射读作 0」的观测口径按公开 MOS 语义构造，是**等价环境**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能解释为什么启动期必须建立这段映射」；步骤 2 产出补全的 `boot_map_segment`，输入是 (va, size, pa, perm)、输出是页表项内容，验证方式是官方 `page_check` 与内核能否访问全局变量。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `boot_map_segment()`。",
      fill: "`static void boot_map_segment(Pde *pgdir, uint32_t va, uint32_t size, uint32_t pa, int perm)`，在 `starter.c` 的「学生填写区：实现 mm/pmap.c 的 boot_map_segment()」内。",
      input: "首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。",
      output: "每个操作各输出一行。页表项取值口径：所属页表不存在时为 `4294967295`（~0）；页表已存在时为该槽位原值（未映射即 `0`，已映射即 `pa|perm|PTE_V`）。",
      ops: [
        ["M", "`M va size pa perm`：建立区间映射，输出「M 首页页表项 末页页表项」；`size==0` 时末页字段固定为 `4294967295`。"],
        ["Q", "`Q va`：查询该虚拟地址的页表项原值，输出「Q 页表项」。"],
      ],
      host: "页表项用 `uint32_t`，`~0` 打印为十进制 `4294967295`（不用 `%p`/`%lx`）；`KADDR` 等价替代为 `phys[]` 下标换算；驱动自有输出缓冲在声明处初始化。",
      real: "逐页 walk + 写 `pa|perm|PTE_V` 的逻辑与真实内核一致；本地仍应在 gxemul 里确认内核能访问 `pages`/`envs` 全局数组。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：`make` 后 gxemul 启动，若 `boot_map_segment` 漏写 `PTE_V`，内核在第一次访问 `pages[]` 时就会 TLB 异常；能跑到 `mips_init` 末尾即说明映射有效。",
    errs: [
      "写页表项时漏掉 `PTE_V`（`wrong.c` 的做法）：物理地址与权限都对，但 MMU 认为该页不存在，后续 walk 与 `page_lookup` 都取不到映射。",
      "不检查 `size % BY2PG != 0`：非整页区间被静默映射，末页越界。",
      "循环用 `va <= va_end` 而非 `<`：多映射一页，踩到相邻区域。",
      "`pa` 不随 `va` 同步前进：整段区间映射到同一物理页。",
      "`size == 0` 时仍去 walk 末页：驱动约定此时末页字段固定为 `4294967295`。",
    ],
  },
  /* ---------------- 2.7 ---------------- */
  {
    id: "1908540572415852545", ch: 2, no: "2.7", tier: "A",
    bg: "批量映射之后是单页的增删。`page_insert` 把一页物理内存映射到某个虚拟地址，`page_remove` 解除映射。这两个函数必须与 `pp_ref` 和 TLB 保持一致：引用计数错了会内存泄漏，TLB 不刷新会访问到已经换出的物理页。",
    kp: "**扩写**：`page_insert` 先 `pgdir_walk(..., create=1)` 取 PTE；若该 va 已映射，**先判断旧页是否就是 pp**——是则直接返回 0（`pp_ref` 不重复递增），否则 `page_remove` 旧页；然后写 `page2pa(pp) | perm | PTE_V`、`pp_ref++`、`tlb_invalidate`。`page_remove` 清 PTE、`pp_ref--`、`tlb_invalidate`；只有 `pp_ref` 归零页才真正可回收。",
    src: [
      ["mm/pmap.c", "本实验要补全的两个函数", "page_insert、page_remove、pgdir_walk、pa2page、page2pa、pp_ref、tlb_invalidate"],
      ["mm/tlb_asm.S", "TLB 失效的真实实现（实验 2.8）", "tlb_out、tlbp、tlbwi"],
      ["include/mmu.h", "页表项标志", "PTE_V、perm"],
    ],
    srcNote: "**内容来源**：`mm/pmap.c` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。harness 里 `tlb_invalidate` 是主机端非终止 no-op（沙箱无 CP0/TLB），其余 `pp_ref` 与页表项语义按公开 MOS 语义构造，是**等价环境**。",
    task: "**每步验证**：步骤 1 能解释页表项增删对 TLB 的影响；步骤 2 补全 `page_insert`（含「已映射的就是 pp」提前返回分支）；步骤 3 确认 `tlb_invalidate` 的调用位置，用官方 `page_check` 的重复 insert 断言验证。",
    judge: {
      mode: "**Tier A**：主机端等价 harness，判的就是真实目标函数 `page_insert()`/`page_remove()`。",
      fill: "`static int page_insert(Pde *pgdir, struct Page *pp, uint32_t va, uint32_t perm)` 与 `static void page_remove(Pde *pgdir, uint32_t va)`，在 `starter.c` 的「学生填写区」内。",
      input: "首行 `npage freemem_bytes`，次行操作数，随后每行一个操作（见下方操作码说明）。",
      output: "每个操作各输出一行；无映射时物理地址打印 `4294967295`（~0）。本实验**不输出**末态空闲链表。",
      ops: [
        ["I", "`I pageidx va perm`：插入映射，输出「I 返回码 物理地址 pp_ref」；页表分配失败时返回码为 `-4`。"],
        ["R", "`R va`：解除映射，输出「R 原页号 解除后物理地址 解除后 pp_ref」；原本未映射时原页号为 `-1`。"],
        ["Q", "`Q va`：查询 va 对应物理地址，输出「Q 物理地址」。"],
        ["P", "`P pageidx`：查询该页引用计数，输出「P 页号 pp_ref」。"],
      ],
      host: "物理地址用 `uint32_t`，`~0` 打印十进制 `4294967295`；`tlb_invalidate`/`panic` 是主机端非终止 no-op（沙箱无 CP0），因此判题观测量落在页表项与 `pp_ref` 上，而非真实 TLB 状态。",
      real: "`pp_ref` 与页表项的维护逻辑与真实内核逐字一致；TLB 失效的真实效果必须在本地 MIPS 环境（实验 2.8）验证。",
      source: "`mm/pmap.c` 原文**未随工作区提供**，harness 为按公开语义构造的**等价**环境，非北航原文转录。",
    },
    local: "本地完整验证（选填）：恢复 `page_check()`，它包含「同一页重复 insert 同一 va，`pp_ref` 不得重复递增」与「remove 后 `pp_ref` 归零、页回到空闲链表」两条断言；再在 gxemul 里确认无 TLB 异常。",
    errs: [
      "漏掉「已映射的就是 pp」提前返回分支（`wrong.c` 的做法）：重复 insert 使 `pp_ref` 重复递增，之后 remove 一次减不到 0，页永远回不到空闲链表。",
      "插入前不 `page_remove` 旧页：旧页 `pp_ref` 不减，泄漏。",
      "写 PTE 时漏 `PTE_V`：映射看起来存在但 MMU 认为无效。",
      "增删后不调 `tlb_invalidate`：快表里仍是旧翻译，访问到已换出的物理页（本地才会暴露）。",
      "`page_remove` 对未映射的 va 也减 `pp_ref`：计数下溢成巨大正数。",
    ],
  },
  /* ---------------- 2.8 (Tier B) ---------------- */
  {
    id: "1908540698916061185", ch: 2, no: "2.8", tier: "B",
    bg: "页表建好了，但每次访存都走两级页表太慢。MIPS 用 TLB（Translation Lookaside Buffer）缓存最近的翻译结果。TLB 未命中时硬件抛异常，软件必须把正确的翻译填进去——这就是 `mm/tlb_asm.S` 里 `tlb_out` 的职责，也是实验 2.7 的 `tlb_invalidate` 真正生效的地方。",
    kp: "**扩写（MIPS CP0 公开约定）**：`tlbp` 按 `EntryHi` 的 VPN/ASID 在 TLB 中查找，命中则把项号写进 `Index`，未命中则把 `Index` 最高位置 1（软件据此跳 `NOFOUND`）。`tlbwi` 把 `EntryLo0`/`EntryLo1`/`EntryHi` 写进 `Index` 指定的项。**`tlbp` 之后的 4 条 `nop` 是 CP0 冒险间隔**（读 `Index` 前必须等 `tlbp` 完成），不是分支延迟槽——这是本实验要求书面解释的重点。",
    src: [
      ["mm/tlb_asm.S", "本实验要补全的汇编函数", "tlb_out、NOFOUND、tlbp、tlbwi、nop × 4"],
      ["include/mmu.h", "TLB 相关 CP0 寄存器编号", "CP0_ENTRYHI、CP0_ENTRYLO0/1、CP0_INDEX"],
      ["mm/pmap.c", "tlb_invalidate 的调用方（实验 2.7）", "page_insert、page_remove、tlb_invalidate"],
    ],
    srcNote: "**内容来源**：`mm/tlb_asm.S` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。`local-build.md` 与 `starter.c` 注释里的汇编骨架是**按 MIPS CP0 公开语义构造的等价示例**，不是北航原文转录；`starter.c` **不参与平台判分**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能说明 TLB 未命中的处理流程」；步骤 2 产出对 `tlbp`/`tlbwi` 用途与 4 条 `nop` 原因的书面解释（须区分 CP0 冒险与分支延迟槽）；步骤 3 产出补全的 `tlb_out`，验证方式是 gxemul 里触发 TLB Miss 后能正确取到映射、不再反复异常。",
    judge: {
      mode: "**Tier B：本地构建（`judgeMode: \"LOCAL_BUILD\"`），平台不自动判分**，完成规则为 `SUBMIT`。原因：`tlbp`/`tlbwi` 直接操作 CP0 与真实 TLB 硬件，沙箱是 x86-64 用户态进程、无 MIPS 交叉编译器、无 gxemul、无 CP0。用 C 写一个「像那样」的程序**不构成概念同构**，只会变成被否决的代理题。",
      fill: "本地在 `mm/tlb_asm.S` 的 `tlb_out` 空出的两行分别填 `tlbp` 与 `tlbwi`。平台侧 `starter.c` 只是注释形式的汇编骨架与 CP0 位段速查，**明确标注不参与判分**。",
      input: "无。`cases.json` 为 `{\"cases\":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。",
      output: "无平台判题输出。真实产出是补全的 `tlb_asm.S`、对 4 条 `nop` 的书面解释，以及 gxemul 的运行日志。",
      host: "不适用于沙箱。实验 2.7 的 harness 里 `tlb_invalidate` 只是非终止 no-op，正是因为它无法在主机上等价再现 CP0 行为。",
      real: "完整验证途径是本地 MIPS 交叉编译 + gxemul，见下方「本地完整验证」与同目录 `local-build.md`。",
      source: "`mm/tlb_asm.S` 原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例，非北航原文转录。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① 从 MIPS 手册查 `tlbp`/`tlbwi` 的语义与 `Index` 最高位的未命中标志；② 在 `tlb_out` 的两行空白处填 `tlbp`（查）与 `tlbwi`（写），`tlbp` 后保留 4 条 `nop` 再读 `Index`；③ 未命中分支跳 `NOFOUND`；④ `make` 后 `mips-linux-gnu-objdump -d gzemul/vmlinuz` 确认指令编码与 `nop` 数量；⑤ gxemul 启动，跑一个会触发 TLB Miss 的用户进程，确认不再反复异常。",
    errs: [
      "把 `tlbp` 与 `tlbwi` 顺序颠倒：先写后查，写进的项号是上一次遗留的 `Index`。",
      "`tlbp` 后立刻读 `Index`（删掉 4 条 `nop`）：CP0 冒险，读到旧值，未命中被误判为命中。",
      "把 4 条 `nop` 解释成分支延迟槽：MIPS 分支延迟槽只有 1 条，这里是 CP0 写后读冒险间隔。",
      "不检查 `Index` 最高位就 `tlbwi`：未命中时 `Index` 是无效项号，写坏 TLB。",
      "只填 `tlbwi` 不填 `tlbp`：命中项永远查不到，每次都当未命中处理。",
    ],
  },
  /* ---------------- 3.12 (Tier B) ---------------- */
  {
    id: "1930155905954140162", ch: 3, no: "3.12", tier: "B",
    bg: "进程跑起来之后，总会遇到「不该由当前指令流处理」的事件：非法地址、缺页、系统调用、外设中断。MIPS 把这些统称异常，硬件强制把 PC 跳到固定入口。本实验要求你把异常处理代码**链接到那个固定地址**，并在那里写分发代码，把不同原因的事件交给不同处理程序。",
    kp: "**扩写（MIPS CP0 公开约定）**：通用异常入口是 `0x80000080`（TLB Refill 另有 `0x80000000`，Reset 是 `0xBFC00000`）。`Cause` 寄存器的 **ExcCode 在 bit6..2**（0=Int 中断、2/3=TLB Load/Store、4=AdEL、5=AdES、8=Sys、…），**IP 在 bit15..8**（挂起的中断线），BD 在 bit31（延迟槽）。`Status` 的 IM 在 bit15..8、EXL 在 bit1、IE 在 bit0。分发代码必须先读 `Cause` 再按 ExcCode/IP 跳转，返回用 `eret` 而非 `jr`（`eret` 会清 EXL 并恢复 PC）。",
    src: [
      ["tools/scse0_3.lds", "把异常入口固定到 0x80000080", ".exc.handler 段、. = 0x80000080"],
      ["lib/traps.c / lib/genex.S", "异常分发与处理", "handle_exception、ExcCode 分派表"],
      ["include/mmu.h", "CP0 寄存器与位段", "CP0_CAUSE、CP0_STATUS、CAUSE_EXCCODE、CAUSE_IP"],
      ["lib/env.c", "异常现场保存", "Trapframe、TIMESTACK、env_pop_tf"],
    ],
    srcNote: "**内容来源**：本实验的 lds 与异常分发代码的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。`local-build.md` 与 `starter.c` 注释里的分发骨架是**按 MIPS CP0 公开语义构造的等价示例**，不是北航原文转录；`starter.c` **不参与平台判分**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能说出 Cause 的 bit2/bit6/bit8/bit15 各是什么」；步骤 2 产出改好的 lds（异常入口 = `0x80000080`）；步骤 3 产出入口处的分发代码，验证方式是 `readelf -S` 看到该段地址正确、gxemul 里触发一次系统调用能进 `handle_sys`。",
    judge: {
      mode: "**Tier B：本地构建（`judgeMode: \"LOCAL_BUILD\"`），平台不自动判分**，完成规则为 `SUBMIT`。原因：需要 MIPS 交叉链接器把代码定位到指定物理地址、需要真实 CP0 异常时序、需要 gxemul 触发异常；沙箱是 x86-64 用户态单文件 C，三者都没有。用 C 模拟一个「异常分发表」**不构成概念同构**，不编造代理题。",
      fill: "本地在 lds 中定位异常入口段到 `0x80000080`，并在该入口写读 `Cause`、按 ExcCode/IP 跳转的分发汇编。平台侧 `starter.c` 只是 Cause/Status 位段速查注释，**明确标注不参与判分**。",
      input: "无。`cases.json` 为 `{\"cases\":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。",
      output: "无平台判题输出。真实产出是改好的 lds、分发代码、`readelf -S` 输出与 gxemul 异常触发日志。",
      host: "不适用于沙箱。主机端无法把代码链接到指定物理地址，也没有 CP0 与异常时序。",
      real: "完整验证途径是本地 MIPS 交叉编译 + gxemul，见下方「本地完整验证」与同目录 `local-build.md`。",
      source: "lds 与分发代码原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例，非北航原文转录。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① 在 `tools/scse0_3.lds` 里加 `. = 0x80000080; .exc.handler : { *(.exc.handler) }`，注意它必须落在 `.text` 之后、且不破坏其他段对齐；② 在该段写分发代码：`mfc0 $t0, CP0_CAUSE` → 取 ExcCode（`srl`+`andi`）→ 查表跳转；中断分支再查 IP；③ `make` 后 `mips-linux-gnu-readelf -S gzemul/vmlinuz` 确认 `.exc.handler` 的 Addr = `0x80000080`；④ `objdump -d` 确认入口第一条指令；⑤ gxemul 里跑一个用户进程发起系统调用，确认进入 `handle_sys` 并能 `eret` 返回。",
    errs: [
      "把入口设成 `0x80000000`：那是 TLB Refill 专用入口，通用异常会被硬件跳到 `0x80000080`，你的代码根本不在那里。",
      "ExcCode 位段取错（当成 bit7..3 或直接读整个 Cause）：分派到错误处理程序。",
      "用 `jr $ra` 代替 `eret` 返回：EXL 不清零，后续中断全被屏蔽，系统看起来「卡死」。",
      "分发前不保存现场：处理程序用掉的寄存器覆盖了用户进程的值。",
      "lds 里把异常段放在 `.text` 之前并抬高基址：内核入口不再是 `0x80000000`，上电即跑飞。",
    ],
  },
  /* ---------------- 3.13 (Tier B) ---------------- */
  {
    id: "1930156027966443521", ch: 3, no: "3.13", tier: "B",
    bg: "时间片轮转的「时间」从哪来？答案是硬件定时器。它周期性地向 CPU 拉一根中断线，CPU 被迫跳进异常入口，操作系统才有机会说「你跑够了，换下一个」。本实验把时钟中断绑到 4 号中断线、写 `timer_irq` 调 `sched_yield`——这是抢占式调度的物理基础。",
    kp: "**扩写**：时钟中断是**外部周期性事件**，软件循环替代不了。gxemul 的 `oldtestmips` 用 8253/8254 定时器（也可用 CP0 `Count`/`Compare`）。绑 4 号中断意味着：`Cause.IP4`（bit12）挂起、`Status.IM4`（bit12）允许、`Status.IE`（bit0）总开关为 1、`Status.EXL`（bit1）为 0 才能响应。分发用 `Cause & Status` 的 IM 位段判断是哪根线。",
    src: [
      ["kclock_init（drivers/）", "设频率、绑 4 号中断", "定时器端口/CP0 Compare、Status.IM4"],
      ["lib/traps.c / 异常分发汇编", "识别 IP4 并跳 timer_irq", "Cause.IP4、Cause & Status"],
      ["tools/scse0_3.lds", "中断入口 0x80000080（实验 3.12）", ".exc.handler"],
      ["lib/sched.c", "被调用的调度函数", "sched_yield、env_pri"],
    ],
    srcNote: "**内容来源**：`kclock_init`/`timer_irq` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节）。`local-build.md` 与 `starter.c` 注释里的骨架是**按公开定时器与 CP0 语义构造的等价示例**，非北航原文转录；`starter.c` **不参与平台判分**。",
    task: "**每步验证**：步骤 1 能说出时钟中断与时间片轮转的关系；步骤 2 写 `kclock_init`；步骤 3 配 lds 中断入口；步骤 4 写 `timer_irq` 调 `sched_yield`；步骤 5 用两个优先级不同的进程观察交替输出比例。",
    judge: {
      mode: "**Tier B：本地构建（`judgeMode: \"LOCAL_BUILD\"`），平台不自动判分**，完成规则 `SUBMIT`。原因：需要真实定时器硬件（8253/8254 或 CP0 Count/Compare）、`Status.IM4`/`Cause.IP4` 的中断时序与 MIPS 交叉工具链；沙箱是 x86-64 用户态单文件 C，无硬件定时器、无 CP0。用 C 写「模拟时钟中断」**不构成概念同构**，不编造代理题。",
      fill: "本地在 `kclock_init` 设频率并绑 4 号中断、在 lds 配中断入口、写 `timer_irq` 调 `sched_yield`。平台侧 `starter.c` 只是时钟中断链路速查注释，**不参与判分**。",
      input: "无。`cases.json` 为 `{\"cases\":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。",
      output: "无平台判题输出。真实产出是 `kclock_init`/`timer_irq` 代码、lds 改动，以及两个进程交替输出的 gxemul 串口日志。",
      host: "不适用于沙箱。主机端没有周期性硬件中断源，也没有 CP0 的 IM/IP 位段。",
      real: "完整验证途径是本地 MIPS 交叉编译 + gxemul，见下方「本地完整验证」与同目录 `local-build.md`。",
      source: "`kclock_init`/`timer_irq` 原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① 先完成 3.12 使 `0x80000080` 入口可用；② `kclock_init` 设频率并置 `Status.IM4`、`Status.IE`、清 `Status.EXL`；③ 分发用 `Cause & Status` 判 IP4 后跳 `timer_irq`；④ `timer_irq` 保存现场后调 `sched_yield`，返回前清中断并 `eret`；⑤ gxemul 里用 `ENV_CREATE_PRIORITY(user_A,2)` 与 `user_B,1`，观察输出交替比例约 2:1。",
    errs: [
      "只置 `Status.IM4` 忘了 `Status.IE`：总开关关着，中断永远不响应。",
      "`Status.EXL` 仍为 1 就期待中断：异常级未退出，中断被屏蔽。",
      "分发只看 `Cause.IP4` 不与 `Status.IM4` 相与：未使能的中断线也被当成时钟中断。",
      "`timer_irq` 不调 `sched_yield`：中断来了又回去，同一进程一直跑，看不出轮转。",
      "用软件忙等循环冒充时钟中断：没有外部周期性事件，抢占无从发生，这不是本实验的同构替代。",
    ],
  },
  /* ---------------- 4.1 (Tier B) ---------------- */
  {
    id: "1930156360117571585", ch: 4, no: "4.1", tier: "B",
    bg: "用户进程不能直接调内核函数——它跑在用户态，连内核页表都看不见。唯一的门是「访管指令」：用户态把调用号与参数放进约定寄存器，执行一条特权指令，硬件强制切到内核态并跳到异常入口。本实验写这扇门的用户侧一半：`user/syscall_wrap.S` 里的 `syscall` 封装。",
    kp: "**扩写（MIPS o32 公开约定）**：系统调用号放 `$v0`，前 4 个参数放 `$a0–$a3`，**第 5 个及以后参数走栈**（调用者预留栈空间）。`syscall` 指令触发 ExcCode=8(Sys) 异常，硬件置 `Status.EXL`、切内核栈与内核页表、把返回地址写进 `EPC`。内核处理完必须把返回值写回 Trapframe 的 `$v0` 并令 `EPC += 4`（跳过 `syscall` 本身），再 `eret`。用户侧从 `$v0` 取返回值。",
    src: [
      ["user/syscall_wrap.S", "本实验要编写的用户侧封装", "syscall、$v0、$a0-$a3、栈上第 5 参数"],
      ["lib/syscall_all.c", "内核侧处理（实验 4.2）", "handle_sys、sys_call_table、mysyscall"],
      ["include/unistd.h", "系统调用号定义", "SYS_mem_alloc、SYS_yield 等"],
      ["lib/traps.c", "异常现场与返回", "Trapframe、EPC、Status.EXL"],
    ],
    srcNote: "**内容来源**：`user/syscall_wrap.S` 的**原指导 Listing 未随工作区提供**（实测 `originalCode` 与 `answer` 均为 0 字节）。`local-build.md` 与 `starter.c` 注释里的 `msyscall` 骨架是**按 MIPS o32 公开语义构造的等价示例**，不是北航原文转录；`starter.c` **不参与平台判分**。",
    task: "**每步输入/输出/验证**：步骤 1 产出「能画出库函数 → syscall 封装 → 陷入内核 → 内核处理 → 返回」的完整流程」；步骤 2 产出 `syscall` 汇编封装；步骤 3 产出参数按 o32 正确传递的证据（`objdump` + 内核侧打印）；步骤 4 产出内核侧 `mysyscall`，验证方式是用户态调用后拿到正确返回值。",
    judge: {
      mode: "**Tier B：本地构建（`judgeMode: \"LOCAL_BUILD\"`），平台不自动判分**，完成规则为 `SUBMIT`。原因：`syscall` 是特权指令，触发真实 CP0 异常（ExcCode=8）并切换内核栈与内核页表；沙箱是 x86-64 用户态单文件 C，无 MIPS 交叉汇编器、无 CP0、无内核态。用 C 函数调用模拟「陷入内核」**不构成概念同构**，不编造代理题。",
      fill: "本地编写 `user/syscall_wrap.S` 的 `syscall` 封装（按 o32 搬参数、执行 `syscall`、从 `$v0` 取返回值），并实现内核侧 `mysyscall`。平台侧 `starter.c` 只是 o32 系统调用约定速查注释，**明确标注不参与判分**。",
      input: "无。`cases.json` 为 `{\"cases\":[]}`，**不生成 `judge-cases.json`**，平台不会对本实验发起判题。",
      output: "无平台判题输出。真实产出是 `syscall_wrap.S`、内核侧处理代码，以及用户态调用拿到正确返回值的 gxemul 日志。",
      host: "不适用于沙箱。主机端没有用户态/内核态之分，也没有访管指令与 CP0。",
      real: "完整验证途径是本地 MIPS 交叉编译 + gxemul，见下方「本地完整验证」与同目录 `local-build.md`。",
      source: "`user/syscall_wrap.S` 原文**未随工作区提供**，骨架为按公开语义构造的**等价**示例，非北航原文转录。",
    },
    local: "完整步骤见同目录 `local-build.md`。要点：① 先完成 3.12 使 `0x80000080` 入口与分发可用；② 写 `msyscall`：把 `$a0–$a3` 与栈上第 5 参数按序保存、`$v0` 存调用号、执行 `syscall`、返回后从 `$v0` 取值；③ 内核侧 `handle_sys` 从 Trapframe 取调用号查 `sys_call_table`，返回值写回 Trapframe 的 `$v0`，`EPC += 4`；④ `make` 后 `objdump -d` 确认 `syscall` 指令编码与寄存器搬运；⑤ gxemul 里用户进程调一次 `sys_yield`/`sys_mem_alloc`，确认返回值正确且能继续执行。",
    errs: [
      "调用号放进 `$a0` 而不是 `$v0`：内核查表查到错误调用号。",
      "第 5 个参数仍塞寄存器：o32 只有 `$a0–$a3` 四个参数寄存器，第 5 个必须走栈。",
      "内核返回时不把返回值写回 Trapframe 的 `$v0`：用户态拿到的是陷入前的旧值。",
      "内核返回时不令 `EPC += 4`：`eret` 回到 `syscall` 指令本身，无限重复陷入。",
      "用 `jal` 直接调内核函数冒充系统调用：没有态切换，用户态根本访问不到内核地址空间。",
    ],
  },
];

/* ================= 组装 ================= */
if (D.length !== 16) throw new Error(`D 条数 ${D.length} != 16`);
let total = 0;
const rows = [];
for (const d of D) {
  const dir = join(EXP_ROOT, CH[d.ch], "experiment-files", d.id);
  if (!existsSync(dir)) throw new Error(`目录不存在：${dir}`);
  const orig = ORIG.get(d.id);
  if (!orig) throw new Error(`current-content.tsv 中找不到 ${d.id}`);
  const o = splitOriginal(orig);
  if (!o.bg || !o.kp || !o.task) throw new Error(`${d.id} 原文小节解析不完整`);

  const title = TITLE.get(d.id).replace(/^实验\s*[\d.]+\s*/, "").trim();
  const cj = JSON.parse(readFileSync(join(dir, "cases.json"), "utf8"));
  const desc = cj.description || "";
  if (d.tier !== "B" && !desc) throw new Error(`${d.id} 非 Tier B 但 description 为空`);

  const j = d.judge;
  const bulletList = [
    `- 判题方式：${j.mode}`,
    `- 你需要填写：${j.fill}`,
    `- 判题输入：${j.input}`,
    `- 判题输出：${j.output}`,
  ];
  /* 操作码逐条文档行：既满足校验闸门 B3，也让学生不必从长句里自己拆操作码。
   * 源文本写作 "`X args`：说明"，这里拆成 "- `X` args：说明"。 */
  if (Array.isArray(j.ops)) {
    for (const [op, text] of j.ops) {
      const m = new RegExp("^`" + op + "(?:\\s+([^`]*))?`[：:]\\s*").exec(text);
      const args = m && m[1] ? " " + m[1].trim() : "";
      const rest = m ? text.slice(m[0].length) : text;
      bulletList.push(`- \`${op}\`${args}：${rest}`);
    }
  }
  bulletList.push(
    `- 宿主环境适配：${j.host}`,
    `- 与真实内核的关系：${j.real}`,
    `- 内容来源标注：${j.source}`,
  );
  const bullets = bulletList.join("\n");

  const contract = d.tier === "B" ? "" :
    `\n\n契约原文（逐字引自本目录 \`cases.json\` 的 \`description\`）：\n\n\`\`\`text\n${desc}\n\`\`\`\n`;

  const md = `# 实验 ${d.no} ${title}

## 实验背景

${o.bg}

${d.bg}

## 知识要点

${o.kp}

${d.kp}

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
${d.src.map((r) => `| \`${r[0]}\` | ${r[1]} | ${r[2]} |`).join("\n")}

${d.srcNote}

## 实验任务

${o.task}

${d.task}

## 本平台判题说明

${bullets}${contract}
## 本地完整验证

${d.local}

## 常见错误

${d.errs.map((e, i) => `${i + 1}. ${e}`).join("\n")}
`;

  const bytes = Buffer.byteLength(md, "utf8");
  writeFileSync(join(dir, "content.md"), md, "utf8");
  total += bytes;
  rows.push(`${d.no}\t${d.id}\tTier ${d.tier}\t${bytes}`);
  console.log(`${d.no.padEnd(5)} ${d.id}  Tier ${d.tier}  ${String(bytes).padStart(5)} B  ${bytes > 6000 ? "  <<< OVER 6000" : bytes < 2500 ? "  <<< UNDER 2500" : ""}`);
  if (process.argv.includes("--stats")) {
    const B = (s) => Buffer.byteLength(s, "utf8");
    const parts = {
      "orig.bg": B(o.bg), "new.bg": B(d.bg),
      "orig.kp": B(o.kp), "new.kp": B(d.kp),
      "src.table": B(d.src.map((r) => `| \`${r[0]}\` | ${r[1]} | ${r[2]} |`).join("\n")),
      "src.note": B(d.srcNote),
      "orig.task": B(o.task), "new.task": B(d.task),
      "judge.bullets": B(bullets), "judge.ops": B((j.ops || []).map(([op, t]) => `- \`${op}\` ${t}`).join("\n")),
      "contract.desc": B(desc), "local": B(d.local), "errs": B(d.errs.join("\n")),
    };
    const sum = Object.values(parts).reduce((a, b) => a + b, 0);
    console.log("      " + Object.entries(parts).map(([k, v]) => `${k}=${v}`).join(" ") + ` | 骨架=${bytes - sum}`);
  }
}
console.log(`\nwrote ${D.length} content.md, total ${total} bytes`);
