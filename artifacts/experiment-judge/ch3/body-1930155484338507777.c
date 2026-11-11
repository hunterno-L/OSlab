/* ===== 学生填写区：实现 lib/env.c 的 env_create() 与 env_create_priority() ===== */

/* 题面 3.8：实现进程创建原语。真实签名（保留原始参数）：
 *   void env_create_priority(void *binary, uint32_t size, int priority)
 *   void env_create(void *binary, uint32_t size)
 *
 * env_create_priority 的 MOS 公开语义（五步，顺序不可乱）：
 *   Step 1 调 env_alloc(&e, 0) 拿一个 PCB（父进程 id 为 0）；
 *          env_alloc 返回非 0（空闲 PCB 耗尽 -E_NO_FREE_ENV / 物理页耗尽 -E_NO_MEM）时
 *          必须【立即返回】，不得把半个进程挂进就绪链表；
 *   Step 2 调 load_icode(e, elf_entry) 把镜像装进该进程地址空间并设入口/初始栈；
 *   Step 3 e->env_pri = (uint32_t)priority —— 原样保存调用者给的优先级（0 也是合法值）；
 *   Step 4 e->env_status = ENV_RUNNABLE —— 只有置成就绪态，调度器才会选中它；
 *   Step 5 LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link) —— 头插进就绪链表。
 * env_create 等价于优先级为 1 的 env_create_priority：env_create_priority(binary, size, 1)。
 *
 * 【宿主等价适配（务必按此契约填写）】真实内核里 binary 是链接进内核的用户镜像字节数组、
 *   size 是其字节数，load_icode(e, binary, size) 直接解析这段 ELF。宿主沙箱没有真实 ELF，
 *   load_icode 改用由 stdin 装入的段表(seg_table)与入口(elf_entry)，因此 binary/size 不再
 *   参与实际加载。为了让这两个保留参数仍有可观测语义，本宿主约定一条守卫：
 *       if (binary == NULL || size == 0) { return; }   // 空镜像不创建进程
 *   正常调用 ENV_CREATE(x) 传的 size 恒为 64、binary 恒非空，守卫不触发；
 *   驱动用 Z（size=0）与 Y（binary=NULL）两个操作专门检验这条守卫。
 *
 * 平台已提供：env_alloc / load_icode / env_in_range / LIST_INSERT_HEAD / env_sched_list /
 *             binary_user_A,B,C / ENV_CREATE / ENV_CREATE_PRIORITY 宏 / elf_entry。
 * 注意：先定义 env_create_priority，再定义 env_create（后者调用前者，避免前置声明）。 */
static void env_create_priority(void *binary, uint32_t size, int priority)
{
    /* finish your code here */
    (void)binary;
    (void)size;
    (void)priority;
}

static void env_create(void *binary, uint32_t size)
{
    /* finish your code here */
    (void)binary;
    (void)size;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 80

/* 平台提供的镜像数组与 ENV_CREATE 宏（实验 3.9 考查宏的应用，本实验考查函数本体）。
 * binary_user_x 是 64 字节缓冲，ENV_CREATE(x) 展开为 env_create(binary_x, 64)。 */
static MOS_UNUSED unsigned char binary_user_A[64];
static MOS_UNUSED unsigned char binary_user_B[64];
static MOS_UNUSED unsigned char binary_user_C[64];

#define ENV_CREATE(x)             env_create(binary_##x, (uint32_t)sizeof binary_##x)
#define ENV_CREATE_PRIORITY(x, y) env_create_priority(binary_##x, (uint32_t)sizeof binary_##x, (y))

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I             env_init()，打印 "I 空闲PCB数"（重复打印 I SKIP）
 *   B             把镜像缓冲填成 seg_binary[i] = i & 0xFF，打印 "B"
 *   G             清空段表，打印 "G"
 *   S va size perm  向段表追加一段（最多 8 段，va<UTOP、size<=16384、perm 0..4095），打印 "S 当前段数"
 *   E entry       设 elf_entry = entry（load_icode 用它作 cp0_epc），打印 "E entry"
 *   A x           ENV_CREATE(x)，x∈{A,B,C}，默认优先级 1，打印 "A x"
 *   P x pri       ENV_CREATE_PRIORITY(x, pri)，pri 0..65535，打印 "P x pri"
 *   Z size        env_create(binary_user_A, size)，size 0..65536（size=0 触发空镜像守卫），打印 "Z size"
 *   Y             env_create(NULL, 64)（binary=NULL 触发空镜像守卫），打印 "Y"
 *   J             平台 env_alloc(&e,0)（不加载镜像、不入就绪链表），打印 "J 返回码 PCB下标"，用于耗尽空闲 PCB
 *   H             就绪链表头下标，打印 "H idx"（空链表 -1）
 *   T idx         打印 "T idx env_id parent status pri cp0_epc sp"
 *   L             打印就绪链表顺序 "L idx idx ..."（头插，故为创建逆序）
 *   F             空闲 PCB 数，打印 "F n"
 *   C             空闲物理页数，打印 "C n"
 *   Q idx va      打印 "Q 物理地址"（无映射 4294967295）
 *   D idx va      打印 "D 该va处32位小端字"（无映射或跨页 4294967295）
 *   R idx va      打印 "R 该va所映射物理页的pp_ref"（无映射 -1）
 * idx 必须 <64。关键常量：ENV_RUNNABLE=2，ENV_NOT_RUNNABLE=1，USTACKTOP=1073741824，
 *   栈页映射在 USTACKTOP-BY2PG=1073737728。每个非空镜像进程约耗 5 页（页目录1+段1+段页表1+栈1+栈页表1），
 *   空镜像进程耗 3 页（页目录1+栈1+栈页表1）。
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
        } else if (op == 'B') {
            uint32_t k;
            for (k = 0; k < SEGBYTES; k++) { seg_binary[k] = (unsigned char)(k & 0xFFu); }
            printf("B\n");
        } else if (op == 'G') {
            seg_n = 0;
            printf("G\n");
        } else if (op == 'S') {
            uint32_t va = 0, size = 0;
            int perm = 0;
            if (scanf("%u %u %d", &va, &size, &perm) != 3) { return 0; }
            if (va >= UTOP || size > SEGBYTES || perm < 0 || perm > 0xFFF) { return 0; }
            if (seg_n >= MAXSEG) { printf("S %u\n", seg_n); continue; }
            seg_table[seg_n].va = va;
            seg_table[seg_n].size = size;
            seg_table[seg_n].perm = (uint32_t)perm;
            seg_n++;
            printf("S %u\n", seg_n);
        } else if (op == 'E') {
            uint32_t entry = 0;
            if (scanf("%u", &entry) != 1) { return 0; }
            elf_entry = entry;
            printf("E %u\n", entry);
        } else if (op == 'A') {
            char x = 0;
            if (scanf(" %c", &x) != 1) { return 0; }
            if (x == 'A') { ENV_CREATE(user_A); }
            else if (x == 'B') { ENV_CREATE(user_B); }
            else if (x == 'C') { ENV_CREATE(user_C); }
            else { return 0; }
            printf("A %c\n", x);
        } else if (op == 'P') {
            char x = 0;
            int pri = 0;
            if (scanf(" %c %d", &x, &pri) != 2) { return 0; }
            if (pri < 0 || pri > 65535) { return 0; }
            if (x == 'A') { ENV_CREATE_PRIORITY(user_A, pri); }
            else if (x == 'B') { ENV_CREATE_PRIORITY(user_B, pri); }
            else if (x == 'C') { ENV_CREATE_PRIORITY(user_C, pri); }
            else { return 0; }
            printf("P %c %d\n", x, pri);
        } else if (op == 'Z') {
            uint32_t size = 0;
            if (scanf("%u", &size) != 1 || size > 65536u) { return 0; }
            env_create(binary_user_A, size);
            printf("Z %u\n", size);
        } else if (op == 'Y') {
            env_create(NULL, 64u);
            printf("Y\n");
        } else if (op == 'J') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("J %d %d\n", r, env_idx(e));
        } else if (op == 'H') {
            struct Env *e = LIST_FIRST(&env_sched_list);
            printf("H %d\n", env_idx(e));
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u %u\n", idx, envs[idx].env_id,
                   envs[idx].env_parent_id, envs[idx].env_status, envs[idx].env_pri,
                   envs[idx].env_tf.cp0_epc, envs[idx].env_tf.regs[29]);
        } else if (op == 'L') {
            printf("L ");
            print_env_list(&env_sched_list, 1);
        } else if (op == 'F') {
            printf("F %d\n", count_env_list(&env_free_list, 0));
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else if (op == 'D') {
            uint32_t idx = 0, va = 0, pa, word = ~0u;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            pa = env_va2pa(envs[idx].env_pgdir, va);
            if (pa != ~0u && (va & (BY2PG - 1u)) <= BY2PG - 4u) {
                memcpy(&word, phys + pa + (va & (BY2PG - 1u)), sizeof word);
            }
            printf("D %u\n", word);
        } else if (op == 'R') {
            uint32_t idx = 0, va = 0, pa;
            int ref = -1;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            pa = env_va2pa(envs[idx].env_pgdir, va);
            if (pa != ~0u && pa / BY2PG < NPAGE_MAX) { ref = (int)pages[pa / BY2PG].pp_ref; }
            printf("R %d\n", ref);
        } else {
            return 0;
        }
    }
    return 0;
}