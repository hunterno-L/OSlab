/* ===== 学生填写区：在 init/init.c 中应用 ENV_CREATE_PRIORITY 宏创建两个用户进程 ===== */

/* 题面 3.9 原文要求（逐字）：
 *   「将 ENV_CREATE_PRIORITY(user_A, 2); 和 ENV_CREATE_PRIORITY(user_B, 1);
 *     这两条语句添加到 init.c 文件中，以初始化创建两个具有不同优先级的进程。」
 *
 * 真实内核里这两条语句直接写在 init/init.c 的初始化流程中。宿主沙箱是单文件程序，
 * 没有独立的 init.c 翻译单元，因此把这段初始化代码收进一个函数 init_user_envs()，
 * 由检查驱动在操作系统启动流程的对应时刻调用它 —— 语句本身、宏名、镜像名、
 * 优先级数值与调用顺序都与原文一致，没有改成别的东西。
 *
 * 你要填写的就是这两条宏调用，顺序必须是先 user_A(优先级 2) 再 user_B(优先级 1)。
 *
 * 平台已提供（不要自己重新定义）：
 *   ENV_CREATE(x)             展开为 env_create(binary_x, sizeof binary_x)，优先级恒为 1
 *   ENV_CREATE_PRIORITY(x,y)  展开为 env_create_priority(binary_x, sizeof binary_x, (y))
 *   binary_user_A / binary_user_B / binary_user_C  三个 64 字节镜像缓冲
 *   env_create / env_create_priority  进程创建原语（实验 3.8 的目标函数，本实验按已实现提供）
 *
 * 注意宏的参数是【镜像名后缀】而不是字符串：宏体做 token 粘贴 binary_##x，
 * 所以必须写 ENV_CREATE_PRIORITY(user_A, 2)，写 ENV_CREATE_PRIORITY(A, 2) 会展开成
 * binary_A 这个不存在的符号，编译直接失败。
 *
 * 为什么这不是「背答案」：驱动用 stdin 控制启动前的机器状态（空闲 PCB 被占用了几个、
 * 镜像有几段、入口地址是多少、物理页还剩多少），因此创建出来的进程下标、env_id、
 * 页表内容、剩余页数都随输入变化，直接 printf 固定文本无法通过。 */
static void init_user_envs(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */

#define MAXOPS 96

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I             env_init()，打印 "I 空闲PCB数"（重复调用打印 I SKIP）
 *   B             把镜像缓冲填成 seg_binary[i] = i & 0xFF，打印 "B"
 *   G             清空段表，打印 "G"
 *   S va size perm  向段表追加一段（最多 8 段，va<UTOP、size<=16384、perm 0..4095），打印 "S 当前段数"
 *   E entry       设 elf_entry = entry，打印 "E entry"
 *   J             平台 env_alloc(&e,0) 占用一个 PCB（不装镜像、不入就绪链表），打印 "J 返回码 PCB下标"
 *   N             调用【学生填写的】init_user_envs()，打印 "N"
 *   H             就绪链表头下标，打印 "H idx"（空链表 -1）
 *   L             就绪链表顺序，打印 "L idx idx ..."（头插，故为创建逆序）
 *   T idx         打印 "T idx env_id parent status pri cp0_epc sp"
 *   F             空闲 PCB 数，打印 "F n"
 *   C             空闲物理页数，打印 "C n"
 *   Q idx va      打印 "Q 物理地址"（无映射 4294967295）
 *   D idx va      打印 "D 该va处32位小端字"（无映射或跨页 4294967295）
 *   R idx va      打印 "R 该va所映射物理页的pp_ref"（无映射 -1）
 * idx 必须 <64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、USTACKTOP=1073741824，
 *   栈页映射在 USTACKTOP-BY2PG=1073737728。env_init 用头插建空闲链表，故分配顺序是 63,62,61...
 *   env_id = (生成号 << 7) | 下标，生成号从 1 起每次分配递增。
 *   每个非空镜像进程约耗 5 页，空镜像进程耗 3 页，单纯 env_alloc 占 1 页（页目录）。
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
        } else if (op == 'J') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("J %d %d\n", r, env_idx(e));
        } else if (op == 'N') {
            init_user_envs();
            printf("N\n");
        } else if (op == 'H') {
            struct Env *e = LIST_FIRST(&env_sched_list);
            printf("H %d\n", env_idx(e));
        } else if (op == 'L') {
            printf("L ");
            print_env_list(&env_sched_list, 1);
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u %u\n", idx, envs[idx].env_id,
                   envs[idx].env_parent_id, envs[idx].env_status, envs[idx].env_pri,
                   envs[idx].env_tf.cp0_epc, envs[idx].env_tf.regs[29]);
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