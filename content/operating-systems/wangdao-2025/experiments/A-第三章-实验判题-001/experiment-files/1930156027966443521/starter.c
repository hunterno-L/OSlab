/* 操作系统实验 3.13 时钟中断
 *
 * 【本文件不参与平台判分】
 * 本实验是「本地构建实验 · 平台不自动判分」（judge-mode.json: LOCAL_BUILD）。
 * 真正要交付的是：
 *   (1) kclock_init：设置定时器频率 + 把时钟中断绑定到 4 号中断；
 *   (2) 链接器脚本中的中断入口地址（0x80000080）；
 *   (3) 异常分发代码把时钟中断路由到 timer_irq；
 *   (4) timer_irq 中调用 sched_yield 实现时间片轮转。
 * 沙箱无硬件定时器、无 CP0、无 MIPS 交叉工具链，无法功能判题。
 * 完整步骤见同目录 local-build.md。
 *
 * 平台不会编译或运行本文件来给分；cases.json 为空，不生成 judge-cases.json。
 * 下面仅以注释形式随脚手架下发中断相关位段速查。
 *
 * 时钟中断链路（MIPS32 + gxemul oldtestmips 公开语义）：
 *   定时器硬件到期 → 拉高中断线 4 → CP0 Cause.IP4（bit 12）置位
 *   → 若 Status.IM4（bit 12）未屏蔽且 Status.IE（bit 0）=1 且 EXL=0
 *   → CPU 取通用异常向量 0x80000080，Cause.ExcCode = 0（Int）
 *   → 异常分发代码识别 IP4 → timer_irq → sched_yield → 切换进程
 *
 * 时间片轮转要点：
 *   timer_irq 必须保存/恢复现场（复用实验 3.10 的 Trapframe 机制），
 *   sched_yield 把当前进程从 ENV_RUNNABLE 摘下、挂回就绪链表尾、选下一个进程 env_run。
 *   中断服务程序里不能做可能睡眠/阻塞的操作。
 */
#include <stdio.h>

int main(void)
{
    printf("实验 3.13 时钟中断：本地构建实验，平台不自动判分。\n");
    printf("请在本地 MIPS 环境完成 kclock_init、中断入口链接与 timer_irq → sched_yield。\n");
    printf("步骤见 local-build.md。\n");
    return 0;
}