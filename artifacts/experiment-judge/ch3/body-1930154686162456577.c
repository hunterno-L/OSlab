/* ===== 学生填写区：实现 lib/env.c 的 env_init() ===== */

/* 题面 3.2：初始化 envs 数组，把全部空闲 PCB 连成 env_free_list，「注意插入的顺序」。
 * 与第二章 page_init 同构：
 *   Step 1: LIST_INIT(&env_free_list)；
 *   Step 2: 升序遍历 i = 0 .. NENV-1，置 envs[i].env_status = ENV_FREE，
 *           并 LIST_INSERT_HEAD(&env_free_list, &envs[i], env_link)。
 * 因为是【头插】，遍历空闲链表得到的下标序列是 NENV-1 .. 0（降序）。
 * 这正是提示「参考 lab2 中把空闲页连成链表的做法」的含义。 */
static void env_init(void)
{
    /* finish your code here */
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
        } else if (op == 'L') {
            int k = 0;
            if (scanf("%d", &k) != 1 || k < 0 || k > (int)NENV) { return 0; }
            printf("L ");
            print_env_list_n(&env_free_list, 0, k);
        } else if (op == 'A') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("A %d %d\n", r, env_idx(e));
        } else if (op == 'F') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_free(&envs[idx]);
            printf("F %u %d\n", idx, count_env_list(&env_free_list, 0));
        } else if (op == 'C') {
            printf("C %d\n", count_env_list(&env_free_list, 0));
        } else {
            return 0;
        }
    }
    return 0;
}