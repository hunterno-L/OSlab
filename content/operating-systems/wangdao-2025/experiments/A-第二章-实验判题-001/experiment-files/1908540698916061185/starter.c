/* 操作系统实验 2.8 TLB 管理
 *
 * 【本文件不参与平台判分】
 * 本实验是「本地构建实验 · 平台不自动判分」（judge-mode.json: LOCAL_BUILD）。
 * 真正要填写的是 mm/tlb_asm.S 中 tlb_out 的两行 MIPS 特权指令（tlbp / tlbwi），
 * 它们操作 CP0 与真实 TLB 硬件；沙箱是 x86-64 用户态进程，无 MIPS 交叉编译器、
 * 无 gxemul、无 CP0，无法功能判题。完整步骤见同目录 local-build.md。
 *
 * 下面只是把待填汇编骨架以注释形式随脚手架下发，方便学生在编辑器里直接看到上下文。
 * 平台不会编译或运行本文件来给分；cases.json 为空，不生成 judge-cases.json。
 *
 * 待填骨架（按 MIPS 公开语义构造的等价示例，非原 Listing 转录）：
 *
 *   LEAF(tlb_out)
 *       mfc0  t0, CP0_ENTRYHI        // 备份调用者的 EntryHi
 *       mtc0  a0, CP0_ENTRYHI        // 用待失效的 VPN 填 EntryHi
 *       nop
 *       ______                       // 空行 1：填 tlbp（探测 TLB，结果写入 CP0 Index）
 *       nop
 *       nop
 *       nop
 *       nop                          // 4 条 nop：CP0 冒险间隔，见 local-build.md 第 3 节
 *       mfc0  t1, CP0_INDEX
 *       bltz  t1, NOFOUND            // Index 最高位为 1 表示未命中
 *       nop
 *       mtc0  zero, CP0_ENTRYHI
 *       mtc0  zero, CP0_ENTRYLO0
 *       mtc0  zero, CP0_ENTRYLO1
 *       nop
 *       ______                       // 空行 2：填 tlbwi（按 Index 写入，清空该项）
 *       nop
 *   NOFOUND:
 *       mtc0  t0, CP0_ENTRYHI        // 恢复 EntryHi
 *       j     ra
 *       nop
 *   END(tlb_out)
 */
#include <stdio.h>

int main(void)
{
    printf("实验 2.8 TLB 管理：本地构建实验，平台不自动判分。\n");
    printf("请在本地 MIPS 环境完成 mm/tlb_asm.S 中 tlb_out 的两行填写（tlbp / tlbwi），\n");
    printf("并解释 tlbp 之后 4 条 nop 的作用。步骤见 local-build.md。\n");
    return 0;
}