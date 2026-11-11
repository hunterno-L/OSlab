/* ===== 学生填写区：实现 lib/env.c 的 load_icode_mapper() ===== */

/* 题面 3.6：把 ELF 的一个段装进进程 e 的地址空间，按段大小分配内存页并增加引用计数。
 * 真实签名（load_elf 逐段回调它）：
 *   int load_icode_mapper(void *binary, uint32_t size, uint32_t va, int perm, struct Env *e)
 *   binary 指向该段在 ELF 文件里的字节流，size 是段字节数，va 是段的目标虚拟地址，
 *   perm 是要写进 PTE 的权限位。
 * 要求：
 *   Step 1: 起止地址对齐到页 —— start = ROUNDDOWN(va, BY2PG)，end = ROUND(va + size, BY2PG)；
 *           size == 0 时什么都不做，直接返回 0；
 *   Step 2: 每覆盖一页就 page_alloc 一页，失败原样返回错误码；
 *   Step 3: 把 binary 中对应字节拷进该页 ——【镜像第 0 字节必须落在虚拟地址 va 处】，
 *           所以首页的目的页内偏移是 off = va - start（不是页首！），其余页偏移为 0；
 *           首页最多拷 BY2PG - off 字节，【末页只拷剩余字节】，
 *           没拷到的部分保持 page_alloc 清零后的 0；
 *   Step 4: page_insert(e->env_pgdir, p, v, perm) 建立映射，失败原样返回错误码。
 * 【引用计数约定】page_insert 自身会 pp_ref++（实验 2.7 已验证的契约），
 *   所以本函数【不要】再额外 p->pp_ref++，否则同一页被计数两次、永远无法回收。
 *   每页净增 1 就是题面「增加引用计数」的落点。
 * 平台已提供：page_alloc / page_insert / page2kva / ROUND / ROUNDDOWN / env_alloc。 */
static int load_icode_mapper(void *binary, uint32_t size, uint32_t va,
                             int perm, struct Env *e)
{
    /* finish your code here */
    (void)binary;
    (void)size;
    (void)va;
    (void)perm;
    (void)e;
    return -E_INVAL;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 48

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I            env_init()，打印 "I 空闲PCB数"（重复打印 I SKIP）
 *   A            env_alloc(&e,0)，打印 "A 返回码 PCB下标"
 *   U idx        选定后续 M 操作的目标进程，打印 "U idx"
 *   B            把镜像缓冲填成 seg_binary[i] = i & 0xFF（位置相关图样，便于校验拷贝偏移），打印 "B"
 *   M va size perm  调学生 load_icode_mapper(seg_binary,size,va,perm,目标进程)，打印 "M 返回码"
 *                va 必须 < UTOP(1073741824)，size <= 16384，perm 0..4095，否则驱动直接结束
 *   Q idx va     打印 "Q 物理地址"（无映射 4294967295）
 *   P idx va     打印 "P 原始PTE"（含权限位；无映射 4294967295）
 *   D idx va     打印 "D 该va处的32位小端字"（无映射或跨页时 4294967295）
 *   R idx va     打印 "R 该va所映射物理页的pp_ref"（无映射 -1）
 *   C            打印空闲物理页数
 * idx 必须 <64。M 之前必须先 U，否则驱动直接结束。
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
        } else if (op == 'M') {
            uint32_t va = 0, size = 0;
            int perm = 0, r;
            if (scanf("%u %u %d", &va, &size, &perm) != 3) { return 0; }
            if (va >= UTOP || size > SEGBYTES || perm < 0 || perm > 0xFFF) { return 0; }
            if (tgt == NULL) { return 0; }
            r = load_icode_mapper((void *)seg_binary, size, va, perm, tgt);
            printf("M %d\n", r);
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