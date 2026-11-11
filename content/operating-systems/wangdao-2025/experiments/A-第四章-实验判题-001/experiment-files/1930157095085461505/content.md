# 实验 4.7 实现进程间通信（IPC）机制

## 实验背景

IPC 以接收方的等待状态协调消息与可选的共享页，使进程之间能够同步。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现进程间通信（IPC）相关的系统调用函数，包括 `sys_ipc_recv` 和 `sys_ipc_can_send`，以支持用户进程之间的消息传递和同步。

### 知识要点

1. 进程间通信（IPC）的基本概念和重要性。
2. 消息传递和同步机制的实现方法。
3. 用户空间和内核空间的交互。
4. 系统调用的实现和调用规范。
5. 进程状态管理和调度。
6. 内存映射和解除映射的系统调用 `sys_mem_map` 和 `sys_mem_unmap`。

### 实验任务

1. 理解进程间通信（IPC）的基本原理和需求。
2. 在 `lib/syscall_all.c` 中编写 `sys_ipc_recv` 函数，实现进程接收消息的功能。
   - 设置 `env_ipc_receiving` 标志，表示进程准备接收消息。
   - 阻塞当前进程，直到有消息到达。
   - 处理接收到的消息并返回。
3. 在 `lib/syscall_all.c` 中编写 `sys_ipc_can_send` 函数，实现进程发送消息的功能。
   - 检查目标进程是否处于可接收状态。
   - 发送消息并更新目标进程的状态。
   - 返回发送结果。
4. 实现进程状态的管理和调度，确保进程在接收和发送消息时能够正确地被调度。

## 知识要点

接收先登记地址并阻塞；发送成功才写入发送者和值并唤醒，传页失败不能伪装成成功。 驱动用状态和日志模拟阻塞及调度，并不执行真实并发。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_ipc_recv`, `sys_ipc_can_send` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

分别填写接收和发送函数。用 K 8 登记接收、K 9 发送，观察 E、T、Q 和 Y；比较未等待、坏地址及映射失败场景。

用 `cases.json` 中「接收态检查不可省：向没在等消息的进程发送必须 -E_IPC_NOT_RECV 且不改其字段；随后 recv 阻塞让出、send 唤醒并带回发送方 envid」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_ipc_recv(int sysno, uint32_t dstva)`；`static int sys_ipc_can_send(int sysno, uint32_t envid, uint32_t value, uint32_t srcva, uint32_t perm)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.7 sys_ipc_recv(int sysno, u_int dstva) 与 sys_ipc_can_send(int sysno, u_int envid, u_int value, u_int srcva, u_int perm)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；S idx st=设 env_status（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE）；R idx=把 PCB 插入就绪链表头；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数；s=8 即 SYS_ipc_recv，a0=dstva；s=9 即 SYS_ipc_can_send，a0=envid a1=value a2=srcva a3=perm；s=11 即 SYS_yield）；E idx=打印 env_pgfault_handler/env_ipc_receiving/env_ipc_dstva/env_ipc_from/env_ipc_value 五个字段；T idx=打印 env_id/parent/status/pri/cr3；Y=打印 env_run 记录日志（首行 Y 条数，随后每条 kind arg cur：0=lcontext arg=页目录物理地址，1=env_pop_tf arg=cp0_epc）；Z=清空日志；Q idx va=物理页基址（无映射 4294967295）；O idx va=页表项原始值；P idx va perm=page_alloc+page_insert 打印 返回码/物理页基址；C=空闲物理页数；L=按序打印就绪链表下标。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM，-11=E_IPC_NOT_RECV。宿主物理页账本：np=256/fm=8192 初始空闲 252 页；每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62].cr3=1040384、envs[61].cr3=1036288）；首次映射某个 4MB 区间额外占 1 页作页表；共享已映射的页只递增 pp_ref，不再占新数据页。env_alloc 出来的进程 pri=1、status=NOT_RUNNABLE 且不在就绪链表里。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `E` idx：输出 E idx handler receiving dstva from value。
- `T` idx：输出 T idx id parent status pri cr3。
- `Y`：输出 Y 条数，再逐行输出 kind arg cur。
- `Z`：清空运行记录，输出 Z。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 未检查接收标志。
- 传页失败仍唤醒。
- 混淆发送者与接收者的地址空间。

结合用例检查状态与资源变化。
