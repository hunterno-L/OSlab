# 实验 1.4 本地完整验证（MIPS 汇编与 C 语言的交互）

> **平台不自动判分。** 本实验的交付物是 `boot/start.S` 中的 MIPS 汇编与一份汇编对照分析，
> 必须在本地 MIPS 交叉编译环境完成。平台侧 `cases.json` 为空、不生成 `judge-cases.json`，
> 学习单元活动的完成规则是 `SUBMIT`（提交即完成），不是 `PASS`。

## 0. 前置条件

- MIPS 交叉工具链（北航实验使用的 `mips-linux-gnu-gcc` / `mips-linux-gnu-ld`，
  或实验指导 `include.mk` 中 `CROSS_COMPILE` 指向的同一套工具链）。
- gxemul（`gxemul -E oldtestmips -d disk.img` 一类命令，具体以实验指导为准）。
- MOS 内核源码树（含 `boot/start.S`、`init/main.c`、`include.mk`、顶层 Makefile）。

**内容来源说明：** 工作区 `question.id=1930154331844431873` 的 `originalCode` 与 `answer` 均为 0 字节，
`boot/start.S` 的 Listing 原文未随工作区提供。下面的步骤依据工作区 `question.content` 的实验任务原文
（任务 4「完成 `boot/start.S` 文件中缺失的部分：设置栈指针并跳转到 `main` 函数」）
与 MIPS o32 调用约定的公开语义给出，**不是北航原 Listing 的转录**。
具体缺哪几行、栈顶符号名（如 `bootstacktop` / `KSTACKTOP`）以你手上的源码树为准。

## 1. 分析 C 代码（实验任务 1）

阅读实验给出的 `fib` 函数与 `main` 函数：
- `fib` 是递归函数，关注每次调用如何保存返回地址 `$ra` 与帧指针 `$fp`；
- `main` 含循环结构，关注循环条件如何用 `slti` + `bnez`/`beq` 表达。

同目录 `starter.c` 提供了可本地编译的等价 C 分析对象（**不参与判分**）。

## 2. 编译 C 到 MIPS 汇编（实验任务 2）

```bash
mips-linux-gnu-gcc -S -O0 -o fib-O0.s fib.c
mips-linux-gnu-gcc -S -O2 -o fib-O2.s fib.c
```

对照两份汇编，回答：
- 参数从哪个寄存器传入（o32 下前 4 个整型参数走 `$a0..$a3`）？
- 返回值从哪个寄存器回传（`$v0`）？
- 递归调用前哪些寄存器被压栈、栈帧大小是多少？
- `-O2` 下循环/递归发生了什么变化（尾调用、寄存器分配、循环展开）？

## 3. 分析汇编（实验任务 3）

在生成的 `.s` 中定位并解释：
- 函数序言/尾声：`addiu $sp,$sp,-N` / `sw $ra,N-4($sp)` / `lw $ra,...` / `jr $ra` / `addiu $sp,$sp,N`；
- 条件判断：`slti`、`bnez`、`beq` 与延迟槽（delay slot）里的指令；
- 循环：回跳标签与计数器更新。

## 4. 补全 `boot/start.S`（实验任务 4，核心交付物）

启动代码要在进入 C 之前把栈准备好。按 MIPS o32 约定：
1. 把栈顶地址装入 `$sp`（栈向低地址增长，`$sp` 指向可用栈空间的最高地址）；
2. 按需要清 `$fp`/`$ra`，保证栈回溯有确定终点；
3. 跳转到 C 入口 `main`（`jal main` 或 `j main`，取决于是否期望返回）；
4. 注意跳转指令的**延迟槽**：`j`/`jal` 后面那条指令会先执行，通常填 `nop`。

骨架（**按公开语义构造的等价示例，非北航原文转录**，符号名以源码树为准）：

```asm
    .set noreorder
    .globl _start
_start:
    /* 1. 设置栈指针：把栈顶符号地址装入 $sp */
    la      $sp, bootstacktop
    /* 2. 清空帧指针，作为栈回溯终点 */
    move    $fp, $zero
    /* 3. 跳转到 C 入口 */
    jal     main
    nop                     /* 延迟槽 */
1:  j       1b              /* main 不应返回；返回则原地停住 */
    nop
```

## 5. 构建与运行验证

```bash
make                      # 顶层 Makefile，交叉编译路径见 include.mk
gxemul -E oldtestmips -d gzemul/vmlinuz   # 具体命令以实验指导为准
```

验证点：
- 内核能越过启动汇编进入 `main`，串口/控制台有输出 → 说明 `$sp` 设置正确；
- 若一进入 C 就跑飞或立刻异常，优先怀疑 `$sp` 指向了非法/未映射地址，或栈顶符号取错；
- 用 `mips-linux-gnu-objdump -d` 反汇编最终镜像，确认 `_start` 处的指令与你填写的一致。

## 6. 提交物

- 补全后的 `boot/start.S`；
- `fib` 的 `-O0`/`-O2` 汇编对照分析（寄存器约定、栈帧、条件跳转、延迟槽）；
- 本地构建与 gxemul 运行的输出记录。

平台侧按 `SUBMIT` 记完成，不做自动判分。