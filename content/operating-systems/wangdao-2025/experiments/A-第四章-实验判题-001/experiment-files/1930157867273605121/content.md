# 实验 4.13 实现 `fork` 系统调用中的缺页处理函数 `pgfault`

## 实验背景

写时复制的页面发生写缺页时，处理程序需要提供独立物理页并保留原内容。

以下为工作区 `question.content` 原文：

### 任务描述

在 `user/fork.c` 中实现 `pgfault` 函数，该函数用于处理 `fork` 过程中可能发生的缺页中断，确保子进程能够正确地复制父进程的内存页。

### 知识要点

1. 缺页中断（Page Fault）的处理机制。
2. 写时复制（Copy-On-Write, COW）技术。
3. 页表项（Page Table Entry, PTE）的权限和状态。
4. 用户空间和内核空间的交互。
5. 内存分配和映射。
6. MIPS 异常处理和现场保存。

### 实验任务

1. 理解 `pgfault` 函数在 `fork` 过程中的作用和实现方法。
2. 在 `user/fork.c` 中编写 `pgfault` 函数，实现缺页中断的处理逻辑。
   - 判断页是否为写时复制的页面。
   - 为写时复制的页面分配新的物理内存页。
   - 更新页表项，将复制的内容映射到新分配的物理内存页。
   - 恢复被中断的进程执行。
3. 确保 `pgfault` 函数能够正确处理不同类型的缺页中断。

## 知识要点

缺页地址可能带页内偏移；先对齐页基址，再整页复制，最后移除临时映射并清除 COW。 内存拷贝、异常栈和触发过程是宿主替身，不能证明真实 MIPS 异常返回。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `user/fork.c` | 题目目标路径 | `pgfault` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

对 COW 页分配临时页、复制内容并以可写权限映射回原地址；非 COW 或分配失败则返回。用 X、Z、Q、O 比较内容和两侧映射。

用 `cases.json` 中「COW 写缺页必须真正复制：换上新的物理页、COW 位清除且保留可写(11->3)、整页内容逐字保留、临时页最终解除映射、旧页仍被子进程持有」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static void pgfault(uint32_t va)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.13 pgfault(u_int va)（user/fork.c，用户态写时复制缺页处理）。六步：非 COW 页直接返回；申请 UXSTACKTOP-BY2PG 临时页；整页 memcpy 原页到临时页；把临时页以 PTE_V|PTE_R（去掉 COW、保留可写）映射回原地址；解除临时映射。驱动操作符（CH4_USER_DRIVER + CH4_PGFAULT_DRIVER）：I=env_init 打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=同时设 curenv 与用户态全局 env；P idx va perm=page_alloc+page_insert 建真实映射，打印 返回码/物理页基址；W dst src n=user_memcpy 打印返回码（0 成功，-3 任一端未映射）；X va=调 pgfault(va) 后打印该地址页表项；Z va=打印 va 所在整页的字节校验和与前 8 字节（未映射打印 -1），用来断言内容被逐字复制；K s a0 a1 a2 a3 a4=填 KERNEL_SP 现场调 handle_sys 打印 $v0（恰好 6 个数；s=2 即 SYS_mem_map，a0=srcid a1=srcva a2=dstid a3=dstva a4=perm；s=3 即 SYS_mem_unmap，a0=envid a1=va）；O idx va=页表项原始值（无页表 4294967295）；Q idx va=物理页基址（无有效映射 4294967295）；C=空闲物理页数。本实验不装配 fork/duppage，故 V/D 操作符不可用。权限位：PTE_V=1，PTE_R=2（可写位），PTE_COW=8，PTE_LIBRARY=16；perm=11 是读写+COW，缺页处理后必须变成 3。关键地址：UXSTACKTOP=1056964608，临时页 va=1056960512(PDX=252)；va=1073750016(UTOP+8192) 经内核段映射到 PA 8192，即启动期第一张页表（bump 分配器从 fm=8192 起分配），其前四个页表项为 3/4099/8195/12291，故 W <userpage> 1073750016 4096 会搬入可辨认的非零内容：整页字节和 108、前 8 字节 3 0 0 0 3 16 0 0。注意 va=UTOP 本身映射到 PA 0，那是全零的保留页，不能用来做内容源。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=10 时 6 页；空闲链表 LIFO，头是最高号页；每次 env_alloc 占 1 页作页目录；首次进入某 4MB 区间额外占 1 页作页表；page_insert 到不同物理页会先 page_remove 旧页（pp_ref 归零即回收），到同一物理页只改权限不递增 pp_ref。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `W` dst src n：复制内存；输出 W 返回码。
- `X` va：调用 pgfault；输出 X 页表项。
- `Z` va：va 查询整页，输出 Z 页基址 校验和 前8字节；未映射输出 Z 页基址 -1。
- `K` s a0 a1 a2 a3 a4：现场分发；输出 K 有符号返回码。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `C`：输出 C 空闲物理页数。

K 返回码为有符号十进制；地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 映射回原址仍保留 COW。
- 用偏移地址代替页基址复制。
- 忘记解除临时映射。

结合用例检查状态与资源变化。
