# 实验 4.14 实现进程状态设置系统调用 `sys_set_env_status`

## 实验背景

进程准备好之后才能进入可调度状态，阻塞时调度器必须跳过它。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `sys_set_env_status` 函数，该函数用于设置进程的状态，使其可以运行或处于其他状态。

### 知识要点

1. 进程状态的概念，包括 RUNNABLE 和其他可能的状态。
2. 用户空间和内核空间的交互。
3. 系统调用的实现和调用规范。
4. 进程控制块（`struct Env`）中的 `env_status` 字段。
5. 进程调度机制。

### 实验任务

1. 理解 `sys_set_env_status` 函数的功能和作用。
2. 在 `lib/syscall_all.c` 中编写 `sys_set_env_status` 函数，实现设置进程状态的功能。
   - 检查传入的进程状态是否有效。
   - 更新进程控制块中的 `env_status` 字段。
   - 如果状态为 RUNNABLE，将进程加入调度链表。
3. 确保 `sys_set_env_status` 函数遵循系统调用规范，正确处理错误情况。

## 知识要点

PCB 状态和就绪链表成员关系不同；同一节点重复插入会造成链表破坏或环。 这里“不摘链”的行为是本宿主调度模型的约定，完整内核应按实际实现验证。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_set_env_status` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

只接受 RUNNABLE 与 NOT_RUNNABLE，找到目标后设置状态；置 RUNNABLE 时仅在不在链表上才头插。用 K 5、T、L 与 K 11 验证。

用 `cases.json` 中「RUNNABLE 必须头插就绪链表、NOT_RUNNABLE 不得入链；对已在链上的进程重复置 RUNNABLE 必须幂等，不得把同一 PCB 挂两次」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_set_env_status(int sysno, uint32_t envid, uint32_t status)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.14 sys_set_env_status(int sysno, u_int envid, u_int status)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；S idx st=直接置 env_status（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE，仅构造夹具用）；R idx=不在就绪链表则头插（仅构造夹具用）；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数；s=5 即 SYS_set_env_status，a0=envid a1=status；s=11 即 SYS_yield）；T idx=打印 env_id/parent/status/pri/cr3；H=就绪链表头下标（空表 -1）；L=按序打印就绪链表全部下标（最多 NENV=64 个，学生写出环时会打印 64 个重复下标而不是死循环）；Y=env_run 记录日志（首行 Y 条数，随后每条 kind arg cur：0=lcontext arg=页目录物理地址，1=env_pop_tf arg=cp0_epc）；Z=清空日志；C=空闲物理页数。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL。envid=0 表示当前进程 curenv。env_alloc 出来的进程 status=NOT_RUNNABLE、pri=1、不在就绪链表上，envid 依次为 191(下标63)、318(下标62)、445(下标61)。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62]=1040384、envs[61]=1036288）。sched_yield 的时间片游标是 static 变量，每个用例都是独立进程运行。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `T` idx：输出 T idx id parent status pri cr3。
- `H`：输出 H 就绪链表头，空时 -1。
- `L`：输出 L 与就绪链表下标。
- `Y`：输出 Y 条数，再逐行输出 kind arg cur。
- `Z`：清空运行记录，输出 Z。
- `C`：输出 C 空闲物理页数。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 重复头插已有节点。
- 接受 FREE 或越界状态。
- 把阻塞误当成必须立即摘链。

结合用例检查状态与资源变化。
