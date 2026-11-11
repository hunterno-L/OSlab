/* ===== 学生填写区：实现 lib/env.c 的 env_alloc() ===== */

/* 典型错误解（四个缺陷都能被用例观测到）：
 *   缺陷 1：env_status 直接设成 ENV_RUNNABLE。新进程还没加载映像就被调度会跑飞。
 *           观测点：N 的 status 字段（参考解 1，本解 2）。
 *   缺陷 2：cp0_status 直接置「当前」位 KUc|IEc，而不是「上一次」位 KUp|IEp。
 *           eret 会把 KUp->KUc、IEp->IEc，于是本解 eret 后反而回到内核态且关中断。
 *           观测点：N 的 sr 与 KUc/IEc/KUp/IEp 四个位字段。
 *   缺陷 3：先 LIST_REMOVE 再调 env_setup_vm。失败时 PCB 已被摘出空闲链表却没人持有，
 *           永久泄漏一个 PCB 槽位。观测点：用例 3 第 5 次分配失败后的 L 4 表头。
 *   缺陷 4：不清空 Trapframe，也不复位 env_pgfault_handler / 三个 IPC 字段。
 *           复用槽位时会继承上一个进程的缺页处理函数与 IPC 状态。
 *           观测点：用例 5 先 D 污染再分配，P 输出（参考解全 0，本解 12345/1/777/888）。 */
static int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    int r;
    struct Env *e;
    *newenv = NULL;
    if (LIST_EMPTY(&env_free_list)) { return -E_NO_FREE_ENV; }
    e = LIST_FIRST(&env_free_list);
    LIST_REMOVE(e, env_link);                       /* 缺陷 3：摘链太早 */
    r = env_setup_vm(e);
    if (r != 0) { return r; }
    e->env_id = mkenvid(e);
    e->env_parent_id = parent_id;
    e->env_status = ENV_RUNNABLE;                   /* 缺陷 1 */
    e->env_pri = 1;
    e->env_tf.regs[29] = USTACKTOP;
    e->env_tf.cp0_status = MIPS_SR_KUc | MIPS_SR_IEc | MIPS_SR_IM4 | MIPS_SR_CU0;  /* 缺陷 2 */
    *newenv = e;                                    /* 缺陷 4：没有 memset / 没有复位 IPC */
    return 0;
}

/* ===== 填写区结束 ===== */