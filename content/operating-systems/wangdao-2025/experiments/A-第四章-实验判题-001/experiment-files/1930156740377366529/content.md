# 实验 4.4 基础系统调用函数`sys_mem_map`

## 实验背景

两个进程可以通过各自页表指向同一物理页，以共享内容而不复制数据页。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/syscall_all.c` 中实现 `int sys_mem_map(int sysno, u_int srcid, u_int srcva, u_int dstid, u_int dstva, u_int perm)` 函数，该函数用于将源进程地址空间中的内存映射到目标进程的地址空间中。

### 知识要点

1. 系统调用的基本概念和作用。
2. 用户空间和内核空间的交互。
3. 内存映射的基本原理。
4. 进程地址空间的管理。
5. MIPS 调用规范和参数传递。
6. 虚拟内存到物理内存的映射。

### 实验任务

1. 理解系统调用 `sys_mem_map` 的功能和参数含义。
2. 在 `lib/syscall_all.c` 中编写 `sys_mem_map` 函数，实现内存映射功能。
3. 确保函数遵循 MIPS 调用规范，正确传递参数和返回结果。
4. 实现内存映射的逻辑，包括源地址到目标地址的映射关系建立。
5. 测试 `sys_mem_map` 函数，验证其能够正确地将源进程的内存映射到目标进程的地址空间中。
6. 确保内存映射后，两个进程能够共享同一物理内存页。

## 知识要点

源页须先存在；引用计数记录共享者，目标映射替换旧页时还涉及旧页回收。 本环境的 page_insert 负责引用计数，不能把它当作真实硬件访问测试。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/syscall_all.c` | 题目目标路径 | `sys_mem_map` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

检查源、目标地址和进程；查找源页后插入目标页表。用 Q 对比两侧物理页基址，用 C 观察最后一个引用解除后的回收。

用 `cases.json` 中「跨进程共享同一物理页：两边地址翻译结果必须相同，只额外消耗目标进程的一页页表」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int sys_mem_map(int sysno, uint32_t srcid, uint32_t srcva, uint32_t dstid, uint32_t dstva, uint32_t perm)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.4 sys_mem_map(int sysno, u_int srcid, u_int srcva, u_int dstid, u_int dstva, u_int perm)。驱动操作符：I=env_init；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；P idx va perm=page_alloc+page_insert 真实 backing 一页，打印 返回码/物理地址；C=空闲物理页数；K s a0 a1 a2 a3 a4=填好 KERNEL_SP 现场后调 handle_sys 并打印 $v0（必须恰好 6 个数，对 2 号调用即 K 2 srcid srcva dstid dstva perm）；Q idx va=物理页基址（无映射 4294967295）；O idx va=页表项原始值（无页表 4294967295）；G=只读当前 $v0。系统调用号：2=mem_map，3=mem_unmap。错误码按 int 解读：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM。页账本：np=256/fm=8192 初始空闲 252 页；每次 env_alloc 占 1 页页目录（envs[63].cr3=1044480、envs[62]=1040384）；首次映射某 4MB 区间额外占 1 页页表；page_insert 自己递增 pp_ref，page_remove 递减到 0 才回收，pp_ref==1 时 page_free 是空操作。

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

- 手动重复增加 pp_ref。
- 把页号当虚拟地址。
- 未映射源页仍返回成功。

结合用例检查状态与资源变化。
