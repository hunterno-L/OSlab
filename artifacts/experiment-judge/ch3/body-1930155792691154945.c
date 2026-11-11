/* ===== 学生填写区：实现 lib/env.c 的 env_run() ===== */

/* 题面 3.10「实验任务」原文（逐字）：
 *   1. 实现 env_run 函数：
 *      - 实现保存当前进程状态的步骤。
 *      - 实现设置 curenv 为新进程的步骤。
 *      - 实现使用 lcontext 切换到新进程地址空间的步骤。
 *      - 实现使用 env_pop_tf 恢复环境寄存器并进入用户模式的步骤。
 *   2. 理解 Trapframe 和 TIMESTACK：
 *      - 理解 Trapframe 的结构和作用，以及它如何与 TIMESTACK 相关联。
 *      - 分析代码，找出 TIMESTACK 的定义和作用。
 *
 * 真实签名：void env_run(struct Env *e)
 *
 * MOS 公开语义（四步，顺序不可乱）：
 *   Step 1 【若已有当前进程】把时钟中断现场 TIMESTACK 整个保存回 curenv->env_tf。
 *          第一次运行时 curenv 为 NULL，此时没有旧现场可存，必须跳过这一步
 *          （无条件写 curenv->env_tf 会解引用空指针）。
 *          注意是【整个结构体赋值】，不是只挑 cp0_epc / sp 两个字段：
 *          cp0_status、cp0_badvaddr、cp0_cause 与 32 个通用寄存器都要带回去。
 *   Step 2 curenv = e，并置 curenv->env_status = ENV_RUNNABLE。
 *   Step 3 lcontext(curenv->env_cr3) —— 传【页目录物理地址】env_cr3，
 *          不是内核虚拟地址 env_pgdir。真实内核里 lcontext 是汇编，把它写进
 *          CP0 Context 寄存器完成地址空间切换。
 *   Step 4 env_pop_tf(&curenv->env_tf) —— 传【新进程自己的】Trapframe 地址，
 *          不是 TIMESTACK。真实内核里它恢复 32 个寄存器并 eret 进入用户态，
 *          eret 的落点就是 env_tf.cp0_epc（由 load_icode 设成 ELF 入口）。
 *
 * 【宿主等价适配（务必按此契约填写）】lcontext / env_pop_tf 在真实内核里是 MIPS 汇编，
 *   宿主沙箱不能执行 MIPS 指令，因此平台把它们实现为【记录函数】：每次调用把
 *   「函数种类 + 关键参数 + 调用时刻的 curenv 下标」追加进事件日志，不做真实切换。
 *   这不削弱判题：四步的调用顺序、lcontext 收到的是物理地址还是虚拟地址、
 *   env_pop_tf 收到的是新进程现场还是 TIMESTACK、Step 2 是否先于 Step 3/4 完成，
 *   全部被日志如实记录，驱动用 Y 操作把日志打出来逐条比对。
 *   TIMESTACK / KERNEL_SP 在真实内核里是汇编常量地址，宿主是两个 struct Trapframe
 *   全局量，宏直接给出指针（避免 64 位主机上把 uint32 地址强转指针造成截断）。
 *
 * 平台已提供：curenv / envs[] / TIMESTACK / KERNEL_SP / lcontext / env_pop_tf /
 *             env_alloc / env_create / load_icode / ENV_CREATE 宏 / env_in_range。
 * 提示：W 操作会打印某个进程的 env_cr3 与页目录物理地址，二者应当相等。 */
static void env_run(struct Env *e)
{
    /* finish your code here */
    (void)e;
}

/* ===== 填写区结束 ===== */

#define MAXOPS 96

/* stdin: 第一行 "npage freemem"（1..256 / 0..65536），第二行操作数 n（0..MAXOPS），随后 n 个操作：
 *   I                 env_init()，打印 "I 空闲PCB数"（重复打印 I SKIP）
 *   B                 镜像缓冲填成 seg_binary[i] = i & 0xFF，打印 "B"
 *   G                 清空段表，打印 "G"
 *   S va size perm    追加一段（最多8段，va<UTOP、size<=16384、perm 0..4095），打印 "S 段数"
 *   E entry           设 elf_entry，打印 "E entry"
 *   A x               ENV_CREATE(x)，x∈{A,B,C}，优先级1，打印 "A x"
 *   P x pri           ENV_CREATE_PRIORITY(x,pri)，pri 0..65535，打印 "P x pri"
 *   J                 平台 env_alloc(&e,0)，打印 "J 返回码 PCB下标"
 *   K idx             平台直接把 curenv 指向 envs[idx]（不改状态、不记日志），打印 "K idx"
 *   M epc sp sr badv cause  平台把五个值写进 TIMESTACK 现场，打印 "M epc sp sr badv cause"
 *   X idx             调用【学生填写的】env_run(&envs[idx])，打印 "X idx"
 *   V                 当前 curenv 下标，打印 "V idx"（NULL 为 -1）
 *   T idx             打印 "T idx env_id parent status pri cp0_epc sp"
 *   O idx             打印 "O idx cp0_status cp0_badvaddr cp0_cause"
 *   W idx             打印 "W idx env_cr3 页目录物理地址"（页目录非法时后者 4294967295）
 *   Y                 打印事件日志："Y 记录总数"，随后每条一行 "种类 参数 curenv下标"
 *                     种类 0=lcontext（参数=收到的地址），1=env_pop_tf（参数=现场里的 cp0_epc，
 *                     现场指针非法时为 4294967295）；curenv下标 -1 表示 NULL/越界。
 *                     日志容量 96 条，超出部分只计数不保存。
 *   Z                 清空事件日志，打印 "Z"
 *   H                 就绪链表头下标，打印 "H idx"（空 -1）
 *   L                 就绪链表顺序，打印 "L idx idx ..."（头插故为创建逆序）
 *   F                 空闲 PCB 数，打印 "F n"
 *   C                 空闲物理页数，打印 "C n"
 *   Q idx va          打印 "Q 物理地址"（无映射 4294967295）
 * idx 必须 <64。关键常量：ENV_RUNNABLE=2、ENV_NOT_RUNNABLE=1、USTACKTOP=1073741824、
 *   ENV_USER_SR=268439564（KUp|IEp|IM4|CU0）、栈页映射在 USTACKTOP-BY2PG=1073737728。
 *   env_init 头插建空闲链表，分配顺序 63,62,61...；env_id=(生成号<<7)|下标，生成号从1起递增。
 *   单段非空镜像进程耗5页，空镜像进程耗3页，单纯 env_alloc 占1页（页目录）。
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
        } else if (op == 'J') {
            struct Env *e = NULL;
            int r = env_alloc(&e, 0);
            printf("J %d %d\n", r, env_idx(e));
        } else if (op == 'K') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            curenv = &envs[idx];
            printf("K %u\n", idx);
        } else if (op == 'M') {
            uint32_t epc = 0, sp = 0, sr = 0, badv = 0, cause = 0;
            if (scanf("%u %u %u %u %u", &epc, &sp, &sr, &badv, &cause) != 5) { return 0; }
            timestack_tf.cp0_epc = epc;
            timestack_tf.regs[29] = sp;
            timestack_tf.cp0_status = sr;
            timestack_tf.cp0_badvaddr = badv;
            timestack_tf.cp0_cause = cause;
            printf("M %u %u %u %u %u\n", epc, sp, sr, badv, cause);
        } else if (op == 'X') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            env_run(&envs[idx]);
            printf("X %u\n", idx);
        } else if (op == 'V') {
            printf("V %d\n", env_idx(curenv));
        } else if (op == 'T') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("T %u %u %u %u %u %u %u\n", idx, envs[idx].env_id,
                   envs[idx].env_parent_id, envs[idx].env_status, envs[idx].env_pri,
                   envs[idx].env_tf.cp0_epc, envs[idx].env_tf.regs[29]);
        } else if (op == 'O') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("O %u %u %u %u\n", idx, envs[idx].env_tf.cp0_status,
                   envs[idx].env_tf.cp0_badvaddr, envs[idx].env_tf.cp0_cause);
        } else if (op == 'W') {
            uint32_t idx = 0;
            if (scanf("%u", &idx) != 1 || idx >= NENV) { return 0; }
            printf("W %u %u %u\n", idx, envs[idx].env_cr3, pa_of(envs[idx].env_pgdir));
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
        } else if (op == 'Q') {
            uint32_t idx = 0, va = 0;
            if (scanf("%u %u", &idx, &va) != 2 || idx >= NENV) { return 0; }
            printf("Q %u\n", env_va2pa(envs[idx].env_pgdir, va));
        } else {
            return 0;
        }
    }
    return 0;
}