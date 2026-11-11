# 实验 4.5 基础系统调用函数`sys_mem_unmap`

## 实验背景

解除映射让目标虚拟地址不再指向原物理页，但共享页可能仍被其他映射引用。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `int sys_mem_unmap(int sysno, u_int envid, u_int va)` 函数，该函数用于解除某个进程地址空间虚拟内存和物理内存之间的映射关系。

### 知识要点

1. 系统调用的基本概念和作用。
2. 用户空间和内核空间的交互。
3. 内存映射和解除映射的基本原理。
4. 进程地址空间的管理。
5. MIPS 调用规范和参数传递。
6. 虚拟内存到物理内存的映射和解除映射操作。

### 实验任务

1. 理解系统调用 `sys_mem_unmap` 的功能和参数含义。
2. 在 `lib/syscall_all.c` 中编写 `sys_mem_unmap` 函数，实现内存解除映射功能。
3. 确保函数遵循 MIPS 调用规范，正确传递参数和返回结果。
4. 实现内存解除映射的逻辑，包括删除虚拟地址到物理地址的映射关系。
5. 测试 `sys_mem_unmap` 函数，验证其能够正确地解除进程地址空间中的内存映射。
6. 确保解除映射后，进程不再能访问之前映射的物理内存页。

## 知识要点

页表项、物理页引用计数和空闲链表是不同状态；重复解除未映射地址应是幂等操作。 本地观察的是模型中的页表与页账本，不是对真实 TLB 的硬件验证。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_mem_unmap` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

先校验地址与目标进程，再调用移除映射逻辑。以 Q、O、C 比较解除前后，并重复解除或先解除共享页的一方。

用 `cases.json` 中「正常解除映射：物理页回到空闲链表、页表项被清零、地址翻译失效，空闲页数恰好加一」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_mem_unmap(int sysno, uint32_t envid, uint32_t va)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.5 sys_mem_unmap(int sysno, u_int envid, u_int va)。驱动操作符：I=env_init；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；P idx va perm=page_alloc+page_insert 真实 backing 一页；C=空闲物理页数；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数，3 号调用即 K 3 envid va 0 0 0）；Q idx va=物理页基址（无映射 4294967295）；O idx va=页表项原始值（无页表 4294967295，页表存在但项被清零则打印 0）；G=只读当前 $v0。系统调用号：1=mem_alloc，2=mem_map，3=mem_unmap。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL。页账本：np=256/fm=8192 初始空闲 252 页；每次 env_alloc 占 1 页页目录；首次映射某 4MB 区间额外占 1 页页表；page_remove 递减 pp_ref 到 0 才回收，且总是把页表项清零。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `C`：输出 C 空闲物理页数。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `G`：输出 G 当前有符号返回值。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 直接清页表项而漏掉引用计数。
- 重复释放。
- 对非法地址或进程返回成功。

结合用例检查状态与资源变化。
