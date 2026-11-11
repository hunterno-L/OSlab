# 实验  3.10 进程运行与切换

## 实验背景

**题目原文**

### 任务描述

本实验的目标是理解和实现进程的运行和环境切换机制。具体任务包括实现 `env_run` 函数，该函数负责保存当前进程的状态，切换到新进程的地址空间，并恢复新进程的状态以运行该进程。

上下文切换连接中断现场、当前进程、地址空间与恢复后的用户执行。

## 知识要点

### 知识要点

1. **进程上下文切换**：
   - 理解进程上下文切换的概念，包括保存和恢复进程状态。
2. **Trapframe**：
   - 理解 Trapframe 的作用，它用于保存和恢复进程的寄存器状态。
3. **环境切换**：
   - 理解如何使用 `lcontext` 函数切换到新进程的地址空间。
4. **进程状态**：
   - 理解进程的不同状态，以及如何在进程间进行切换。
5. **MIPS 架构**：
   - 理解 MIPS 架构中的寄存器和指令，特别是与进程控制相关的指令。

**理解与应用**：平台用日志替代 MIPS 切换指令；TIMESTACK 是宿主现场对象，日志记录 lcontext 与 env_pop_tf 的顺序和参数。

## 关联源码

| 文件 | 作用 | 关注符号 |
| --- | --- | --- |
| `lib/env.c` | 题面所涉代码位置 | `static void env_run(struct Env *e)` |

**内容来源**：原文来自 question.content；原指导 Listing 未随工作区提供，平台按题面及公开语义构造等价示例。

## 实验任务

### 实验任务

1. **实现 `env_run` 函数**：
   - 实现保存当前进程状态的步骤。
   - 实现设置 `curenv` 为新进程的步骤。
   - 实现使用 `lcontext` 切换到新进程地址空间的步骤。
   - 实现使用 `env_pop_tf` 恢复环境寄存器并进入用户模式的步骤。
2. **理解 Trapframe 和 TIMESTACK**：
   - 理解 Trapframe 的结构和作用，以及它如何与 TIMESTACK 相关联。
   - 分析代码，找出 TIMESTACK 的定义和作用。

**实施与验证**：已有 curenv 时先保存完整现场，再设置新 curenv，传 env_cr3 给 lcontext，最后用新进程 Trapframe 调 env_pop_tf。以 T、O、W、Y 观察。

## 本平台判题说明

- 判题方式：Tier A 单文件 C 主机端等价 harness，填写 `starter.c` 中成对标记包围的学生填写区：`static void env_run(struct Env *e)`。
- 宿主以固定宽度整数及数组模拟地址与内存；完整验证仍需本地 MIPS 环境。
- 以下逐字引用 `cases.json.description`；stdin 按空白分词，输出的行内空格敏感。

> 驱动 lib/env.c 的 env_run()（北航 3.10：保存当前进程状态→设置 curenv→lcontext 切换地址空间→env_pop_tf 恢复并进入用户态）。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..96），随后 n 个操作：I=平台 env_init()（重复打印 I SKIP）；B=镜像缓冲填成 seg_binary[i]=i&0xFF；G=清空段表；S va size perm=追加一段（最多8段，va<1073741824、size<=16384、perm 0..4095），打印「S 段数」；E entry=设 elf_entry；A x=ENV_CREATE(x)（x∈A/B/C，优先级1）；P x pri=ENV_CREATE_PRIORITY(x,pri)（pri 0..65535）；J=平台 env_alloc(&e,0)（不装镜像、不入就绪链表），打印「J 返回码 下标」；K idx=平台直接把 curenv 指向 envs[idx]（不改状态、不记日志）；M epc sp sr badv cause=平台把五个值写进 TIMESTACK 现场；X idx=调用学生填写的 env_run(&envs[idx])；V=curenv 下标（NULL 为 -1）；T idx=打印「T idx env_id parent status pri cp0_epc sp」；O idx=打印「O idx cp0_status cp0_badvaddr cp0_cause」；W idx=打印「W idx env_cr3 页目录物理地址」（非法页目录时后者 4294967295）；Y=打印事件日志「Y 记录总数」随后每条一行「种类 参数 curenv下标」，种类0=lcontext（参数=收到的地址）、1=env_pop_tf（参数=现场里的 cp0_epc，现场指针非法时 4294967295），curenv下标 -1 表示 NULL/越界，日志容量96条超出只计数；Z=清空事件日志；H=就绪链表头下标（空 -1）；L=就绪链表顺序（头插故为创建逆序）；F=空闲PCB数；C=空闲物理页数；Q idx va=物理地址（无映射 4294967295）。idx<64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、USTACKTOP=1073741824、ENV_USER_SR=268439564、栈页映射在 1073737728。env_init 头插建空闲链表故分配顺序 63,62,61...；env_id=(生成号<<7)|下标，生成号从1起递增；单段非空镜像进程耗5页、空镜像进程耗3页、单纯 env_alloc 占1页。宿主等价：lcontext/env_pop_tf 是记录函数（真实内核里是 MIPS 汇编），四步顺序、lcontext 收到物理地址还是虚拟地址、env_pop_tf 收到新进程现场还是 TIMESTACK、Step2 是否先于 Step3/4，全部由 Y 日志如实判定。

**操作与参数 → 输出**：

- `I` → I 数量 / I SKIP
- `B` → B
- `G` → G
- `S` va size perm → S 段数
- `E` entry → E entry
- `A` x → A x
- `P` x pri → P x pri
- `J` → J 返回码 下标
- `K` idx → K idx
- `M` epc sp sr badv cause → M epc sp sr badv cause
- `X` idx → X idx
- `V` → V 下标
- `T` idx → T idx env_id parent status pri cp0_epc sp
- `O` idx → O idx cp0_status cp0_badvaddr cp0_cause
- `W` idx → W idx env_cr3 页目录物理地址
- `Y` → Y 总数，然后每条记录一行（见契约）
- `Z` → Z
- `H` → H 下标
- `L` → L 下标...
- `F` → F 空闲数
- `C` → C 空闲页数
- `Q` idx va → Q 物理地址

**契约片段**：「S 段数」；「J 返回码 下标」；「T idx env_id parent status pri cp0_epc sp」；「O idx cp0_status cp0_badvaddr cp0_cause」；「W idx env_cr3 页目录物理地址」；「Y 记录总数」；「种类 参数 curenv下标」。

## 常见错误

1. 先切换 curenv 再保存，会覆盖新进程而丢失旧现场。
2. 首次运行时无条件访问空 curenv。
3. 把虚拟指针传给 lcontext，或向 env_pop_tf 传旧现场。
