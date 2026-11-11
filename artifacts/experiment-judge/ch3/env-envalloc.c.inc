/* ---- 平台提供的 lib/env.c env_alloc（实验 3.5 考查时排除本片段） ----
 * MOS 公开语义：
 *   Step 1 从 env_free_list 取一个空闲 PCB，空则 -E_NO_FREE_ENV；
 *   Step 2 调 env_setup_vm 建立虚拟内存布局，失败必须原样返回错误码
 *          （此时 PCB 仍留在空闲链表上，不能被摘掉）；
 *   Step 3 填 env_id（mkenvid）、env_parent_id、env_status = ENV_NOT_RUNNABLE、env_pri = 1；
 *   Step 4 清空 Trapframe，设初始 sp = USTACKTOP，
 *          cp0_status 置 KUp|IEp|IM4|CU0 且清 KUc|IEc —— eret 后才能进入用户态并开中断；
 *   Step 5 把 PCB 从空闲链表摘下，*newenv 指向它。 */

/* 实验 3.4 把 env_setup_vm 当作学生填写目标（body 在本片段之后），故先前置声明。
 * 当 env-setupvm.c.inc 作为平台件被包含时，这条声明与随后的定义兼容，无副作用。 */
static MOS_UNUSED int env_setup_vm(struct Env *e);

static MOS_UNUSED int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    int r;
    struct Env *e;
    *newenv = NULL;
    if (LIST_EMPTY(&env_free_list)) { return -E_NO_FREE_ENV; }
    e = LIST_FIRST(&env_free_list);
    if (!env_in_range(e)) { return -E_NO_FREE_ENV; }
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
    e->env_ipc_dstva = 0;
    e->env_ipc_from = 0;
    e->env_ipc_value = 0;
    LIST_REMOVE(e, env_link);
    *newenv = e;
    return 0;
}