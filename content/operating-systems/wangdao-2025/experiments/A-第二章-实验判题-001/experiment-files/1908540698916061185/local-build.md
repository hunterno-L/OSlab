# 实验 2.8 本地完整验证（TLB 管理 · `tlb_out`）

> **平台不自动判分。** 交付物是 `mm/tlb_asm.S` 中 `tlb_out` 的两行特权指令填写与一份 `nop` 解释。
> 平台侧 `cases.json` 为空、不生成 `judge-cases.json`，学习单元活动完成规则为 `SUBMIT`。

## 0. 前置条件

- 已完成实验 2.5–2.7（页表创建、地址映射、页表项插入与移除），内核能建立并修改页表。
- MIPS 交叉工具链 + gxemul；MOS 源码树含 `mm/tlb_asm.S`、`include/asm/cp0regdef.h`、`include/asm/regdef.h`。

**内容来源说明：** 工作区 `question.id=1908540698916061185` 的 `originalCode` 与 `answer` 均为 0 字节，
`mm/tlb_asm.S` 的 Listing 原文未随工作区提供。下面的骨架与解释依据工作区 `question.content` 的实验任务原文
（「在空出的两行位置填入 `tlbp` 或 `tlbwi` 指令」「解释为何第 10 行处指令后有 4 条 `nop` 指令」）
与 MIPS32 体系结构手册的公开语义给出，**不是北航原 Listing 的转录**。行号与宏名以你手上的源码树为准。

## 1. 理解 TLB 与未命中流程（实验任务 1）

- TLB 是页表的硬件缓存。MIPS 软件管理 TLB：TLB 未命中触发 TLB Refill 异常，由内核查页表后写回 TLB。
- `tlb_out` 的语义是**失效**：把某个虚拟页号对应的 TLB 项清掉，使下次访问重新触发 refill，
  从而看到 `page_insert`/`page_remove` 刚刚改过的页表项。这就是 `tlb_invalidate()` 的底层实现。
- 相关 CP0 寄存器：`EntryHi`（VPN + ASID）、`EntryLo0/EntryLo1`（两个物理页帧 + 权限位）、
  `Index`（TLB 槽位号，最高位为 1 表示探测未命中）、`PageMask`、`Wired`。

## 2. 两条特权指令的用途（实验任务 2）

| 指令 | 全称 | 作用 |
| --- | --- | --- |
| `tlbp` | TLB Probe | 用 `EntryHi` 的 VPN/ASID 在 TLB 中查找匹配项，把命中槽位号写入 `Index`；未命中则把 `Index` 最高位置 1（结果为负） |
| `tlbwi` | TLB Write Indexed | 把 `EntryHi`/`EntryLo0`/`EntryLo1`/`PageMask` 的当前值写入 `Index` 指定的那个 TLB 槽位 |

（对照：`tlbwr` 是 Write Random，写随机槽位，用于 refill；本实验要按探测到的槽位精确清除，所以用 `tlbwi`。）

## 3. 为什么 `tlbp` 之后有 4 条 `nop`（实验任务 2 的核心问答）

`tlbp` 对 `Index` 的写入是**异步生效**的：CP0 存在写后读冒险（CP0 hazard）。
紧随其后的 `mfc0 t1, CP0_INDEX` 可能读到旧值。MIPS R3000/早期实现要求在这类
「写 CP0 → 读同一 CP0」之间留出足够的指令间隔（本实验给出的骨架是 4 条 `nop`），
以保证探测结果已经稳定可见。

要点：
- 这不是流水线分支延迟槽（分支延迟槽只有 1 条），而是 **CP0 冒险间隔**；
- 少了这些 `nop`，`bltz t1, NOFOUND` 可能基于陈旧的 `Index` 判断，
  导致「该清的项没清」或「清错槽位」，表现为随机的地址翻译错误 —— 极难复现的 bug；
- 具体需要几条间隔与处理器实现有关，**以实验指导给出的 4 条为准**，不要自行删减。

## 4. 填写 `tlb_out`（实验任务 3，核心交付物）

骨架（**按公开语义构造的等价示例，非北航原文转录**）：

```asm
LEAF(tlb_out)
    mfc0  t0, CP0_ENTRYHI        /* 备份调用者 EntryHi */
    mtc0  a0, CP0_ENTRYHI        /* a0 = 待失效的虚拟地址（取其 VPN） */
    nop
    tlbp                         /* ← 空行 1：探测，结果进 Index */
    nop
    nop
    nop
    nop                          /* CP0 冒险间隔 */
    mfc0  t1, CP0_INDEX
    bltz  t1, NOFOUND            /* Index < 0 → TLB 里本来就没有，直接恢复返回 */
    nop
    mtc0  zero, CP0_ENTRYHI
    mtc0  zero, CP0_ENTRYLO0
    mtc0  zero, CP0_ENTRYLO1     /* 三项清零 = 该项不再有效 */
    nop
    tlbwi                        /* ← 空行 2：按 Index 写回，完成清除 */
    nop
NOFOUND:
    mtc0  t0, CP0_ENTRYHI        /* 恢复 EntryHi，避免污染调用者 */
    j     ra
    nop
END(tlb_out)
```

填写时注意：
- `tlbp` 必须在 `mtc0 a0, CP0_ENTRYHI` **之后**（否则探测的是别人的 VPN）；
- `tlbwi` 必须在三个 `mtc0 zero` **之后**（否则写进去的不是空项）；
- `NOFOUND` 分支也要恢复 `EntryHi`，两条路径都不能漏；
- `.set noreorder` 下延迟槽必须显式填 `nop`，不要让汇编器自动重排。

## 5. 构建与运行验证

```bash
make
gxemul -E oldtestmips -d gzemul/vmlinuz    # 具体命令以实验指导为准
```

验证点：
- 内核自检（`page_check()` 等）通过，且**不再出现随机的地址翻译错误**；
- 在 `page_remove` 之后立刻访问同一虚拟地址，应触发 TLB Refill 并重新查页表，
  而不是继续用旧翻译命中已被回收的物理页；
- 用 `mips-linux-gnu-objdump -d` 反汇编，确认 `tlbp`/`tlbwi` 落在正确位置、4 条 `nop` 未被优化掉。

## 6. 提交物

- 补全后的 `mm/tlb_asm.S`；
- 对 `tlbp`/`tlbwi` 用途与 4 条 `nop` 作用的书面解释；
- 本地构建与运行记录。

平台侧按 `SUBMIT` 记完成，不做自动判分。