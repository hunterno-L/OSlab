# 实验 4.10 实现 `fork` 系统调用中的 `duppage` 函数

## 实验背景

fork 复制地址空间时，可以共享只读或库页面，并为普通可写页建立写时复制关系。

以下为工作区 `question.content` 原文：

### 任务描述

在 `user/fork.c` 中实现 `duppage` 函数，该函数用于在 `fork` 系统调用中处理页表项的复制，以确保父子进程能够正确地共享或独立访问内存页。

### 知识要点

1. 进程的内存管理，特别是页表项（PTE）的使用。
2. 写时复制（Copy-On-Write, COW）机制。
3. 页表项的权限标志，如 PTE_COW 和 PTE_LIBRARY。
4. 用户空间和内核空间的交互。
5. 进程状态和调度。

### 实验任务

1. 理解 `duppage` 函数的作用和写时复制机制。
2. 在 `user/fork.c` 中编写 `duppage` 函数，实现对不同权限页的不同处理方式。
   - 对于只读页面，直接映射给子进程。
   - 对于共享页面，保持共享状态。
   - 对于写时复制页面，进行复制操作。
3. 确保 `duppage` 函数能够正确地处理所有类型的页表项。

## 知识要点

COW 是先共享、在后续写缺页时复制；共享页标记应优先于普通可写页处理。 平台中真正的内容复制由 4.13 的缺页路径模拟，本题不证明真实硬件写保护。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `user/fork.c` | 题目目标路径 | `duppage` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

根据 pn 求页地址和权限；无效页跳过，库页或只读页按原权限共享，可写页先映射子进程再重新映射父进程并加 COW。用 D、O、Q 检查两侧。

用 `cases.json` 中「三类页各自的处理：可写页父子双方都加 COW(3->11)、只读页原权限共享(1)、LIBRARY 共享页保持共享(19)、未映射页一个系统调用都不做」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static void duppage(uint32_t envid, uint32_t pn)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.10 duppage(u_int envid, u_int pn)（user/fork.c，用户态）。三类页分别处理：只读页原权限共享一次；PTE_LIBRARY 共享页原权限共享一次；可写页先映射给子进程再映射给自己，两次都带 perm|PTE_COW。驱动操作符（CH4_USER_DRIVER）：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=同时设 curenv 与用户态全局 env；S idx st=设 env_status；R idx=插入就绪链表头；P idx va perm=page_alloc+page_insert 建真实映射，打印 返回码/物理页基址；D envid pn=调 duppage(envid,pn) 后打印 父进程页表项/子进程页表项（子进程下标取 ENVX(envid)，无页表则 4294967295）；V=调 fork() 打印 返回值/子进程下标/env 下标；K s a0 a1 a2 a3 a4=填 KERNEL_SP 现场调 handle_sys 打印 $v0（必须恰好 6 个数；s=3 即 SYS_mem_unmap，a0=envid a1=va）；O idx va=页表项原始值（无页表 4294967295）；Q idx va=物理页基址（无有效映射 4294967295）；E idx=env_pgfault_handler/env_ipc_receiving/env_ipc_dstva/env_ipc_from/env_ipc_value；T idx=env_id/parent/status/pri/cr3；C=空闲物理页数；L=就绪链表下标；H=就绪链表头（空表 -1）。权限位：PTE_V=1，PTE_R=2（MOS 的可写位），PTE_COW=8，PTE_LIBRARY=16；perm=3 读写、1 只读、19 读写+共享、17 只读+共享、11 读写+COW。pn 与 va 换算：va=pn<<12，故 pn=1024->4194304、1025->4198400、1026->4202496、1027->4206592。UXSTACKTOP=1056964608，PGFAULT_ENTRY=4198400。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=8 时 4 页；空闲链表 LIFO，头是最高号页；每次 env_alloc 占 1 页作页目录；首次进入某 4MB 区间额外占 1 页作页表；page_insert 到【同一物理页】只改权限不递增 pp_ref，到不同物理页则先 page_remove 旧页。用户态实验的平台裁掉了 yield 与 IPC 两段（源码预算），故 K 11/K 8/K 9 返回 -E_NO_SYS(-10)。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `D` envid pn：调用 duppage；输出 D 父页表项 子页表项。
- `V`：调用 fork；输出 V 返回值 子下标 env下标。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `E` idx：输出 E idx handler receiving dstva from value。
- `T` idx：输出 T idx id parent status pri cr3。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。
- `H`：输出 H 就绪链表头，空时 -1。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 只给子页加 COW 而漏掉父页。
- 把库页当普通可写页。
- 把 pn 直接当字节地址。

结合用例检查状态与资源变化。
