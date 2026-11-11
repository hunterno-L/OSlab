/* ---- 平台提供的 load_icode（实验 3.7 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 用 ELF 加载器把镜像各段装进进程地址空间（宿主等价 load_elf）；
 *          任一段失败就立即返回，【不得】继续设置入口与栈；
 *   Step 2 把 Trapframe 的 cp0_epc 设为 ELF 入口 —— env_pop_tf 的 eret 就跳到这里；
 *          入口值原样赋给 cp0_epc，不做对齐/范围裁剪；
 *   Step 3 为初始栈分配一页，以 PTE_R|PTE_V 映射到 USTACKTOP-BY2PG，
 *          并把 sp（regs[29]）设为 USTACKTOP。
 *
 * 【引用计数约定】与 3.6 一致：page_insert 自身会 pp_ref++（实验 2.7 已验证的契约），
 * 所以这里不再额外 p->pp_ref++，栈页 pp_ref 恒为 1。 */

static MOS_UNUSED void load_icode(struct Env *e, uint32_t entry)
{
    struct Page *p = NULL;
    if (!env_in_range(e) || !pgdir_ok(e->env_pgdir)) { return; }
    if (load_elf(e, load_icode_mapper) != 0) { return; }
    e->env_tf.cp0_epc = entry;
    if (page_alloc(&p) != 0) { return; }
    if (page_insert(e->env_pgdir, p, USTACKTOP - BY2PG, PTE_R | PTE_V) != 0) { return; }
    e->env_tf.regs[29] = USTACKTOP;
}