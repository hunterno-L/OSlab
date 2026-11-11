# 实验 4.1 本地完整验证（用户空间到内核空间的系统调用机制）

> **平台不自动判分。** 交付物是 `user/syscall_wrap.S` 的 `syscall` 封装汇编与内核侧 `mysyscall` 处理。
> 平台侧 `cases.json` 为空、不生成 `judge-cases.json`，学习单元活动完成规则为 `SUBMIT`。

## 0. 前置条件

- **必须先完成实验 3.12（异常处理）**：`syscall` 指令触发的就是 `0x80000080` 通用异常，
  `Cause.ExcCode = 8`（Sys）。没有分发代码，`syscall` 会直接跑飞。
- 已完成第三章（进程创建与运行），内核能创建并运行用户进程。
- MIPS 交叉工具链 + gxemul；MOS 源码树含 `user/syscall_wrap.S`、`user/syscall.c`、
  `lib/syscall_all.c`、`include/unistd.h`。

**内容来源说明：** 工作区 `question.id=1930156360117571585` 的 `originalCode` 与 `answer` 均为 0 字节，
`user/syscall_wrap.S` 的 Listing 原文未随工作区提供。下面的骨架依据工作区 `question.content`
的实验任务原文（「编写 `syscall` 函数，该函数负责执行特权指令 `syscall`，以陷入内核态」
「确保系统调用参数在内核空间中的正确传递和存储，遵循 MIPS 调用规范」
「实现 `mysyscall` 函数，该函数在内核中处理系统调用请求，并返回结果」）
与 MIPS o32 调用约定的公开语义给出，**不是北航原 Listing 的转录**。
函数名（`syscall` / `msyscall`）、参数个数与系统调用号以你手上的源码树为准。

## 1. 系统调用的完整流程（知识要点 1、2）

```
用户库函数（如 write / fork 的用户态封装）
  → user/syscall_wrap.S 的 syscall 封装：搬参数 + 执行 syscall 指令
  → CPU 硬件：Cause.ExcCode=8(Sys)，EPC=syscall 指令地址，Status.EXL=1，PC=0x80000080
  → 异常分发代码（实验 3.12）识别 ExcCode=8
  → handle_sys（实验 4.2）：从 Trapframe 取系统调用号与参数，查系统调用表
  → 具体 sys_* 内核函数（实验 4.3 起）
  → 返回值写回 Trapframe 的 $v0 槽位，EPC += 4
  → eret：清 EXL，回到用户态，用户代码从 $v0 读返回值
```

关键认识：**用户态与内核态之间唯一的合法通道就是这条异常路径**。
用户态不能直接调用内核函数（页表权限与特权级都不允许），也不能直接读写内核数据。

## 2. MIPS o32 参数传递约定（知识要点 3）

| 位置 | 用途 |
| --- | --- |
| `$v0` | 陷入前：系统调用号；返回后：内核返回值 |
| `$a0..$a3` | 前 4 个整型/指针参数 |
| 调用者栈 `$sp+16` 起 | 第 5 个及以后的参数（o32 要求调用者为前 4 个参数也预留 16 字节影子空间） |
| `$ra` | 返回地址，封装函数被 `jal` 调用 |
| `$s0..$s7` | callee-saved，封装函数若使用必须自己保存/恢复 |
| `$t0..$t9` | caller-saved，`syscall` 之后不可假定其值仍有效 |

栈帧必须 8 字节对齐（o32 要求）。

## 3. 编写 `syscall` 封装（实验任务 2，核心交付物）

骨架（**按公开语义构造的等价示例，非北航原文转录**）：

```asm
#include <asm/regdef.h>
#include <asm/asm.h>

    .set noreorder
    .text
/* int msyscall(int num, int a0, int a1, int a2, int a3, int a4);
 * C 侧调用时：num→$a0, a0→$a1, a1→$a2, a2→$a3, a3→$sp+16, a4→$sp+20
 * 封装要把它们搬到内核期望的位置：num→$v0, 参数→$a0..$a3, 第 5 个→$sp+16
 */
LEAF(msyscall)
    addiu   $sp, $sp, -24          /* 保存 $ra + 预留参数影子空间，8 字节对齐 */
    sw      $ra, 20($sp)

    move    $v0, $a0               /* 系统调用号 → $v0 */
    move    $a0, $a1               /* 参数 1 */
    move    $a1, $a2               /* 参数 2 */
    move    $a2, $a3               /* 参数 3 */
    lw      $t0, 36($sp)           /* 原第 5 个参数（调用者栈上的 a3） */
    sw      $t0, 16($sp)           /* 放到内核读取的位置 */

    syscall                        /* ← 特权指令：陷入内核态 */
    nop                            /* 延迟槽 */

    lw      $ra, 20($sp)
    addiu   $sp, $sp, 24
    jr      $ra                    /* 返回值已在 $v0 */
    nop
END(msyscall)
```

填写时最容易错的四点：
1. **搬参数的顺序**：`$a0→$v0` 必须先把号取走，再依次左移，否则会覆盖还没读的参数；
2. **第 5 个参数**：不在寄存器里，必须从调用者栈上取，偏移要算上自己新开的栈帧；
3. **`$ra` 必须保存**：`syscall` 本身不改 `$ra`，但若封装里再 `jal` 别的函数就会被覆盖；
4. **延迟槽**：`.set noreorder` 下 `syscall`/`jr` 后面都要显式填指令或 `nop`。

## 4. 内核侧处理与返回（实验任务 3、4）

- 异常分发把 `ExcCode=8` 路由到 `handle_sys`（实验 4.2 实现）；
- `handle_sys` 从 Trapframe 读 `$v0`（号）与 `$a0..$a3`（参数），查表调用对应 `sys_*`；
- **返回值必须写回 Trapframe 的 `$v0` 槽位**，而不是当前内核栈上的 `$v0`——
  用户态恢复的是 Trapframe 里的值；
- **`EPC += 4`**：`EPC` 指向 `syscall` 指令本身，不加 4 会反复陷入同一条指令，表现为死循环；
- 实验任务 4 的 `mysyscall` 是一个最小内核处理函数：取参数、做一件可观测的事（如打印）、返回一个确定值，
  用来验证整条链路通了。

## 5. 构建与验证

```bash
make
mips-linux-gnu-objdump -d gzemul/vmlinuz | grep -A20 '<msyscall>'
gxemul -E oldtestmips -d gzemul/vmlinuz      # 具体命令以实验指导为准
```

验证点：
1. 用户进程调用 `msyscall` 后，内核侧 `mysyscall` 被进入（串口有打印）→ 陷入成功；
2. 用户进程拿到的返回值等于内核写入的值 → 返回路径正确；
3. 传 4 个以内参数与传第 5 个参数分别验证，内核收到的值与用户传的一致 → 参数约定正确；
4. 调用返回后用户进程能继续正常执行后续指令 → `EPC += 4` 与现场恢复正确；
5. 若一调用就复位/跑飞：查 `0x80000080` 分发是否识别 ExcCode=8、栈是否 8 字节对齐、`$ra` 是否被破坏。

## 6. 提交物

- 补全后的 `user/syscall_wrap.S`；
- 内核侧 `mysyscall`（及必要的分发/表项改动）；
- 参数传递与返回值的本地验证记录（含第 5 个参数的用例）。

平台侧按 `SUBMIT` 记完成，不做自动判分。