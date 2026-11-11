# 实验 4.15 实现 `fork` 系统调用

## 实验背景

fork 的父分支需要把已映射用户页和异常处理资源配置给新子进程，最后才允许其运行。

以下为工作区 `question.content` 原文：

### 任务描述

在 `user/fork.c` 中完成 `fork` 函数中父进程部分的实现，包括地址空间的遍历、`duppage`、分配子进程的异常处理栈、设置子进程的处理函数、设置子进程的运行状态，并返回子进程的 `envid`。

### 知识要点

1. `fork` 系统调用的基本概念和作用。
2. 进程的地址空间和页表管理。
3. 写时复制（Copy-On-Write, COW）技术。
4. 异常处理机制和异常处理栈。
5. 进程状态的设置和管理。
6. 用户空间和内核空间的交互。

### 实验任务

1. 理解 `fork` 函数中父进程部分的逻辑和流程。
2. 在 `user/fork.c` 中实现父进程部分的逻辑，包括：
   - 地址空间的遍历和 `duppage` 处理。
   - 分配子进程的异常处理栈。
   - 设置子进程的处理函数。
   - 设置子进程的运行状态。
   - 返回子进程的 `envid`。
3. 确保 `fork` 函数能够正确地创建子进程并初始化其状态。

## 知识要点

普通可写页通过 COW 共享；异常栈独立分配，登记 handler 后再设置可运行状态。 本参考模型对部分中间调用失败不回滚；AC 不能证明资源耗尽时真实 fork 的完整错误恢复。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `user/fork.c` | 题目目标路径 | `fork` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

遍历用户页并对有效项调用 duppage；给子进程分配异常栈、登记入口、设 RUNNABLE，返回子 envid。用 V、O、E、T、L 核对目标与状态。

用 `cases.json` 中「异常栈/handler/RUNNABLE 三件事都必须落在子进程身上：父进程的 handler、栈页映射、状态与就绪链表都不得被改」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int fork(void)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.15 fork(void)（user/fork.c，侧重父进程分支）。父进程必须：遍历 pn=0..VPN(USTACKTOP)-1 只对带 PTE_V 的页调 duppage(envid,pn)；syscall_mem_alloc(envid, UXSTACKTOP-BY2PG, PTE_V|PTE_R) 申请子进程异常栈；syscall_set_pgfault_handler(envid, PGFAULT_ENTRY, UXSTACKTOP) 登记处理函数；syscall_set_env_status(envid, ENV_RUNNABLE) 唤醒子进程；返回子进程 envid。驱动操作符（CH4_USER_DRIVER + CH4_FORK_DRIVER + CH4_DUPPAGE_DRIVER）：I=env_init 打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=同时设 curenv 与用户态全局 env；P idx va perm=page_alloc+page_insert 建真实映射，打印 返回码/物理页基址；V=调 fork() 打印 返回值/子进程下标(ENVX)/env 下标；Y=子进程分支夹具（令 syscall_env_alloc 返回 0，env 先置 NULL 作哨兵）打印 返回值/env 下标；W dst src n=user_memcpy 打印返回码；T idx=env_id/parent/status/pri/cr3；E idx=env_pgfault_handler/env_ipc_receiving/env_ipc_dstva/env_ipc_from/env_ipc_value；Q idx va=物理页基址（无有效映射 4294967295）；O idx va=页表项原始值（无页表 4294967295）；C=空闲物理页数；L=就绪链表下标；H=就绪链表头（空表 -1）。本实验不装配 pgfault，故 X/Z 不可用；duppage 由平台提供（4.10 已考）。权限位：PTE_V=1，PTE_R=2（可写位），PTE_COW=8，PTE_LIBRARY=16；duppage 后可写页 3->11、只读页 1 与共享页 19 原样。关键地址：USTACKTOP=UTOP=1073741824，其栈页 va=1073737728(PDX=255,PTX=1023)；UXSTACKTOP=1056964608，异常栈页 va=1056960512(PDX=252)；PGFAULT_ENTRY=4198400；ENV_RUNNABLE=2，ENV_NOT_RUNNABLE=1。错误码：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=8 时 4 页；空闲链表 LIFO，头是最高号页；每次 env_alloc 占 1 页作页目录；首次进入某 4MB 区间额外占 1 页作页表；set_pgfault_handler 内部会再申请一页栈并回收前一次 mem_alloc 的栈页（同址重映射到不同物理页 -> page_remove 旧页，pp_ref 归零即回收），故一次空 fork 净耗 3 页。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `V`：调用 fork；输出 V 返回值 子下标 env下标。
- `Y`：模拟子分支，输出 Y 返回值 env下标。
- `W` dst src n：复制内存；输出 W 返回码。
- `T` idx：输出 T idx id parent status pri cr3。
- `E` idx：输出 E idx handler receiving dstva from value。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。
- `H`：输出 H 就绪链表头，空时 -1。

地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 对 envid=0 操作父进程而非新子进程。
- 漏掉高地址页。
- 初始化前唤醒子进程。
