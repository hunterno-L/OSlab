/* ===== 学生填写区：实现 lib/env.c 的 env_alloc() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.5「取空闲 PCB / 初始化各字段 / 设置 cpo_status /
 * 调用 env_setup_vm」+ MOS 公开语义构造的等价示例。
 *
 * 正确性自行验证要点（已用 5 条用例实测）：
 *   - 空闲链表空时返回 -E_NO_FREE_ENV(-5)，不是 -E_NO_MEM；
 *   - env_setup_vm 失败必须原样返回错误码，且 PCB 仍留在空闲链表上（用例 3 用 4 页物理内存
 *     制造第 5 次分配失败，随后 L 4 检查表头仍是 59）；
 *   - env_status 必须是 ENV_NOT_RUNNABLE(1) 而不是 ENV_RUNNABLE(2)：新进程要等
 *     env_create/load_icode 完成后才可被调度；
 *   - cp0_status 必须置 KUp|IEp|IM4|CU0 且清 KUc|IEc：eret 把 KUp->KUc、IEp->IEc，
 *     若直接置 KUc|IEc 则 eret 后反而回到内核态/关中断（用例 1/5 的 N 输出逐位检查）；
 *   - regs[29] = USTACKTOP = 0x40000000；
 *   - 复用槽位时必须重新初始化 Trapframe 与 pri/ipc 字段（用例 5 先用 D 操作污染成
 *     0xDEADBEEF/0xFFFFFFFF/99 再分配，只有真正 memset+赋值才能通过）。 */
static int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    int r;
    struct Env *e;
    *newenv = NULL;
    if (LIST_EMPTY(&env_free_list)) { return -E_NO_FREE_ENV; }
    e = LIST_FIRST(&env_free_list);
    r = env_setup_vm(e);
    if (r != 0) { return r; }
    e->env_id = mkenvid(e);
    e->env_parent_id = parent_id;
    e->env_status = ENV_NOT_RUNNABLE;
    e->env_pri = 1;
    memset(&e->env_tf, 0, sizeof e->env_tf);
    e->env_tf.regs[29] = USTACKTOP;
    e->env_tf.cp0_status = ENV_USER_SR;
    e->env_pgfault_handler = 0;
    e->env_ipc_receiving = 0;
    e->env_ipc_from = 0;
    e->env_ipc_value = 0;
    LIST_REMOVE(e, env_link);
    *newenv = e;
    return 0;
}

/* ===== 填写区结束 ===== */