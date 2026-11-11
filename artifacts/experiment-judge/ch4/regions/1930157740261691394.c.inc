/* ===== 学生填写区：实现 lib/syscall_all.c 的 sys_set_pgfault_handler() ===== */

/* 题面 4.12 任务描述原文：
 *   「在 lib/syscall_all.c 中实现 sys_set_pgfault_handler 函数，该函数允许用户进程
 *     设置自己的缺页处理函数。」
 * 实验任务 2「编写 sys_set_pgfault_handler 函数，实现设置缺页处理函数的功能：
 * 检查传入的处理函数指针是否有效；调用 syscall_mem_alloc 为处理函数分配内存；
 * 设置 env_pgfault_handler 字段为传入的处理函数」、实验任务 3「确保函数遵循 MIPS
 * 调用规范，正确处理错误情况」。
 *
 * 【宿主环境说明】题面未给签名，工作区也未随附 lib/syscall_all.c 原文。以下签名与步骤按 MOS 公开语义构造，
 * 属等价示例而非原文转录；u_int 一律写成等宽的 uint32_t。
 *
 * MOS 公开语义，四步：
 *   Step 1 校验处理函数指针：func == 0 或 func >= UTOP -> 返回 -E_INVAL
 *          （处理函数必须在用户空间，空指针与内核地址都不许）；
 *   Step 2 校验异常栈顶：xstacktop < BY2PG 或 xstacktop > UTOP -> 返回 -E_INVAL
 *          （栈顶至少要容得下一页，且不能伸进内核段）；
 *   Step 3 envid2env(envid, &e, 0) 取 PCB，失败原样上抛；写 e->env_pgfault_handler = func；
 *   Step 4 调 sys_mem_alloc(sysno, envid, xstacktop - BY2PG, PTE_V | PTE_R)
 *          为异常处理栈申请一页，返回值原样上抛。
 *
 * 【关键契约一】申请的那一页在 xstacktop - BY2PG，不是 xstacktop 本身：
 *   栈是【向下】生长的，栈顶地址本身不属于这一页。差一页就会把栈建到别处，
 *   第一次缺页切栈即跑飞。
 * 【关键契约二】权限必须是 PTE_V | PTE_R（MOS 里 PTE_R 就是可写位 / MIPS 的 D 位）：
 *   异常栈要压寄存器，只读页会让处理函数自己再缺页。
 * 【关键契约三】参数校验必须在 envid2env 与写 handler 之前完成，
 *   任何一次失败都不得留下半个 handler。
 * 【关键契约四】本函数【不】解除旧栈页的映射（MOS 原样语义），
 *   重复设置会再申请一页，旧页仍挂着；也【不】把进程挂进就绪链表。
 * 宿主 PCB 未设 env_xstacktop 字段：真实 MOS 用它给陷阱入口汇编切异常栈，
 * 那段汇编属实验 4.1（Tier B 本地构建），此处 xstacktop 只用于算出要申请的那一页。 */
static int sys_set_pgfault_handler(int sysno, uint32_t envid,
                                   uint32_t func, uint32_t xstacktop)
{
    /* finish your code here */
    (void)sysno; (void)envid; (void)func; (void)xstacktop;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */