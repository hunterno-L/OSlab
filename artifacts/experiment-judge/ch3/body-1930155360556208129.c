/* ===== 学生填写区：实现 lib/env.c 的 load_icode() ===== */

/* 题面 3.7：把 ELF 镜像装进进程地址空间，设置程序计数器与初始栈。
 * 真实签名：void load_icode(struct Env *e, uint32_t entry)
 *   Step 1: 调 load_elf(e, load_icode_mapper) 逐段加载镜像。
 *           load_elf 返回非 0 时必须【立即返回】，不得继续设置入口与栈
 *           （否则进程会带着半个地址空间被调度）；
 *   Step 2: e->env_tf.cp0_epc = entry —— env_pop_tf 的 eret 就跳到这个地址。
 *           entry 原样赋值，不做页对齐、不做范围裁剪；
 *   Step 3: page_alloc 一页作初始栈，失败就返回；
 *           page_insert(e->env_pgdir, p, USTACKTOP - BY2PG, PTE_R | PTE_V)；
 *           e->env_tf.regs[29] = USTACKTOP。
 * 【引用计数约定】page_insert 自身会 pp_ref++（实验 2.7 契约，与 3.6 一致），
 *           这里【不要】再额外 p->pp_ref++，栈页 pp_ref 恒为 1。
 * 平台已提供：load_elf / load_icode_mapper / page_alloc / page_insert / env_alloc。
 * 宿主等价：load_elf 遍历由 stdin 给出的段表（S 操作追加），逐段回调 load_icode_mapper，
 *           回调签名、调用顺序、每段一次、遇错即停的语义与真实 ELF 加载器一致。 */
static void load_icode(struct Env *e, uint32_t entry)
{
    /* finish your code here */
    (void)e;
    (void)entry;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 48

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I             env_init()，打印 "I 空闲PCB数"（重复打印 I SKIP）
 *   A             env_alloc(&e,0)，打印 "A 返回码 PCB下标"
 *   U idx         选定后续 L 操作的目标进程，打印 "U idx"
 *   B             把镜像缓冲填成 seg_binary[i] = i & 0xFF，打印 "B"
 *   G             清空段表，打印 "G"
 *   S va size perm  向段表追加一段（最多 8 段），打印 "S 当前段数"
 *                 va < UTOP，size <= 16384，perm 0..4095，否则驱动直接结束
 *   L entry       调学生 load_icode(目标进程, entry)，打印 "L"（函数无返回值）
 *   X idx         把该 PCB 的 sp 污染成 0xDEADBEEF、cp0_epc 污染成 0x12345678，
 *                 用于检验 load_icode 是否真的重新设置了这两个字段，打印 "X idx"
 *   N idx         打印 "N idx cp0_epc sp"
 *   Q idx va      打印 "Q 物理地址"（无映射 4294967295）
 *   P idx va      打印 "P 原始PTE"
 *   D idx va      打印 "D 该va处的32位小端字"（无映射或跨页 4294967295）
 *   R idx va      打印 "R 该va所映射物理页的pp_ref"（无映射 -1）
 *   C             打印空闲物理页数
 * idx 必须 <64。L 之前必须先 U，否则驱动直接结束。
 * 关键常量：USTACKTOP = 1073741824，栈页映射在 USTACKTOP-BY2PG = 1073737728。
 */
int main(void)
{
    uint32_t np = 0, fm = 0;
    int n = 0, i, did_i = 0;
    struct Env *tgt = NULL;
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
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("A %d %d\n", r, env_idx(e));
        } else if (op == 'U') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            tgt = &envs[idx];
            printf("U %u\n", idx);
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
        } else if (op == 'L') {
            uint32_t entry = 0;
            if (scanf("%u", &entry) != 1) { return 0; }
            if (tgt == NULL) { return 0; }
            elf_entry = entry;
            load_icode(tgt, entry);
            printf("L\n");
        } else if (op == 'X') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            envs[idx].env_tf.regs[29] = 0xDEADBEEFu;
            envs[idx].env_tf.cp0_epc = 0x12345678u;
            printf("X %u\n", idx);
        } else if (op == 'N') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("N %u %u %u\n", idx, envs[idx].env_tf.cp0_epc, envs[idx].env_tf.regs[29]);
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else if (op == 'P') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("P %u\n", env_pte(envs[idx].env_pgdir, va));
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
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else {
            return 0;
        }
    }
    return 0;
}