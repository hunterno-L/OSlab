# 实验 3.12 本地完整验证（异常处理）

> **平台不自动判分。** 交付物是链接器脚本的异常入口设置 + 异常分发汇编。
> 平台侧 `cases.json` 为空、不生成 `judge-cases.json`，学习单元活动完成规则为 `SUBMIT`。

## 0. 前置条件

- 已完成第二章（页表与地址映射）与实验 3.1–3.10（进程控制块、进程运行与切换）。
- MIPS 交叉工具链 + gxemul；MOS 源码树含 `tools/scse0_3.lds`、异常入口汇编（如 `kernel/genex.S` / `init/entry.S`，以源码树为准）。

**内容来源说明：** 工作区 `question.id=1930155905954140162` 的 `originalCode` 与 `answer` 均为 0 字节，
链接器脚本与异常分发代码的 Listing 原文未随工作区提供。下面的片段依据工作区 `question.content`
的实验任务原文（「在链接器脚本中设置异常处理程序的入口地址为 0x80000080」「编写异常分发代码」
「Cause Register 的 bit2、bit6、bit8、bit15」）与 MIPS32 体系结构手册的公开语义给出，
**不是北航原 Listing 的转录**。文件名、宏名与已有标号以你手上的源码树为准。

## 1. 为什么入口必须是 `0x80000080`（知识要点 2）

MIPS32 在 `Status.BEV = 0` 时，**通用异常向量固定**在 `0x80000080`（TLB Refill 在 `0x80000000`，
Reset 在 `0xBFC00000`）。CPU 发生异常时硬件直接把 PC 置为该地址，**不查任何软件表**。
因此必须让链接器把异常分发代码**恰好放在这个物理地址**，否则异常发生时会执行到无关指令。

这就是本实验为什么是「改 lds」而不是「改 C」：地址是链接期决定的。

## 2. 修改链接器脚本（实验任务 1、3）

在 `SECTIONS` 中为异常入口单独开一个输出段，用 `.` 显式定位（**等价示例，非原文转录**）：

```ld
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text) }

    /* 通用异常向量：必须落在 0x80000080 */
    . = 0x80000080;
    .exc.handler : { *(.exc.handler) }

    /* 其余段继续顺序放置 */
    .data : { *(.data) }
    .bss  : { *(.bss) }
    _end = .;
}
```

要点：
- 汇编侧要把分发代码放进同名输入段（`.section .exc.handler,"ax"`），否则 `*(.exc.handler)` 收不到东西；
- `. = 0x80000080;` 之前若已有内容越过该地址，链接器会报「location counter backwards」——
  说明 `.text` 太大，需要调整段顺序或把异常入口段前置；
- `0x80000080` 落在 `kseg0`（`0x80000000..0x9FFFFFFF`），是**不经 TLB 的固定映射**，
  物理地址 = 虚拟地址 − `0x80000000` = `0x80`。异常早期页表可能还不可用，所以必须用 kseg0。

## 3. 编写异常分发代码（实验任务 2）

分发逻辑：保存现场 → 读 `Cause` → 按 `ExcCode` 跳转 → 恢复现场 → `eret`。

`Cause` 位段（知识要点 1）：
- `bit 6..2` = `ExcCode`（5 位异常原因编码）。取法：`srl $t0, $Cause, 2` 后 `andi $t0, $t0, 0x1f`。
- `bit 15..8` = `IP[7:0]`（中断挂起）。`bit 8` = IP0，`bit 15` = IP7；时钟中断是 IP4（`bit 12`，见实验 3.13）。
- `ExcCode == 0`（Int）时还要再看 `IP` 与 `Status.IM` 才能确定是哪一号中断。
- `bit 31` = BD：异常发生在分支延迟槽时置位，`EPC` 指向分支指令本身，返回时需要特殊处理。

骨架（**等价示例，非原文转录**）：

```asm
    .set noreorder
    .section .exc.handler,"ax"
    .globl handle_exc
handle_exc:
    /* 1. 保存现场到 Trapframe（内核栈 / TIMESTACK，见实验 3.10） */
    mfc0  k0, CP0_CAUSE
    mfc0  k1, CP0_EPC
    /* ... 保存 $at/$v0..$t9/$sp/$ra 等 ... */

    /* 2. 取 ExcCode = (Cause >> 2) & 0x1f */
    srl   k0, k0, 2
    andi  k0, k0, 0x1f

    /* 3. 按 ExcCode 分发 */
    beq   k0, $zero, do_irq        /* 0 = Int 中断 */
    nop
    li    t0, 8
    beq   k0, t0, do_syscall       /* 8 = Sys（实验 4.1/4.2） */
    nop
    li    t0, 2
    beq   k0, t0, do_tlb_refill    /* 2 = TLBL */
    nop
    /* ... 其余原因 ... */
    j     do_unknown
    nop

do_irq:
    /* 再按 Cause.IP 与 Status.IM 细分中断号（时钟中断 → timer_irq） */
    j     handle_irq
    nop

    /* 4. 恢复现场后 eret：eret 会清 Status.EXL 并把 PC 置为 EPC */
```

注意：
- `eret` **不是** `jr $ra`：它同时清 `EXL` 并跳回 `EPC`，用错会永远停在异常级；
- `k0/k1`（`$26/$27`）是内核保留寄存器，异常早期只能用它们，否则会破坏用户现场；
- `.set noreorder` 下每条分支/跳转的延迟槽都要显式填指令或 `nop`。

## 4. 构建与验证

```bash
make
mips-linux-gnu-readelf -S gzemul/vmlinuz | grep -i exc   # 确认异常段地址
mips-linux-gnu-objdump -d gzemul/vmlinuz | grep -A40 '^80000080'
gxemul -E oldtestmips -d gzemul/vmlinuz                  # 具体命令以实验指导为准
```

验证点：
1. `readelf -S` 里异常入口段的地址**恰好是 `0x80000080`**；
2. `objdump` 反汇编 `0x80000080` 处就是你的分发代码第一条指令；
3. 主动制造一次异常（例如实验 4.1 的 `syscall`，或访问未映射地址）后，
   内核能进入分发代码并按 `ExcCode` 走到正确分支，而不是跑飞或复位；
4. `eret` 之后能正确回到被中断的代码继续执行（`EPC` 与 BD 位处理正确）。

## 5. 提交物

- 修改后的链接器脚本；
- 异常分发汇编；
- `readelf -S` / `objdump` 的地址核对记录 + 一次真实异常的处理记录。

平台侧按 `SUBMIT` 记完成，不做自动判分。