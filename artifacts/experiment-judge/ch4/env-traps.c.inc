/* ---- 平台提供的 lib/traps.c page_fault_handler（实验 4.11 考查时排除本片段） ----
 *
 * 【宿主环境说明】工作区未随附 lib/traps.c 原文。题面 4.11 实验任务 2
 * 逐条要求的是「检查虚拟地址是否合法 / 为缺失的页面分配物理内存 / 更新页表项建立映射 /
 * 处理内存不足等异常」，即【按需分页】语义；真实 MOS 的 page_fault_handler 走的是
 * 「把异常转发到用户注册的 env_pgfault_handler」的 COW 路径。本 harness 以题面文字为准
 * 构造按需分页版本，属等价示例，不是原文转录。
 *
 * 签名适配：真实 MOS 从 TIMESTACK 取现场，宿主改为显式传入 struct Trapframe *tf，
 * 便于检查驱动构造任意故障地址；取现场这一步本身不是本题考查点。
 *
 * 宿主页表项约定：PTE_V 已置位但 PTE_ADDR 为 0，表示「该页已登记、尚未分配物理页」，
 * 这正是按需分页要补的洞（检查驱动用夹具 op 造出这种页表项）。
 *
 * 算法（填写区注释里逐条给出，保证可判题）：
 *   Step 1 tf / curenv 非法直接返回；va = tf->cp0_badvaddr；
 *   Step 2 va >= UTOP 是内核地址缺页，不在本函数处理范围，直接返回；
 *   Step 3 pgdir_walk(..., create=0) 找页表项，失败 / 为 NULL / 越界 -> 返回；
 *   Step 4 PTE_V 为 0 说明该页根本没登记（真正的非法访问），返回，不分配；
 *   Step 5 PTE_ADDR 非 0 说明已有物理页，不是缺页，返回；
 *   Step 6 page_alloc 失败（内存不足）-> 返回，且【页表项必须保持原样】；
 *   Step 7 *pte = page2pa(p) | 原有权限位，p->pp_ref++。
 * 全程不修改 tf、不切换进程：恢复被中断进程的执行由陷阱返回路径负责。 */

static MOS_UNUSED void page_fault_handler(struct Trapframe *tf)
{
    uint32_t va, perm;
    Pte *pte = NULL;
    struct Page *p = NULL;
    if (tf == NULL || curenv == NULL || !env_in_range(curenv)) { return; }
    if (!pgdir_ok(curenv->env_pgdir)) { return; }
    va = tf->cp0_badvaddr;
    if (va >= UTOP) { return; }
    if (pgdir_walk(curenv->env_pgdir, ROUNDDOWN(va, BY2PG), 0, &pte) != 0) { return; }
    if (pte == NULL || !pte_in_range(pte)) { return; }
    perm = *pte & 0xFFFu;
    if ((perm & PTE_V) == 0) { return; }
    if (PTE_ADDR(*pte) != 0) { return; }
    if (page_alloc(&p) != 0) { return; }
    *pte = page2pa(p) | perm;
    p->pp_ref++;
}