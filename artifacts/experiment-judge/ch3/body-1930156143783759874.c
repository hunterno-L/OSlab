/* ===== 学生填写区：实现 lib/sched.c 的 sched_yield()（时钟中断里的时间片轮转调度） ===== */

/* 题面 3.14 原文：
 *   任务描述：「在MIPS3000处理器中，实现一个基于时间片轮转算法的进程调度机制，
 *              通过时钟中断触发进程调度，确保进程能够被正确调度。」
 *   实验任务：
 *     1. 补充 sched_yield 函数：根据注释，完成 sched_yield 函数的编写，
 *        该函数用于在时钟中断发生时进行进程调度。
 *     2. 修改 env.c 文件：根据调度方法对 env.c 中的部分函数进行修改，
 *        确保进程能够被正确调度。
 *     3. 实现进程状态管理：编写代码管理进程的状态，特别是就绪状态的进程。
 *     4. 实现链表操作：编写代码使用两个链表存储所有就绪状态的进程，
 *        并实现进程的插入和切换操作。
 *
 * 真实签名：void sched_yield(void) —— 无参无返回，由时钟中断服务程序每滴答调用一次。
 *
 * 【宿主环境说明】工作区未随附 lib/sched.c 原文。题面任务 4 提到
 *   「两个链表」，真实 MOS 的部分版本用 env_sched_list[2] 分高/低优先级两条就绪队列；
 *   本宿主按另一等价公开语义构造：**单条就绪链表 env_sched_list + env_pri 即该进程
 *   一轮能连续运行的时钟滴答数**，并把算法逐条写在下面，保证可判题。
 *   概念同构点全部保留：时间片轮转、优先级决定时间片长度、只有 ENV_RUNNABLE 参与、
 *   用链表管理就绪进程、由时钟中断驱动、进程插入与切换。
 *   题面任务 2「修改 env.c」在本宿主里已由平台件完成（env_create_priority 头插
 *   env_sched_list、env_run 置 ENV_RUNNABLE），学生只需填 sched_yield 本体。
 *
 * 必须实现的算法（顺序与边界都不可改）：
 *   用两个【跨调用保持】的静态量：当前游标 cur（struct Env *，初值 NULL）与
 *   剩余时间片 left（int，初值 0）。
 *   一、若 cur == NULL 或 left == 0 或 cur 已不在 envs[] 内 或 cur 已不是 ENV_RUNNABLE，
 *       则需要重新挑选：
 *       1. 起点：cur 为 NULL（或不在 envs[] 内）时取链表头 LIST_FIRST(&env_sched_list)；
 *          否则取【cur 的下一个】LIST_NEXT(cur, env_sched_link) —— 不是链表头！
 *          从下一个开始才能保证轮转公平，从头开始会让链表头进程饿死其他进程。
 *       2. 沿 env_sched_link 环形查找第一个 env_status == ENV_RUNNABLE 的进程：
 *          走到 NULL 就绕回 LIST_FIRST；每步都校验指针落在 envs[] 内，
 *          且最多走 NENV 步（防学生写出的环导致死循环）。
 *       3. 一个就绪进程都找不到（含空链表）就【直接 return】，
 *          绝不能调用 env_run —— 没有可运行的进程时切换现场是错的。
 *       4. 找到则 cur = 该进程，left = (cur->env_pri == 0) ? 1 : (int)cur->env_pri。
 *          env_pri 为 0 是合法值，必须钳到至少 1 个滴答，否则该进程永远选不上。
 *   二、left--（先挑选后递减：pri 为 p 的进程恰好连续跑 p 个滴答）。
 *   三、env_run(cur) —— 保存旧现场、切 curenv、lcontext、env_pop_tf 都由它完成。
 *
 * 平台已提供：env_sched_list / envs[] / curenv / env_run / env_create /
 *             env_create_priority / ENV_CREATE_PRIORITY 宏 / env_free / env_in_range。
 * 注意：env_run 是平台件（实验 3.10 的目标函数，本实验按已实现提供），
 *       它每次调用都会往事件日志里追加 lcontext 与 env_pop_tf 两条记录，
 *       驱动用 Y 操作把日志打出来 —— 于是「每个滴答究竟选中了哪个进程」完全可观测。 */
static void sched_yield(void)
{
    /* finish your code here */
}

/* ===== 填写区结束 ===== */

#define MAXOPS 96

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I                 env_init()，打印 "I 空闲PCB数"（重复打印 I SKIP）
 *   B                 镜像缓冲填成 seg_binary[i] = i & 0xFF，打印 "B"
 *   G                 清空段表，打印 "G"
 *   S va size perm    追加一段（最多8段，va<UTOP、size<=16384、perm 0..4095），打印 "S 段数"
 *   E entry           设 elf_entry，打印 "E entry"
 *   M epc sp sr badv cause  平台把五个值写进 TIMESTACK 时钟中断现场，打印同样五个值
 *   A x               ENV_CREATE(x)，x∈{A,B,C}，优先级1，打印 "A x"
 *   P x pri           ENV_CREATE_PRIORITY(x,pri)，pri 0..65535，打印 "P x pri"
 *   J                 平台 env_alloc(&e,0)（不装镜像、不入就绪链表），打印 "J 返回码 下标"
 *   U idx st          平台把 envs[idx].env_status 设为 st（0=FREE,1=NOT_RUNNABLE,2=RUNNABLE），
 *                     打印 "U idx st"。只改状态，不动链表。
 *   D idx             平台 env_free(&envs[idx])：从就绪链表摘下、置 FREE、清 env_id、
 *                     放回空闲链表（不回收物理页），打印 "D idx"
 *   N                 一个时钟滴答：调用【学生填写的】sched_yield()，打印 "N"
 *   V                 当前 curenv 下标，打印 "V idx"（NULL 为 -1）
 *   T idx             打印 "T idx env_id parent status pri cp0_epc sp"
 *   Y                 打印事件日志："Y 记录总数"，随后每条一行 "种类 参数 curenv下标"。
 *                     种类 0=lcontext（参数=页目录物理地址），1=env_pop_tf（参数=现场 cp0_epc）。
 *                     env_run 每次调用追加两条，故【记录总数 = 2 × 实际发生的切换次数】；
 *                     每条的 curenv下标 就是该滴答选中的进程。容量96条，超出只计数不保存。
 *   Z                 清空事件日志，打印 "Z"
 *   H                 就绪链表头下标，打印 "H idx"（空 -1）
 *   L                 就绪链表顺序，打印 "L idx idx ..."（头插故为创建逆序）
 *   F                 空闲 PCB 数，打印 "F n"
 *   C                 空闲物理页数，打印 "C n"
 * idx 必须 <64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、ENV_FREE=0、
 *   USTACKTOP=1073741824、ENV_USER_SR=268439564、NENV=64。
 *   env_init 头插建空闲链表，分配顺序 63,62,61...；env_id=(生成号<<7)|下标，生成号从1起递增。
 *   ENV_CREATE_PRIORITY 头插 env_sched_list，故就绪链表顺序是创建的逆序。
 *   单段非空镜像进程耗5页，空镜像进程耗3页，单纯 env_alloc 占1页。
 *   建议每个用例的 N 操作不超过 40 个，否则日志会超过 96 条容量而被截断。
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
        } else if (op == 'M') {
            uint32_t epc = 0, sp = 0, sr = 0, badv = 0, cause = 0;
            if (scanf("%u %u %u %u %u", &epc, &sp, &sr, &badv, &cause) != 5) { return 0; }
            timestack_tf.cp0_epc = epc;
            timestack_tf.regs[29] = sp;
            timestack_tf.cp0_status = sr;
            timestack_tf.cp0_badvaddr = badv;
            timestack_tf.cp0_cause = cause;
            printf("M %u %u %u %u %u\n", epc, sp, sr, badv, cause);
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
        } else if (op == 'J') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("J %d %d\n", r, env_idx(e));
        } else if (op == 'U') {
            uint32_t idx = 0, st = 0;
            if (scanf("%u %u", &idx, &st) != 2 || idx >= NENV || st > 2u) { return 0; }
            envs[idx].env_status = st;
            printf("U %u %u\n", idx, st);
        } else if (op == 'D') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_free(&envs[idx]);
            printf("D %u\n", idx);
        } else if (op == 'N') {
            sched_yield();
            printf("N\n");
        } else if (op == 'V') {
            printf("V %d\n", env_idx(curenv));
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u %u\n", idx, envs[idx].env_id,
                   envs[idx].env_parent_id, envs[idx].env_status, envs[idx].env_pri,
                   envs[idx].env_tf.cp0_epc, envs[idx].env_tf.regs[29]);
        } else if (op == 'Y') {
            int k, lim = rec_n < MAXREC ? rec_n : MAXREC;
            printf("Y %d\n", rec_n);
            for (k = 0; k < lim; k++) {
                printf("%d %u %d\n", rec_kind[k], rec_arg[k], rec_cur[k]);
            }
        } else if (op == 'Z') {
            rec_n = 0;
            printf("Z\n");
        } else if (op == 'H') {
            struct Env *e = LIST_FIRST(&env_sched_list);
            printf("H %d\n", env_idx(e));
        } else if (op == 'L') {
            printf("L ");
            print_env_list(&env_sched_list, 1);
        } else if (op == 'F') {
            printf("F %d\n", count_env_list(&env_free_list, 0));
        } else if (op == 'C') {
            printf("C %d\n", count_free_pages());
        } else {
            return 0;
        }
    }
    return 0;
}