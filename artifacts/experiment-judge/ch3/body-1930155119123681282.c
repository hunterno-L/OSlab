/* ===== 学生填写区：实现 lib/env.c 的 env_alloc() ===== */

/* 题面 3.5：从 env_free_list 取一个空闲 PCB，初始化各字段并设置 CP0 状态寄存器。
 *   Step 1: 空闲链表为空 -> 返回 -E_NO_FREE_ENV；否则取表头 PCB；
 *   Step 2: 调 env_setup_vm(e) 建立虚拟内存布局，失败必须【原样返回错误码】，
 *           且此时 PCB 仍留在空闲链表上（不能先摘链再失败）；
 *   Step 3: e->env_id = mkenvid(e)；e->env_parent_id = parent_id；
 *           e->env_status = ENV_NOT_RUNNABLE（新进程不立即被调度）；e->env_pri = 1；
 *   Step 4: 清空 Trapframe，e->env_tf.regs[29] = USTACKTOP（初始栈指针），
 *           e->env_tf.cp0_status = KUp|IEp|IM4|CU0，并清 KUc|IEc ——
 *           eret 会把 KUp->KUc、IEp->IEc，这样才能进入用户态并开中断；
 *   Step 5: LIST_REMOVE 把 PCB 摘出空闲链表，*newenv = e，返回 0。
 * 平台已提供：env_init / env_setup_vm / mkenvid / env_free / page_alloc。 */
static int env_alloc(struct Env **newenv, uint32_t parent_id)
{
    /* finish your code here */
    (void)newenv;
    (void)parent_id;
    return -E_NO_FREE_ENV;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 80

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I        env_init()，打印 "I 空闲PCB数"（重复调用打印 I SKIP）
 *   A parent 调学生 env_alloc(&e,parent)，打印 "A 返回码 PCB下标 新envid"
 *            （下标 -1 表示 *newenv 为空或越界；envid 取驱动自己保存的 e->env_id）
 *   N idx    打印 "N idx env_id parent_id status pri sp sr KUc IEc KUp IEp"
 *   P idx    打印 "P idx env_pgfault_handler ipc_receiving ipc_from ipc_value"
 *   D idx    驱动把 envs[idx] 的 Trapframe/优先级/状态/IPC 字段全部污染成哨兵值，
 *            用于检验 env_alloc 是否真的重新初始化了这些字段（打印 "D idx"）
 *   F idx    env_free(&envs[idx])，打印 "F idx"
 *   U idx    curenv = &envs[idx]，打印 "U idx"
 *   L k      打印空闲 PCB 链表前 k 个下标（k 0..64）
 *   W idx    打印 "W idx 页目录物理地址 env_cr3"（检验 env_setup_vm 是否被调用）
 *   C        打印空闲物理页数
 * idx 必须 <64，否则驱动直接结束。
 */
int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, did_i = 0;
    if (scanf("%u %u", &np, &fm) != 2) { return 0; }
    if (np == 0 || np > NPAGE_MAX || fm > 65536u) { return 0; }
    reset_env_state(np, fm);
    if (scanf("%d", &n) != 1 || n < 0 || n > MAXOPS) { return 0; }
    for (i = 0; i < n; i++) {
        char op = 0;
        if (scanf(" %c", &op) != 1) { break; }
        if (op == 'I') {
            if (did_i) { printf("I SKIP\n"); continue; }
            did_i = 1;
            env_init();
            printf("I %d\n", count_env_list(&env_free_list, 0));
        } else if (op == 'A') {
            uint32_t parent = 0;
            struct Env *e = NULL;
            int r;
            if (scanf("%u", &parent) != 1) { return 0; }
            r = env_alloc(&e, parent);
            if (env_in_range(e)) {
                printf("A %d %d %u\n", r, env_idx(e), e->env_id);
            } else {
                printf("A %d -1 0\n", r);
            }
        } else if (op == 'N') {
            uint32_t idx = 0;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            printf("N %u %u %u %u %u %u %u %u %u %u %u\n", idx, e->env_id,
                   e->env_parent_id, e->env_status, e->env_pri,
                   e->env_tf.regs[29], e->env_tf.cp0_status,
                   (e->env_tf.cp0_status & MIPS_SR_KUc) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_IEc) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_KUp) ? 1u : 0u,
                   (e->env_tf.cp0_status & MIPS_SR_IEp) ? 1u : 0u);
        } else if (op == 'P') {
            uint32_t idx = 0;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            printf("P %u %u %u %u %u\n", idx, e->env_pgfault_handler,
                   e->env_ipc_receiving, e->env_ipc_from, e->env_ipc_value);
        } else if (op == 'D') {
            uint32_t idx = 0, k;
            struct Env *e;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            e = &envs[idx];
            for (k = 0; k < 32u; k++) { e->env_tf.regs[k] = 0xDEADBEEFu; }
            e->env_tf.cp0_status = 0xFFFFFFFFu;
            e->env_tf.cp0_epc = 0xFFFFFFFFu;
            e->env_tf.cp0_badvaddr = 0xFFFFFFFFu;
            e->env_tf.cp0_cause = 0xFFFFFFFFu;
            e->env_pri = 99u;
            e->env_status = ENV_RUNNABLE;
            e->env_pgfault_handler = 12345u;
            e->env_ipc_receiving = 1u;
            e->env_ipc_from = 777u;
            e->env_ipc_value = 888u;
            printf("D %u\n", idx);
        } else if (op == 'F') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_free(&envs[idx]);
            printf("F %u\n", idx);
        } else if (op == 'U') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            curenv = &envs[idx];
            printf("U %u\n", idx);
        } else if (op == 'L') {
            int k = 0;
            if (scanf("%d", &k) != 1 || k < 0 || k > (int)NENV) { return 0; }
            printf("L ");
            print_env_list_n(&env_free_list, 0, k);
        } else if (op == 'W') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("W %u %u %u\n", idx, pa_of(envs[idx].env_pgdir), envs[idx].env_cr3);
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else {
            return 0;
        }
    }
    return 0;
}