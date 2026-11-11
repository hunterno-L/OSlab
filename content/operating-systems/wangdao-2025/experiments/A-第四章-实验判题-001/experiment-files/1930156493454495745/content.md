# 实验 4.2 实现内核的系统调用处理机制

## 实验背景

系统调用将用户请求转换为内核操作：入口从保存的现场读取编号和参数，分发后把结果交还调用方。

以下为工作区 `question.content` 原文：

### 任务描述

编写 `handle_sys` 函数，完成内核部分的系统调用处理机制，使得用户空间的系统调用能够正确地在内核中执行并返回结果。

### 知识要点

1. 用户空间和内核空间的交互机制。
2. 系统调用的流程，包括用户空间的库函数调用、`syscall` 陷入内核态、内核空间的系统调用函数执行。
3. MIPS 调用规范，特别是参数传递和栈帧管理。
4. `syscall` 函数的作用，即从用户空间陷入内核态。
5. `handle_sys` 函数的实现，包括参数解析和调用相应的内核函数。
6. `sys_call_table` 的使用，将系统调用号映射到对应的内核函数。

### 实验任务

1. 理解并实现系统调用的完整流程，包括用户空间到内核空间的转换。
2. 编写 `handle_sys` 函数，该函数负责解析系统调用号，并调用相应的内核函数。
3. 实现 `syscall` 函数，该函数负责执行特权指令 `syscall`，以陷入内核态。
4. 确保系统调用参数在内核空间中的正确传递和存储，遵循 MIPS 调用规范。
5. 测试用户空间的系统调用，验证其能够正确地调用内核函数并返回结果。
6. 完成 `handle_sys` 函数，使得内核部分的系统调用机制可以正常工作。 

## 知识要点

调用号是表索引，空槽与越界必须先拒绝；寄存器现场承担参数和返回值的约定。 题面还要求用户态 syscall 封装；该部分不由本填写区验证。



## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| 题面未指定路径 | 题目目标路径 | `handle_sys` |
| `starter.c` | 宿主环境与驱动 | 学生填写区、main |
| `cases.json` / `judge-cases.json` | 输入与输出 | stdin / stdout |

**内容来源**：上文为题面原文。原指导 Listing 未随工作区提供，以下为按公开语义构造的等价示例；具体行为以本目录驱动和用例为准。

## 实验任务

读取 regs[2] 与 regs[4..8]，检查表项后调用并将结果写回 regs[2]。用 K 检验合法号与 0、6、7、越界号；结合 Q、T 等观察副作用。

用 `cases.json` 中「三个内存类系统调用正常分发：返回值必须写回 $v0，映射与解除映射真实生效」的输入运行程序，对照 `judge-cases.json` 输出检查状态。

## 本平台判题说明

Tier A 主机端等价 harness：在 `starter.c` 学生填写区实现 `static void handle_sys(void)`。

stdin 首行 `npage freemem`，次行为操作数，随后是操作及参数。

判题契约原文：

> 驱动内核系统调用入口 handle_sys()（北航 4.2：解析 $v0 里的系统调用号，经 sys_call_table[] 分发到对应内核函数，并把返回值写回 $v0）。stdin 首行 npage freemem（1..256 / 0..65536），次行操作数 n（0..64），随后 n 个操作：I=平台 env_init()（重复打印 I SKIP），打印「I 空闲PCB数」；A parent=平台 env_alloc(&e,parent)，打印「A 返回码 下标 env_id」；U idx=设 curenv=&envs[idx]，打印「U idx」；S idx st=直接改 env_status（st 0..2），打印「S idx st」；R idx=把 envs[idx] 头插就绪链表（判重），打印「R idx」；P idx va perm=平台 page_alloc+page_insert 给 envs[idx] 造一页映射（va<1073741824），打印「P 返回码 物理地址」；K sysno a0 a1 a2 a3 a4=把 6 个值填进 KERNEL_SP 现场的 regs[2]/regs[4..8]，调用学生填写的 handle_sys()，打印「K (int)regs[2]」；G=打印当前 regs[2]；Q idx va=envs[idx] 中 va 的物理地址（无映射 4294967295）；T idx=打印「T idx env_id parent status pri cr3」；E idx=打印「E idx pgfault_handler ipc_receiving ipc_dstva ipc_from ipc_value」；H=就绪链表头下标（空 -1）；L=就绪链表顺序；F=空闲PCB链表顺序；C=空闲物理页数；Y=打印 env_run 记录「Y 总数」后逐行「kind arg curenv下标」（kind 0=lcontext 传页目录物理地址，1=env_pop_tf 传 cp0_epc）；Z=清空记录。错误码以 uint32 补码打印：-E_NO_SYS=-10→4294967286，-E_BAD_ENV=-2→4294967294，-E_INVAL=-3→4294967293，-E_NO_MEM=-4→4294967292。系统调用号：1=mem_alloc 2=mem_map 3=mem_unmap 4=env_alloc 5=set_env_status 8=ipc_recv 9=ipc_can_send 10=set_pgfault_handler 11=yield；0/6/7 为空槽位，SYS_MAX=12。

操作（输出以空格分隔）：

- `I`：初始化；重复输出 I SKIP。
- `A` parent：分配 PCB。
- `U` idx：设置当前进程。
- `S` idx st：设置状态。
- `R` idx：判重后头插。
- `P` idx va perm：构造映射。
- `K` sysno a0 a1 a2 a3 a4：分发调用，返回值有符号。
- `G`：读取返回值。
- `Q` idx va：查物理地址，无映射为 4294967295。
- `T` idx：查看 PCB。
- `E` idx：查看 IPC 与处理函数字段。
- `H`：就绪链表头。
- `L`：就绪链表顺序。
- `F`：空闲 PCB 顺序。
- `C`：空闲物理页数。
- `Y`：运行记录。
- `Z`：清空记录。

输出字段再核对：「I 空闲PCB数」、「A 返回码 下标 env_id」、「U idx」、「S idx st」、「R idx」、「P 返回码 物理地址」、「K (int)regs[2]」、「T idx env_id parent status pri cr3」、「E idx pgfault_handler ipc_receiving ipc_dstva ipc_from ipc_value」、「Y 总数」、「kind arg curenv下标」。
K/G 输出有符号负数；契约的补码是位模式。

宿主以 `uint32_t` 表示地址与寄存器，使用页和进程模型，不执行真实 MIPS 指令或硬件 TLB；完整验证仍需本地 MIPS 交叉编译与运行 MOS。

## 常见错误

- 把返回值写入 regs[4]。
- 未检查空槽。
- 丢失第五个参数。

结合用例检查状态与资源变化。
