# 实验 4.9 实现 `fork` 系统调用中的 `sys_env_alloc`

## 实验背景

fork 让父、子从同一调用点继续，但各自读取到的返回值和当前进程指针不同。

以下为工作区 `question.content` 原文：

### 任务描述

在 `user/fork.c` 中实现 `fork` 函数，包括调用 `sys_env_alloc` 创建子进程环境，并正确设置子进程的执行逻辑。

### 知识要点

1. 进程控制块（`struct Env`）的概念和作用。
2. `fork` 系统调用的功能和实现方法。
3. `sys_env_alloc` 函数的作用和实现。
4. 进程的创建和复制。
5. 父子进程的返回值处理。
6. 用户空间和内核空间的交互。

### 实验任务

1. 理解 `fork` 系统调用的功能和 `sys_env_alloc` 的作用。
2. 在 `user/fork.c` 中实现 `fork` 函数，调用 `sys_env_alloc` 创建子进程环境。
   - 调用 `sys_env_alloc` 获取子进程的 `envid`。
   - 根据 `sys_env_alloc` 的返回值决定 `fork` 的返回值。
3. 实现子进程的执行逻辑。
   - 设置子进程的 `env` 指针指向自身的进程控制块。
   - 确保子进程能够从 `fork` 函数中返回并开始执行。
4. 确保父进程和子进程能够正确地恢复执行。

## 知识要点

系统调用返回零表示子分支；进程标识的下标部分可以定位 PCB，但零不是子进程自己的标识。 Y 是模拟子分支，不能证明真实寄存器恢复或两进程并发执行。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `user/fork.c` | 题目目标路径 | `fork` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

填写整个 fork；子分支用当前 envid 更新 env 并返回零，父分支完成页复制和子进程初始化。用 Y 哨兵夹具检查 env，V 观察父分支。

用 `cases.json` 中「子进程分支：env 哨兵必须被改写成自己的 PCB 且返回 0，全程不创建进程、不申请物理页、不动就绪链表；换 curenv 后必须跟着换」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static int fork(void)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 实验 4.9 fork(void)（user/fork.c，侧重子进程分支）。七步：syscall_env_alloc 取 envid；envid==0 走子进程分支（env 指向自己的 PCB 后 return 0）；否则父进程分支遍历地址空间调 duppage、为子进程申请异常栈、登记 PGFAULT_ENTRY、置 ENV_RUNNABLE、返回子进程 envid。驱动操作符（CH4_USER_DRIVER + CH4_FORK_DRIVER + CH4_DUPPAGE_DRIVER）：I=env_init 打印空闲 PCB 数；A parent=env_alloc 打印 返回码/下标/env_id；U idx=同时设 curenv 与用户态全局 env；P idx va perm=page_alloc+page_insert 建真实映射，打印 返回码/物理页基址；V=调 fork() 打印 返回值/子进程下标(ENVX)/env 下标；Y=【子进程分支夹具】令 syscall_env_alloc 返回 0 模拟子进程带 $v0=0 恢复执行，先把 env 置 NULL 作哨兵再调 fork()，打印 返回值/env 下标（正确实现必须返回 0 且 env 下标等于 curenv 下标；漏写 env 赋值留下 -1，误用返回的 envid 会得到 0）；W dst src n=user_memcpy 打印返回码；T idx=env_id/parent/status/pri/cr3；E idx=env_pgfault_handler/env_ipc_receiving/env_ipc_dstva/env_ipc_from/env_ipc_value；Q idx va=物理页基址（无有效映射 4294967295）；O idx va=页表项原始值（无页表 4294967295）；C=空闲物理页数；L=就绪链表下标；H=就绪链表头（空表 -1）。本实验不装配 pgfault，故 X/Z 操作符不可用。关键常量：ENVX(envid)=envid&63，envid=(生成号<<7)|下标，故 ENVX 恒等于 PCB 下标；USTACKTOP=UTOP=1073741824，VPN(USTACKTOP)=262144；UXSTACKTOP=1056964608，异常栈页 va=1056960512；PGFAULT_ENTRY=4198400；ENV_RUNNABLE=2，ENV_NOT_RUNNABLE=1。错误码：-2=E_BAD_ENV，-3=E_INVAL，-4=E_NO_MEM，-5=E_NO_FREE_ENV。宿主物理页账本：np=256/fm=8192 初始空闲 252 页，np=5 时 1 页；空闲链表 LIFO，头是最高号页；每次 env_alloc 占 1 页作页目录；首次进入某 4MB 区间额外占 1 页作页表；fork 里 set_pgfault_handler 会再申请一页栈并把前一次 mem_alloc 的栈页回收（pp_ref 归零），故一次空 fork 净耗 3 页（子进程页目录 + 异常栈页表 + 栈页）。

操作（输出以空格分隔）：

- `I`：初始化；输出 I 空闲PCB数，重复为 I SKIP。
- `A` parent：输出 A 返回码 下标 env_id。
- `U` idx：设置当前进程；输出 U idx。
- `P` idx va perm：建映射；输出 P 返回码 物理页基址。
- `V`：调用 fork；输出 V 返回值 子下标 env下标。
- `Y`：模拟子分支，输出 Y 返回值 env下标。
- `W` dst src n：复制内存；输出 W 返回码。
- `T` idx：输出 T idx id parent status pri cr3。
- `E` idx：输出 E idx handler receiving dstva from value。
- `Q` idx va：输出 Q 物理页基址；无映射为 4294967295。
- `O` idx va：输出 O 页表项；无页表为 4294967295。
- `C`：输出 C 空闲物理页数。
- `L`：输出 L 与就绪链表下标。
- `H`：输出 H 就绪链表头，空时 -1。

地址与页表项为无符号十进制。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 用返回的零直接索引 envs。
- 子分支漏设 env。
- 在父分支操作当前进程而非子进程。
