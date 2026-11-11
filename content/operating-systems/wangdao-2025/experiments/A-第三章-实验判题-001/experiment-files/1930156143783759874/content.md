# 实验  3.14 进程调度实验

## 实验背景

**题目原文**

### 任务描述

在MIPS3000处理器中，实现一个基于时间片轮转算法的进程调度机制，通过时钟中断触发进程调度，确保进程能够被正确调度。

时钟滴答触发时间片轮转。

## 知识要点

### 知识要点

1. **进程调度算法**：理解时间片轮转算法的基本原理和实现方法。
2. **进程状态管理**：了解如何管理进程的状态，特别是就绪状态（ENV_RUNNABLE）。
3. **链表操作**：掌握如何使用链表存储和管理就绪状态的进程。
4. **函数调用**：理解如何在时钟中断服务程序中调用进程调度函数（sched_yield）。
5. **优先级设置**：了解如何设置进程的优先级，以及如何根据优先级进行进程调度。

**理解与应用**：原文要求两个链表；平台用单链表及 env_pri 时间片建模，不能代表双队列全部行为；env.c 由平台提供。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/sched.c` | 题面所涉代码位置 | `static void sched_yield(void)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **补充sched_yield函数**：根据注释，完成sched_yield函数的编写，该函数用于在时钟中断发生时进行进程调度。
2. **修改env.c文件**：根据调度方法对env.c中的部分函数进行修改，确保进程能够被正确调度。
3. **实现进程状态管理**：编写代码管理进程的状态，特别是就绪状态的进程。
4. **实现链表操作**：编写代码使用两个链表存储所有就绪状态的进程，并实现进程的插入和切换操作。

**实施与验证**：保留游标与剩余片，到期或不可运行时从下一项环形寻找，最多 NENV 步；优先级 0 按 1 滴答。用 N 推进、Y 检查选择。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static void sched_yield(void)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/sched.c 的 sched_yield()（北航 3.14：MIPS3000 上基于时间片轮转、由时钟中断触发的进程调度）。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..96），随后 n 个操作：I=平台 env_init()（重复打印 I SKIP）；B=镜像缓冲填成 seg_binary[i]=i&0xFF；G=清空段表；S va size perm=追加一段（最多8段，va<1073741824、size<=16384、perm 0..4095），打印「S 段数」；E entry=设 elf_entry；M epc sp sr badv cause=平台把五个值写进 TIMESTACK 时钟中断现场并原样打印；A x=ENV_CREATE(x)（x∈A/B/C，优先级1）；P x pri=ENV_CREATE_PRIORITY(x,pri)（pri 0..65535）；J=平台 env_alloc(&e,0)（不装镜像、不入就绪链表），打印「J 返回码 下标」；U idx st=平台把 envs[idx].env_status 设为 st（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE），只改状态不动链表；D idx=平台 env_free(&envs[idx])（从就绪链表摘下、置FREE、清env_id、放回空闲链表，不回收物理页）；N=一个时钟滴答，调用学生填写的 sched_yield()；V=curenv 下标（NULL 为 -1）；T idx=打印「T idx env_id parent status pri cp0_epc sp」；Y=打印事件日志「Y 记录总数」随后每条一行「种类 参数 curenv下标」，种类0=lcontext（参数=页目录物理地址）、1=env_pop_tf（参数=现场cp0_epc）；env_run 每次调用追加两条，故记录总数=2×实际切换次数，每条的 curenv下标 即该滴答选中的进程；容量96条超出只计数；Z=清空事件日志；H=就绪链表头下标（空 -1）；L=就绪链表顺序（头插故为创建逆序）；F=空闲PCB数；C=空闲物理页数。idx<64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、ENV_FREE=0、USTACKTOP=1073741824、ENV_USER_SR=268439564、NENV=64。env_init 头插建空闲链表故分配顺序 63,62,61...；env_id=(生成号<<7)|下标，生成号从1起递增；单段非空镜像进程耗5页、空镜像3页、单纯 env_alloc 1页。算法契约：静态游标 cur 与剩余时间片 left 跨调用保持；cur为NULL/left为0/cur越界/cur非RUNNABLE 时从【cur的下一个】起环形查找第一个 RUNNABLE（最多NENV步，走到NULL绕回表头），找不到直接 return 不调用 env_run；找到则 left=max(1,env_pri)；然后 left-- 再 env_run(cur)。宿主等价：单条就绪链表 + env_pri 即一轮连续运行的滴答数（题面提到的「两个链表」是真实 MOS 部分版本的高低优先级双队列，此处按等价公开语义构造并已诚实标注）；env_run/lcontext/env_pop_tf 为平台件，切换行为由 Y 日志如实记录。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `B` → B
- `G` → G
- `S` va size perm → S 段数
- `E` entry → E entry
- `M` epc sp sr badv cause → M epc sp sr badv cause
- `A` x → A x
- `P` x pri → P x pri
- `J` → J 返回码 下标
- `U` idx st → U idx st
- `D` idx → D idx
- `N` → N
- `V` → V 下标
- `T` idx → T idx env_id parent status pri cp0_epc sp
- `Y` → Y 总数，然后每条记录一行（见契约）
- `Z` → Z
- `H` → H 下标
- `L` → L 下标...
- `F` → F 空闲数
- `C` → C 空闲页数

**契约片段**：「S 段数」；「J 返回码 下标」；「T idx env_id parent status pri cp0_epc sp」；「Y 记录总数」；「种类 参数 curenv下标」；「两个链表」。

## 常见错误

1. 每次从表头查找会导致饥饿。
2. 游标或剩余片未跨调用保存。
3. 不跳过非 RUNNABLE；空队列仍切换；忽略优先级 0。
