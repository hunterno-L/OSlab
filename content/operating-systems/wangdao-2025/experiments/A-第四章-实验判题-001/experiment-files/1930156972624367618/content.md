# 实验 4.6 基础系统调用函数`sys_yield`

## 实验背景

进程通过系统调用主动让出处理器，调度器决定下一次运行的进程。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `void sys_yield(void)` 函数，该函数用于实现用户进程对CPU的放弃，以便操作系统调度器可以切换到其他进程。

### 知识要点

1. 系统调用的基本概念和作用。
2. 用户空间和内核空间的交互。
3. 进程调度的基本原理。
4. `sys_yield` 函数的功能和实现方法。
5. MIPS 调用规范。
6. 进程切换机制和现场保存。

### 实验任务

1. 理解系统调用 `sys_yield` 的功能和作用。
2. 在 `lib/syscall_all.c` 中编写 `sys_yield` 函数，实现进程让出CPU控制权的功能。
3. 确保函数遵循 MIPS 调用规范，正确实现系统调用接口。
4. 实现进程切换机制，包括保存当前进程的现场（如 `KERNEL_SP` 和 `TIMES_TACK`）。
5. 测试 `sys_yield` 函数，验证其能够正确地让出CPU控制权，并触发进程调度器进行进程切换。
6. 确保进程切换后，能够正确恢复进程的执行状态。

## 知识要点

让出不等于阻塞：可运行状态由调度策略使用，现场保存和恢复由切换路径处理。 现场保存与硬件恢复超出宿主日志的验证范围。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_yield` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

让 sys_yield 调用调度器；先用 S、R 构造可运行队列，再用 K 11 与 Y 查看选择记录，T 检查原进程状态。

用 `cases.json` 中「只有一个就绪进程：让出 CPU 仍必须走完整 env_run 四步切回自己，且不得把自己阻塞掉」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static void sys_yield(void)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.6 sys_yield(void)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；S idx st=设 env_status（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE）；R idx=把 PCB 插入就绪链表头；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数，s=11 即 SYS_yield）；Y=打印 env_run 的记录日志（首行 Y 记录条数，随后每条 kind arg cur：kind 0=lcontext arg=页目录物理地址，kind 1=env_pop_tf arg=cp0_epc，cur=调用时刻 curenv 下标）；Z=清空记录日志；T idx=打印 env_id/parent/status/pri/cr3；H=打印就绪链表头下标（空表打印 -1）；L=按序打印就绪链表全部下标；C=打印空闲物理页数；Q idx va=打印物理页基址（无映射打印 4294967295）；P idx va perm=page_alloc+page_insert 并打印 返回码/物理页基址。宿主物理页账本：np=256/fm=8192 时启动期映射占 4 页，初始空闲 252 页；每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62].cr3=1040384、envs[61].cr3=1036288）；首次映射某个 4MB 区间还要额外占 1 页作页表。env_alloc 出来的进程 pri=1、status=ENV_NOT_RUNNABLE，且不在就绪链表里，必须用 S+R 手工构造就绪场景。sched_yield 的时间片游标是函数内 static 变量，跨调用保持，故每个用例都是独立进程运行。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `Y`：输出 Y 条数，再逐行输出 kind arg cur。
- `Z`：清空运行记录，输出 Z。
- `T` idx：输出 T idx id parent status pri cr3。
- `H`：输出 H 就绪链表头，空时 -1。
- `L`：输出 L 与就绪链表下标。
- `C`：输出 C 空闲物理页数。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 让出时误改为 NOT_RUNNABLE。
- 绕过调度器直接选进程。
- 把日志替身当成真实上下文切换。
