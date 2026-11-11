# 实验 4.8 进程创建系统调用 `sys_env_alloc`

## 实验背景

创建进程需要分配控制块和页目录，并让子进程从复制的现场继续运行。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `sys_env_alloc` 函数，该函数用于创建一个新的进程环境，并为 `fork` 系统调用提供支持。

### 知识要点

1. 进程创建的基本概念和方法。
2. 用户空间和内核空间的交互。
3. 系统调用的实现和调用规范。
4. 进程环境的创建和管理。
5. 进程状态和调度。
6. 内存分配和管理。

### 实验任务

1. 理解 `sys_env_alloc` 函数的功能和作用，以及它在 `fork` 系统调用中的重要性。
2. 在 `lib/syscall_all.c` 中编写 `sys_env_alloc` 函数，实现创建新进程环境的功能。
   - 分配内存以存储新进程的环境信息。
   - 初始化新进程的环境，包括寄存器、栈等。
   - 设置新进程的状态，确保它不会立即被调度。
3. 确保 `sys_env_alloc` 函数遵循系统调用规范，正确处理错误情况。
4. 实现 `fork` 系统调用，利用 `sys_env_alloc` 创建子进程。
   - 在父进程中调用 `sys_env_alloc` 创建子进程环境。
   - 根据 `sys_env_alloc` 的返回值决定 `fork` 的返回值。
   - 确保父进程和子进程能够正确地恢复执行。

## 知识要点

父子返回值不同：子现场的 $v0 为零，父侧得到子进程标识；新子进程初始化后尚不可运行。 完整 fork 的用户态分支另见 4.9 和 4.15。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_env_alloc` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

由当前进程 ID 调用 env_alloc，复制现场并把子现场返回值清零，保持 NOT_RUNNABLE。用 N、J、T 和 K 4 检查复制、状态与资源耗尽。

用 `cases.json` 中「fork 第一步：返回带生成号的子进程 envid、父进程填 parent、现场整体复制且子进程 $v0 归零、状态 NOT_RUNNABLE 且不挂就绪链表」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_env_alloc(int sysno)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.8 sys_env_alloc(int sysno)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id（失败时打印 -1 与 0）；U idx=设 curenv；S idx st=设 env_status（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE）；R idx=把 PCB 插入就绪链表头；N idx epc v0=给 envs[idx].env_tf 写可辨认的 cp0_epc 与 regs[2]，用来检验现场整体复制与子进程 $v0 归零；J idx=打印 env_tf 的 cp0_epc/regs[2]($v0)/regs[29]($sp)/cp0_status；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数；s=4 即 SYS_env_alloc，s=11 即 SYS_yield）；T idx=打印 env_id/parent/status/pri/cr3；Y=env_run 记录日志（首行 Y 条数，随后每条 kind arg cur：0=lcontext arg=页目录物理地址，1=env_pop_tf arg=cp0_epc）；Z=清空日志；H=就绪链表头下标（空表 -1）；L=按序打印就绪链表下标；F=按序打印空闲 PCB 链表下标；C=空闲物理页数。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM，-5=E_NO_FREE_ENV。宿主账本：np=256/fm=8192 初始空闲 252 页，np=6 时仅 2 页；每次 env_alloc 占 1 页作页目录，按空闲链表头顺序发出（envs[63].cr3=1044480、envs[62]=1040384、envs[61]=1036288，np=6 时 envs[63].cr3=20480、envs[62]=16384）；env_alloc 初始化 regs[29]=USTACKTOP=1073741824、cp0_status=ENV_USER_SR=268439564、pri=1、status=NOT_RUNNABLE；mkenvid 第 g 次分配给下标 k 的进程 envid=(g<<7)|k，故 64 次分配后 envs[0].env_id=8192、cr3=786432。操作条数上限 MAXOPS=96，足够先申请满 64 个 PCB 再触发 -E_NO_FREE_ENV。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `N` idx epc v0：设置现场；输出 N idx epc v0。
- `J` idx：输出 J idx epc v0 sp status。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `T` idx：输出 T idx id parent status pri cr3。
- `Y`：输出 Y 条数，再逐行输出 kind arg cur。
- `Z`：清空运行记录，输出 Z。
- `H`：输出 H 就绪链表头，空时 -1。
- `L`：输出 L 与就绪链表下标。
- `F`：输出 F 与空闲PCB下标。
- `C`：输出 C 空闲物理页数。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 过早将子进程置为 RUNNABLE。
- 未清零子现场 $v0。
- 分配失败仍使用控制块。
