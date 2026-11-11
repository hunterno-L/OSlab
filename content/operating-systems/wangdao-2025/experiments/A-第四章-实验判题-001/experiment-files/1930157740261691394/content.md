# 实验 4.12 实现设置缺页处理函数的系统调用 `sys_set_pgfault_handler`

## 实验背景

进程注册缺页处理入口，并准备异常栈，使后续用户态处理有独立执行空间。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `sys_set_pgfault_handler` 函数，该函数允许用户进程设置自己的缺页处理函数。

### 知识要点

1. 缺页中断处理机制。
2. 用户空间和内核空间的交互。
3. 进程控制块（`struct Env`）中的 `env_pgfault_handler` 字段。
4. 内存分配和异常处理。
5. MIPS 调用规范。

### 实验任务

1. 理解 `sys_set_pgfault_handler` 函数的功能和作用。
2. 在 `lib/syscall_all.c` 中编写 `sys_set_pgfault_handler` 函数，实现设置缺页处理函数的功能。
   - 检查传入的处理函数指针是否有效。
   - 调用 `syscall_mem_alloc` 为处理函数分配内存。
   - 设置 `env_pgfault_handler` 字段为传入的处理函数。
3. 确保 `sys_set_pgfault_handler` 函数遵循 MIPS 调用规范，正确处理错误情况。
4. 实现内核部分的系统调用，以便在内核中设置和调用用户指定的缺页处理函数。

## 知识要点

入口地址、栈顶和 PCB 字段是不同对象；栈向低地址使用，分配页应位于栈顶下方。 原文提及 syscall_mem_alloc；此宿主填写区的内核等价路径调用 sys_mem_alloc，分配的是异常栈页。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_set_pgfault_handler` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

校验 func 和 xstacktop，定位进程，登记处理入口并为 xstacktop-BY2PG 分配页。通过 K 10、E、Q、O 检查目标、权限与失败结果。

用 `cases.json` 中「正常设置：handler 写入 PCB，异常栈那一页必须建在 xstacktop-BY2PG 且可写（PTE_V|PTE_R），栈顶地址本身不映射」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_set_pgfault_handler(int sysno, uint32_t envid, uint32_t func, uint32_t xstacktop)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.12 sys_set_pgfault_handler(int sysno, u_int envid, u_int func, u_int xstacktop)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；S idx st=设 env_status；R idx=插入就绪链表头；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数；s=10 即 SYS_set_pgfault_handler，a0=envid a1=func a2=xstacktop）；E idx=打印 env_pgfault_handler/env_ipc_receiving/env_ipc_dstva/env_ipc_from/env_ipc_value；T idx=打印 env_id/parent/status/pri/cr3；Q idx va=物理页基址（无映射 4294967295）；O idx va=页表项原始值（无页表 4294967295）；C=空闲物理页数；L=就绪链表下标；H=就绪链表头（空表 -1）。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM。关键常量：UTOP=1073741824，BY2PG=4096，PTE_V=1，PTE_R=2（MOS 的 PTE_R 即可写位），故异常栈页表项低 12 位应为 3。常用地址：func=4198400(0x00401000)、xstacktop=1056964608(UXSTACKTOP=0x3F000000) 对应栈页 va=1056960512(0x3EFFF000, PDX=252)；xstacktop=1073741824(UTOP) 对应栈页 va=1073737728(PDX=255)。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=7 时 3 页；每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62]=1040384；np=7 时 envs[63].cr3=24576）；首次映射某个 4MB 区间额外占 1 页作页表；同址重映射到不同物理页时旧页 pp_ref 归零被回收。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `E` idx：输出 E idx handler receiving dstva from value。
- `T` idx：输出 T idx id parent status pri cr3。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。
- `H`：输出 H 就绪链表头，空时 -1。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 把栈页分配在 xstacktop。
- 忽略非法入口或栈顶。
- 把字段写到当前进程而非目标进程。
