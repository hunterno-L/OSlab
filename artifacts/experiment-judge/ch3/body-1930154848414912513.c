/* ===== 学生填写区：实现 lib/env.c 的 envid2env() ===== */

/* 题面 3.3：通过进程标识符 env_id 取得对应的 PCB。MOS 公开语义：
 *   Step 1: envid == 0 是特例，表示「当前进程」，*penv = curenv，返回 0；
 *   Step 2: e = &envs[ENVX(envid)]，ENVX 取低 LOG2NENV 位（NENV=64 即低 6 位）；
 *   Step 3: 若 e->env_status == ENV_FREE 或 e->env_id != envid，返回 -E_BAD_ENV。
 *           env_id 的高位是 mkenvid 的生成号，PCB 槽位被复用后旧 id 必须失效，
 *           否则用户进程能凭旧 id 操作一个已经换了主人的 PCB；
 *   Step 4: checkperm 非 0 时做权限检查：只有 curenv 自己、或 curenv 的父进程
 *           （e->env_id == curenv->env_parent_id）才有权操作，否则 -E_BAD_ENV；
 *   Step 5: *penv = e，返回 0。
 * 前置条件：checkperm 非 0 时 curenv 一定非空（检查驱动在输入边界保证）。 */
static int envid2env(uint32_t envid, struct Env **penv, int checkperm)
{
    /* finish your code here */
    (void)envid; (void)checkperm;
    *penv = NULL;
    return -E_BAD_ENV;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 48

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
            printf("U %u\n", idx);
        } else if (op == 'S') {
            uint32_t idx = 0, st = 0;
            if (scanf("%u %u", &idx, &st) != 2 || idx >= NENV || st > 2u) { return 0; }
            envs[idx].env_status = st;
            printf("S %u %u\n", idx, st);
        } else if (op == 'F') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_free(&envs[idx]);
            printf("F %u\n", idx);
        } else if (op == 'N') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("N %u %u %u %u\n", idx, envs[idx].env_id, envs[idx].env_status,
                   envs[idx].env_parent_id);
        } else if (op == 'E') {
            uint32_t envid = 0;
            int cp = 0, r;
            struct Env *e = NULL;
            if (scanf("%u %d", &envid, &cp) != 2 || cp < 0 || cp > 1) { return 0; }
            /* 真实内核里 checkperm 只在进程上下文中使用；curenv 为空时不允许，
             * 否则学生按 MOS 原样写 curenv->env_parent_id 会解引用空指针。 */
            if (cp != 0 && curenv == NULL) { return 0; }
            r = envid2env(envid, &e, cp);
            printf("E %d %d\n", r, env_idx(e));
        } else {
            return 0;
        }
    }
    return 0;
}