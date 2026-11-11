/* 操作系统实验 3.12 异常处理
 *
 * 【本文件不参与平台判分】
 * 本实验是「本地构建实验 · 平台不自动判分」（judge-mode.json: LOCAL_BUILD）。
 * 真正要交付的是：
 *   (1) 链接器脚本里把异常处理入口固定到 0x80000080；
 *   (2) 该入口处的 MIPS 异常分发汇编（读 CP0 Cause 后跳转到对应处理程序）。
 * 沙箱是 x86-64 用户态单文件 C，无 MIPS 交叉链接器、无 CP0、无法把代码链接到指定物理地址，
 * 因此无法功能判题。完整步骤见同目录 local-build.md。
 *
 * 平台不会编译或运行本文件来给分；cases.json 为空，不生成 judge-cases.json。
 * 下面仅以注释形式随脚手架下发 Cause 寄存器位段速查，便于学生在编辑器里直接对照。
 *
 * CP0 Cause（MIPS32 公开语义）：
 *   bit 6..2   ExcCode  异常原因编码（0=Int 中断, 1=Mod, 2=TLBL, 3=TLBS,
 *                      4=AdEL, 5=AdES, 8=Sys, 9=Bp, 10=RI, 11=CpU, 12=Ov ...）
 *   bit 15..8  IP[7:0]  中断挂起位；IP4（bit 12）即时钟中断（见实验 3.13）
 *   bit 31     BD       异常是否发生在分支延迟槽
 * CP0 Status：
 *   bit 15..8  IM[7:0]  中断屏蔽位，需与 Cause.IP 对应位同时置位才会真正取中断
 *   bit 1      EXL      异常级；置位时不再接受普通异常，eret 会清它
 *   bit 22     BEV      =0 时普通异常向量在 0x80000080，=1 时在 0xBFC00380
 *
 * 异常向量（BEV=0）：
 *   0x80000000  TLB Refill
 *   0x80000080  通用异常（本实验入口）
 *   0xBFC00000  Reset
 */
#include <stdio.h>

int main(void)
{
    printf("实验 3.12 异常处理：本地构建实验，平台不自动判分。\n");
    printf("请在本地 MIPS 环境完成链接器脚本的 0x80000080 入口设置与异常分发汇编。\n");
    printf("步骤见 local-build.md。\n");
    return 0;
}