/* 内核缺页实验（4.11）：启用驱动 B 夹具与 M 操作符（写 TIMESTACK 的 cp0_badvaddr
 * 后调 page_fault_handler(TIMESTACK)，再打印该页页表项）。 */
#define CH4_TRAP_DRIVER 1