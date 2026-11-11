# 实验 4.11 实现缺页中断处理函数 `page_fault_handler`

## 实验背景

访问已登记但尚无物理页的地址时，缺页处理可以按策略分配页面并补齐映射。

以下为工作区 `question.content` 原文：

### 任务描述

在 `lib/traps.c` 中完成 `page_fault_handler` 函数的实现，该函数用于处理进程访问不存在的物理页面时引发的缺页中断。

### 知识要点

1. 缺页中断（Page Fault）的基本概念和处理流程。
2. 内存管理，包括物理内存和虚拟内存的映射。
3. TLB（Translation Lookaside Buffer）的作用和缺页中断的关系。
4. 异常处理机制和异常处理栈（Exception Stack Frame）。
5. 用户空间和内核空间的交互。
6. 页表项（Page Table Entry, PTE）的更新和物理内存的分配。

### 实验任务

1. 理解缺页中断的触发条件和处理流程。
2. 在 `lib/traps.c` 中编写 `page_fault_handler` 函数，实现缺页中断的处理逻辑。
   - 检查引发中断的虚拟地址是否合法。
   - 在页表中为缺失的页面分配物理内存。
   - 更新页表项，建立虚拟地址到物理地址的映射。
   - 处理可能的异常情况，如内存不足。
3. 确保 `page_fault_handler` 函数能够正确地恢复被中断的进程执行。

## 知识要点

异常现场给出出错地址；页表项的地址字段与低位权限含义不同，补页时不能破坏权限。 宿主使用传入的 Trapframe 指针并模拟按需分页；原题所述完整异常恢复及真实 MOS 的用户处理函数转发并未由此验证。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `lib/traps.c` | 题目目标路径 | `page_fault_handler` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

宿主模型只处理 PTE_V 已置位且物理地址字段为零的条目；用 B 构造该状态、M 触发、O/Q/C 检查权限和分配。无效项、已有页和耗尽场景也要检查。

用 `cases.json` 中「基本按需分页：登记未分配的洞被补上物理页，低 12 位权限原样保留（读写页 3、读写+COW 页 11、只读页 1），页内偏移的重复缺页不得再分配」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static void page_fault_handler(struct Trapframe *tf)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.11 page_fault_handler(struct Trapframe *tf)（宿主签名适配：真实 MOS 从 TIMESTACK 取现场）。按需分页语义：把「PTE_V 已置位但 PTE_ADDR 为 0」的页表项补上物理页，原样保留低 12 位权限。驱动操作符：I=env_init 并打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=设 curenv；S idx st=设 env_status；R idx=插入就绪链表头；P idx va perm=page_alloc+page_insert 建一条真实映射，打印 返回码/物理页基址；B idx va perm=缺页夹具，pgdir_walk(create=1) 后把页表项低 12 位直接写成 perm（PTE_ADDR 留 0，造出「已登记未分配」的洞），必要时新建页表；M va=把 va 写进 TIMESTACK 的 cp0_badvaddr 后调 page_fault_handler(TIMESTACK)，再打印 curenv 在 va 处的页表项原始值（curenv 为空则 4294967295）；O idx va=页表项原始值（无页表 4294967295）；Q idx va=物理页基址（无有效映射 4294967295）；T idx=env_id/parent/status/pri/cr3；J idx=env_tf 的 cp0_epc/$v0/$sp/cp0_status；C=空闲物理页数；L=就绪链表下标；H=就绪链表头（空表 -1）。权限位：PTE_V=1，PTE_R=2（MOS 的 PTE_R 即可写位），PTE_COW=8，PTE_LIBRARY=16；故 perm=3 是读写页、perm=1 是只读页、perm=11 是读写+COW、perm=9 是只读+COW。UTOP=1073741824，BY2PG=4096。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=7 时 3 页；每次 env_alloc 占 1 页作页目录（envs[63].cr3=1044480、envs[62]=1040384；np=7 时 envs[63].cr3=24576）；首次进入某个 4MB 区间（B 或 P）额外占 1 页作页表；空闲链表 LIFO，头是最高号页。内核段只有 PDX(UTOP)=256 与 PDX(UENVS)=509 两条页目录项有效，故 M 1073741824 打印 3、M 4294967295 打印 4294967295。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `S` idx st：设置状态；输出 S idx st。
- `R` idx：判重后头插；输出 R idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `B` idx va perm：构造缺页；输出 B 返回码。
- `M` va：触发处理；输出 M 页表项。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `T` idx：输出 T idx id parent status pri cr3。
- `J` idx：输出 J idx epc v0 sp status。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。
- `H`：输出 H 就绪链表头，空时 -1。

输出字段再核对：「PTE_V 已置位但 PTE_ADDR 为 0」、「已登记未分配」。
地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 固定写入读写权限而覆盖原权限。
- 无效项也分配。
- 内存耗尽仍更新页表项。

结合用例检查状态与资源变化。
