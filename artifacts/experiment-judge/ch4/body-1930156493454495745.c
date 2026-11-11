/* ===== 学生填写区：实现内核系统调用入口 handle_sys() ===== */

/* 题面 4.2「实现内核的系统调用处理机制」任务描述原文：
 *   「编写 handle_sys 函数，完成内核部分的系统调用处理机制，使得用户空间的系统调用
 *     能够正确地在内核中执行并返回结果。」
 * 实验任务 2「编写 handle_sys 函数，该函数负责解析系统调用号，并调用相应的内核函数」、
 * 实验任务 6「完成 handle_sys 函数，使得内核部分的系统调用机制可以正常工作」。
 * （实验任务 3 的 syscall 特权指令封装属 user/syscall_wrap.S，是实验 4.1，Tier B 本地构建。）
 *
 * MOS 公开语义，四步：
 *   Step 1 现场在 KERNEL_SP（宿主等价 &kernsp_tf）；$v0 = tf->regs[2] 是系统调用号；
 *   Step 2 号为 0、或 >= SYS_MAX、或 sys_call_table[sysno] 为 NULL（0/6/7 号空槽位）
 *          -> tf->regs[2] = (uint32_t)(-E_NO_SYS) 并立即返回；
 *   Step 3 否则把槽位强转成 syscall_fn，传 (int)sysno 与 tf->regs[4..8]
 *          （MIPS o32：$a0..$a3 是 regs[4..7]，栈上第一个参数是 regs[8]）；
 *   Step 4 把返回值写回 tf->regs[2]，用户态 msyscall 从这里取结果。
 * 【不要】做 cp0_epc += 4：跳过 syscall 指令属陷阱返回路径，不在本题考查范围。
 * 平台已提供 sys_call_table[SYS_MAX]（void * 数组，见 env-systable 段）与 syscall_fn 类型。 */
static void handle_sys(void)
{
    /* finish your code here */
    struct Trapframe *tf = KERNEL_SP;
    (void)tf;
}

/* ===== 填写区结束 ===== */
/* ---- 第四章共享检查驱动（平台提供，学生不填写） ----
 *
 * stdin：第一行 np fm（物理页数 1..NPAGE_MAX、空闲内存上界字节数），第二行操作条数
 * n（0..MAXOPS），随后 n 行每行一个操作符。完整操作符表、门控宏与页账本见
 * artifacts/experiment-judge/ch4/protocol.md 及本实验 content.md「检查驱动与输入协议」。
 * 骨架：I A U S R P G Q O T N J E H L F C 恒有；K 除 CH4_TRAP_DRIVER 外恒有；
 * B/M 需 CH4_TRAP_DRIVER；Y/Z 在非用户态是事件日志；用户态下 W 随 CH4_USER_DRIVER、
 * V/Y 需 CH4_FORK_DRIVER（Y 改为子进程分支夹具）、D 需 CH4_DUPPAGE_DRIVER、
 * X/Z 需 CH4_PGFAULT_DRIVER。未启用的操作符按未知输入拒绝。
 *
 * 边界原则（harness-spec §6）：任何畸形/截断/越界输入都必须以退出码 0 结束，
 * 否则沙箱会把「答案错误」误判成 Runtime Error。故所有 scanf 都校验返回值，
 * 所有下标/地址都先做范围检查；不满足前置条件的输入直接 return 0，绝不打印伪造结果。
 */

#define MAXOPS 96

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
            printf("A %d %d %u\n", r, env_idx(e), env_in_range(e) ? e->env_id : 0u);
        } else if (op == 'U') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            curenv = &envs[idx];
#ifdef CH4_USER_DRIVER
            env = &envs[idx];
#endif
            printf("U %u\n", idx);
        } else if (op == 'S') {
            uint32_t idx = 0, st = 0;
            if (scanf("%u %u", &idx, &st) != 2 || idx >= NENV || st > 2u) { return 0; }
            envs[idx].env_status = st;
            printf("S %u %u\n", idx, st);
        } else if (op == 'R') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            if (!in_sched_list(&envs[idx])) {
                LIST_INSERT_HEAD(&env_sched_list, &envs[idx], env_sched_link);
            }
            printf("R %u\n", idx);
        } else if (op == 'P') {
            uint32_t idx = 0, va = 0, perm = 0;
            struct Page *p = NULL;
            int r;
            if (scanf("%u %u %u", &idx, &va, &perm) != 3 || idx >= NENV || va >= UTOP) { return 0; }
            if (!pgdir_ok(envs[idx].env_pgdir)) { printf("P -3 4294967295\n"); continue; }
            r = page_alloc(&p);
            if (r == 0) { r = page_insert(envs[idx].env_pgdir, p, va, perm); }
            printf("P %d %u\n", r, (r == 0) ? page2pa(p) : ~0u);
#ifdef CH4_TRAP_DRIVER
        } else if (op == 'B') {
            uint32_t idx = 0, va = 0, perm = 0;
            Pte *pte = NULL;
            int r;
            if (scanf("%u %u %u", &idx, &va, &perm) != 3 || idx >= NENV || va >= UTOP) { return 0; }
            if (!pgdir_ok(envs[idx].env_pgdir)) { printf("B -3\n"); continue; }
            r = pgdir_walk(envs[idx].env_pgdir, va, 1, &pte);
            if (r != 0 || pte == NULL) { printf("B %d\n", r); continue; }
            *pte = (Pte)(perm & 0xFFFu);
            printf("B 0\n");
#endif
#ifndef CH4_TRAP_DRIVER
        } else if (op == 'K') {
            uint32_t s = 0, a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0;
            if (scanf("%u %u %u %u %u %u", &s, &a0, &a1, &a2, &a3, &a4) != 6) { return 0; }
            /* 真实内核里 handle_sys 只在进程上下文中被调用（curenv 一定非空）；
             * 多个 sys_* 会解引用 curenv，驱动在输入边界拒绝无现场的调用。 */
            if (curenv == NULL) { return 0; }
            kernsp_tf.regs[2] = s;
            kernsp_tf.regs[4] = a0;
            kernsp_tf.regs[5] = a1;
            kernsp_tf.regs[6] = a2;
            kernsp_tf.regs[7] = a3;
            kernsp_tf.regs[8] = a4;
            handle_sys();
            printf("K %d\n", (int)kernsp_tf.regs[2]);
#endif
        } else if (op == 'G') {
            printf("G %d\n", (int)kernsp_tf.regs[2]);
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else if (op == 'O') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("O %u\n", env_pte(envs[idx].env_pgdir, va));
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u\n", idx, envs[idx].env_id, envs[idx].env_parent_id,
                   envs[idx].env_status, envs[idx].env_pri, envs[idx].env_cr3);
        } else if (op == 'N') {
            /* 给 PCB 现场写可辨认的 cp0_epc 与 $v0，检验现场整体复制与子进程 $v0 归零 */
            uint32_t idx = 0, epc = 0, v0 = 0;
            if (scanf("%u %u %u", &idx, &epc, &v0) != 3 || idx >= NENV) { return 0; }
            envs[idx].env_tf.cp0_epc = epc;
            envs[idx].env_tf.regs[2] = v0;
            printf("N %u %u %u\n", idx, epc, v0);
        } else if (op == 'J') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("J %u %u %u %u %u\n", idx, envs[idx].env_tf.cp0_epc,
                   envs[idx].env_tf.regs[2], envs[idx].env_tf.regs[29],
                   envs[idx].env_tf.cp0_status);
        } else if (op == 'E') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("E %u %u %u %u %u %u\n", idx, envs[idx].env_pgfault_handler,
                   envs[idx].env_ipc_receiving, envs[idx].env_ipc_dstva,
                   envs[idx].env_ipc_from, envs[idx].env_ipc_value);
#ifdef CH4_TRAP_DRIVER
        } else if (op == 'M') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            timestack_tf.cp0_badvaddr = va;
            page_fault_handler(TIMESTACK);
            printf("M %u\n", (curenv != NULL && env_in_range(curenv))
                   ? env_pte(curenv->env_pgdir, va) : ~0u);
#endif
#ifdef CH4_USER_DRIVER
#ifdef CH4_FORK_DRIVER
        } else if (op == 'V') {
            int r;
            /* fork 第一步就是 syscall_env_alloc，它会解引用 curenv */
            if (curenv == NULL || env == NULL) { return 0; }
            r = fork();
            printf("V %d %d %d\n", r, r > 0 ? (int)ENVX((uint32_t)r) : -1, env_idx(env));
        } else if (op == 'Y') {
            int r;
            if (curenv == NULL || env == NULL) { return 0; }
            /* 模拟子进程带 $v0=0 从 fork 返回。env 先置 NULL 作哨兵：
             * 子进程分支必须把它改回自己的 PCB，漏写就会留下 -1。 */
            env = NULL;
            fork_child_stub = 1;
            r = fork();
            fork_child_stub = 0;
            printf("Y %d %d\n", r, env_idx(env));
#endif
#ifdef CH4_DUPPAGE_DRIVER
        } else if (op == 'D') {
            uint32_t envid = 0, pn = 0;
            if (scanf("%u %u", &envid, &pn) != 2) { return 0; }
            if (env == NULL || !env_in_range(env) || pn >= VPN(USTACKTOP)) { return 0; }
            duppage(envid, pn);
            printf("D %u %u\n", user_pte(pn << 12),
                   env_pte(envs[ENVX(envid)].env_pgdir, pn << 12));
#endif
#ifdef CH4_PGFAULT_DRIVER
        } else if (op == 'X') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            if (env == NULL || !env_in_range(env)) { return 0; }
            pgfault(va);
            printf("X %u\n", user_pte(va));
        } else if (op == 'Z') {
            uint32_t va = 0;
            if (scanf("%u", &va) != 1) { return 0; }
            dump_user_page(va);
#endif
        } else if (op == 'W') {
            uint32_t dst = 0, src = 0, len = 0;
            if (scanf("%u %u %u", &dst, &src, &len) != 3 || len > BY2PG) { return 0; }
            if (env == NULL || !env_in_range(env)) { return 0; }
            printf("W %d\n", user_memcpy(dst, src, len));
#endif
        } else if (op == 'H') {
            struct Env *e = LIST_FIRST(&env_sched_list);
            printf("H %d\n", env_idx(e));
        } else if (op == 'L') {
            printf("L ");
            print_env_list(&env_sched_list, 1);
        } else if (op == 'F') {
            printf("F ");
            print_env_list(&env_free_list, 0);
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
#ifndef CH4_USER_DRIVER
        } else if (op == 'Y') {
            int k, lim = rec_n < MAXREC ? rec_n : MAXREC;
            printf("Y %d\n", rec_n);
            for (k = 0; k < lim; k++) { printf("%d %u %d\n", rec_kind[k], rec_arg[k], rec_cur[k]); }
        } else if (op == 'Z') {
            rec_n = 0;
            printf("Z\n");
#endif
        } else {
            return 0;
        }
    }
    return 0;
}