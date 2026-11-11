# 实验 4.3 基础系统调用函数 `sys_mem_alloc`

## 实验背景

用户进程需要通过内核分配物理页并建立自己的虚拟地址映射，IPC 和 fork 依赖这一基础。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `int sys_mem_alloc(int sysno, u_int envid, u_int va, u_int perm)` 函数，该函数用于分配内存，是进程间通信（IPC）和 `fork` 等系统调用的基础。

### 知识要点

1. 系统调用的基本概念和作用。
2. 用户空间和内核空间的交互。
3. 内存分配的基本原理。
4. MIPS 调用规范和参数传递。
5. 虚拟内存到物理内存的映射。
6. `pmap.c` 中相关函数的使用。

### 实验任务

1. 理解系统调用 `sys_mem_alloc` 的功能和参数含义。
2. 在 `lib/syscall_all.c` 中编写 `sys_mem_alloc` 函数，实现内存分配功能。
3. 调用 `pmap.c` 中定义的相关函数，完成虚拟内存到物理内存的映射。
4. 确保函数遵循 MIPS 调用规范，正确传递参数和返回结果。
5. 测试 `sys_mem_alloc` 函数，验证其能够正确分配内存并映射到物理内存。
6. 确保 `sys_mem_alloc` 函数能够作为 IPC 和 `fork` 等系统调用的基础支持。

## 知识要点

页表项将虚拟地址与物理页及权限关联；页分配与插入任一步失败都要正确处理资源。 原题的 MIPS 调用规范和真实进程行为需在完整内核中验证。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_mem_alloc` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

先校验地址与进程，再分配页、插入映射并设置有效位；插入失败须归还新页。比较 K、Q、O 与 C，可检查重映射和内存耗尽。

用 `cases.json` 中「正常分配与同址重映射：perm 补 PTE_V、旧页必须被 page_remove 回收，空闲页数不多不少」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_mem_alloc(int sysno, uint32_t envid, uint32_t va, uint32_t perm)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.3 sys_mem_alloc(int sysno, u_int envid, u_int va, u_int perm)。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；C=打印空闲物理页数；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数）；Q idx va=打印物理页基址（无映射打印 4294967295）；O idx va=打印页表项原始值（无页表打印 4294967295）；T idx=打印 env_id/parent/status/pri/cr3。系统调用号：1=mem_alloc，3=mem_unmap。错误码以 uint32 补码打印后再按 int 解读：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM，-5=E_NO_FREE_ENV，-10=E_NO_SYS。宿主物理页账本：np=256/fm=8192 时启动期映射占 4 页，初始空闲 252 页；每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62].cr3=1040384、envs[61].cr3=1036288）；首次映射某个 4MB 区间还要额外占 1 页作页表。np=6/fm=8192 时初始空闲仅 2 页，np=8 时 4 页。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `C`：输出 C 空闲物理页数。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `T` idx：输出 T idx id parent status pri cr3。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 插入失败后不归还页。
- 忘记有效位。
- 越界地址仍分配页。

结合用例检查状态与资源变化。
