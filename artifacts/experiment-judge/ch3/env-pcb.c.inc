/* ---- 第三章/第四章共享：MOS 进程管理宿主环境（lib/env.c 的等价骨架） ----
 *
 * 【宿主环境说明】工作区未提供 lib/env.c 原文。本段的函数语义按题面文字 +
 * MOS/MIPS3000 公开语义构造，属等价示例，不是原文逐字转录。
 *
 * 宿主等价适配：
 *   1. envs[] 是静态数组（真实内核里它由 mips_vm_init 用 alloc 分配、经 UENVS 窗口访问），
 *      因为第三章考查的是进程管理逻辑而不是启动期分配；物理内存 arena 仍沿用第二章模型。
 *   2. TIMESTACK / KERNEL_SP 在真实内核里是汇编常量地址，宿主改为两个 struct Trapframe
 *      全局量，宏直接给出指针 —— 避免在 64 位主机上把 uint32 地址强转成指针造成截断 UB。
 *   3. lcontext() / env_pop_tf() 在真实内核里是 MIPS 汇编（写 CP0 Context、恢复寄存器、eret），
 *      宿主用【记录函数】等价替代：只把调用参数与调用时刻的 curenv 记进事件日志，
 *      不做真实上下文切换。学生 env_run 的四步调用顺序仍被真实校验。
 *   4. panic 不终止进程；一切链表步行带 NENV 步数上限；一切学生返回的指针先做范围校验。
 */

LIST_HEAD(Env_list, Env);

static MOS_UNUSED struct Env envs[NENV];
static MOS_UNUSED struct Env_list env_free_list;
static MOS_UNUSED struct Env_list env_sched_list;
static MOS_UNUSED struct Env *curenv;
static MOS_UNUSED uint32_t envid_gen;      /* mkenvid 的生成号计数器 */

/* 宿主等价：TIMESTACK（时钟中断现场）与 KERNEL_SP（系统调用现场） */
static MOS_UNUSED struct Trapframe timestack_tf;
static MOS_UNUSED struct Trapframe kernsp_tf;
#define TIMESTACK (&timestack_tf)
#define KERNEL_SP (&kernsp_tf)

/* include/env.h 的真实 mkenvid：低 LOG2NENV 位是下标，高位是生成号，
 * 这样同一个 PCB 槽位被复用后 env_id 不同，旧 id 会被 envid2env 拒绝。 */
static MOS_UNUSED uint32_t mkenvid(struct Env *e)
{
    envid_gen++;
    return (envid_gen << (1u + LOG2NENV)) | (uint32_t)(e - envs);
}

/* ---- 驱动侧保护 ---- */

static MOS_UNUSED int env_in_range(struct Env *e)
{
    return e != NULL && e >= envs && e < envs + NENV;
}

static MOS_UNUSED int env_idx(struct Env *e)
{
    if (!env_in_range(e)) { return -1; }
    return (int)(e - envs);
}

static MOS_UNUSED int in_env_free_list(struct Env *target)
{
    struct Env *e = LIST_FIRST(&env_free_list);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { return 0; }
        if (e == target) { return 1; }
        e = LIST_NEXT(e, env_link);
        steps++;
    }
    return 0;
}

static MOS_UNUSED int in_sched_list(struct Env *target)
{
    struct Env *e = LIST_FIRST(&env_sched_list);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { return 0; }
        if (e == target) { return 1; }
        e = LIST_NEXT(e, env_sched_link);
        steps++;
    }
    return 0;
}

/* 学生可能把 env_pgdir 留成 NULL 或写成野指针；页目录必须容得下 1024 个 Pde */
static MOS_UNUSED int pgdir_ok(Pde *p)
{
    unsigned char *b;
    if (p == NULL) { return 0; }
    if (p == boot_pgdir) { return 1; }
    b = (unsigned char *)p;
    if (b < phys || b + 1024u * (uint32_t)sizeof(Pde) > phys + sizeof phys) { return 0; }
    return ((uint32_t)(b - phys) % (uint32_t)sizeof(Pde)) == 0;
}

/* 驱动自有的地址翻译：不依赖学生实现，对越界页目录/页表项免疫 */
static MOS_UNUSED uint32_t env_va2pa(Pde *pgdir, uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    pde = pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    if (!(pte & PTE_V)) { return ~0u; }
    if (PTE_ADDR(pte) + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    return PTE_ADDR(pte);
}

/* va 处页表项原始值；无页表/无映射返回 ~0 */
static MOS_UNUSED uint32_t env_pte(Pde *pgdir, uint32_t va)
{
    Pde pde;
    uint32_t base;
    Pte pte;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    pde = pgdir[PDX(va)];
    if (!(pde & PTE_V)) { return ~0u; }
    base = PTE_ADDR(pde);
    if (base + BY2PG > (uint32_t)sizeof phys) { return ~0u; }
    memcpy(&pte, phys + base + PTX(va) * (uint32_t)sizeof(Pte), sizeof pte);
    return pte;
}

/* 内核段（PDX(UTOP)..1023，跳过 UVPT 自映射槽）中与 boot_pgdir 逐字相同的项数。
 * 满分 = 1024 - PDX(UTOP) - 1 = 767。跳过 UVPT 是因为它本来就该被自映射覆盖。 */
static MOS_UNUSED uint32_t kernel_pde_copied(Pde *pgdir)
{
    uint32_t i, cnt = 0;
    if (!pgdir_ok(pgdir)) { return ~0u; }
    for (i = PDX(UTOP); i < 1024u; i++) {
        if (i == PDX(UVPT)) { continue; }
        if (pgdir[i] == boot_pgdir[i]) { cnt++; }
    }
    return cnt;
}

/* 学生可能把 env_pgdir 留成 NULL 或野指针，先校验再算物理地址 */
static MOS_UNUSED int ptr_in_phys(const void *p)
{
    const unsigned char *b = (const unsigned char *)p;
    return b >= phys && b < phys + sizeof phys;
}

static MOS_UNUSED uint32_t pa_of(const void *p)
{
    if (!ptr_in_phys(p)) { return ~0u; }
    return (uint32_t)((const unsigned char *)p - phys);
}

/* 页目录所在物理页的 pp_ref；页目录非法时返回 -1 */
static MOS_UNUSED int pgdir_pp_ref(struct Env *e)
{
    uint32_t pa;
    if (!env_in_range(e) || !pgdir_ok(e->env_pgdir)) { return -1; }
    pa = pa_of(e->env_pgdir);
    if (pa == ~0u || pa % BY2PG != 0 || pa / BY2PG >= NPAGE_MAX) { return -1; }
    return (int)pages[pa / BY2PG].pp_ref;
}

/* ---- lcontext / env_pop_tf 的宿主记录替身 ----
 * 三个替身函数拆在 env-recorder 段（装配时紧跟本段）；数组与 MAXREC 留在这里。 */

#define MAXREC 96
static MOS_UNUSED int rec_kind[MAXREC];      /* 0 = lcontext, 1 = env_pop_tf */
static MOS_UNUSED uint32_t rec_arg[MAXREC];  /* lcontext: 页目录物理地址; env_pop_tf: cp0_epc */
static MOS_UNUSED int rec_cur[MAXREC];       /* 调用时刻 curenv 的下标，-1 = NULL/越界 */
static MOS_UNUSED int rec_n;

/* ---- 状态复位与驱动辅助 ---- */

/* 宿主等价：真实内核启动后 boot_pgdir 已含内核段映射（由 mips_vm_init 建立）。
 * 这里用启动期 bump 分配器建立同样的内核映射，供 env_setup_vm 拷贝到各进程页目录。
 * 只映射少量页，避免 1MB 宿主 arena 被耗尽；映射目标都是随后 page_init 标记为已用的页。
 * 必须在 page_init 之前调用（真实启动顺序：mips_vm_init -> page_init）。 */
static MOS_UNUSED void boot_kernel_map(void)
{
    uint32_t k;
    for (k = 0; k < 4u; k++) {
        Pte *pte = boot_pgdir_walk(boot_pgdir, UTOP + k * BY2PG, 1);
        if (pte == NULL) { return; }
        *pte = (k * BY2PG) | PTE_V | PTE_R;
    }
    for (k = 0; k < 2u; k++) {
        Pte *pte = boot_pgdir_walk(boot_pgdir, UENVS + k * BY2PG, 1);
        if (pte == NULL) { return; }
        *pte = ((2u + k) * BY2PG) | PTE_V | PTE_R;
    }
}

/* 宿主测试夹具（可选）：真实内核的 boot_pgdir 在 UTOP 以下是空的，因为那 1GB 属于
 * 用户态地址空间。这里故意留一条「boot 期遗留的用户态映射」（va 0x00400000 -> pa 4096），
 * 用来检验 env_setup_vm 是否只拷内核段 PDX(UTOP)..1023，而不是整张 1024 项页目录。
 * 必须在 page_init 之前建立：否则 bump 分配器会把已挂上空闲链表的页当页表发出去。 */
static MOS_UNUSED void boot_stale_user_map(void)
{
    Pte *pte = boot_pgdir_walk(boot_pgdir, 0x00400000u, 1);
    if (pte == NULL) { return; }
    *pte = 0x00001000u | PTE_V | PTE_R;
}

static MOS_UNUSED void reset_env_state_ex(uint32_t np, uint32_t fm, uint32_t stale)
{
    uint32_t i;
    reset_state(np, fm);
    boot_kernel_map();
    if (stale) { boot_stale_user_map(); }
    page_init();
    for (i = 0; i < NENV; i++) {
        memset(&envs[i], 0, sizeof envs[i]);
        envs[i].env_status = ENV_FREE;
    }
    LIST_INIT(&env_free_list);
    LIST_INIT(&env_sched_list);
    curenv = NULL;
    envid_gen = 0;
    memset(&timestack_tf, 0, sizeof timestack_tf);
    memset(&kernsp_tf, 0, sizeof kernsp_tf);
    rec_n = 0;
    memset(rec_kind, 0, sizeof rec_kind);
    memset(rec_arg, 0, sizeof rec_arg);
    memset(rec_cur, 0, sizeof rec_cur);
}

static MOS_UNUSED void reset_env_state(uint32_t np, uint32_t fm)
{
    reset_env_state_ex(np, fm, 0);
}

/* 平台侧的进程销毁（真实 MOS 的 env_destroy 简化版）：仅供驱动构造场景，
 * 学生不填写。已空闲的进程不重复入链，避免错误实现造成环。 */
static MOS_UNUSED void env_free(struct Env *e)
{
    if (!env_in_range(e)) { return; }
    if (in_sched_list(e)) { LIST_REMOVE(e, env_sched_link); }
    e->env_status = ENV_FREE;
    e->env_id = 0;
    if (!in_env_free_list(e)) { LIST_INSERT_HEAD(&env_free_list, e, env_link); }
}

static MOS_UNUSED void print_env_list(struct Env_list *head, int sched)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0, first = 1;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { break; }
        if (!first) { printf(" "); }
        printf("%d", (int)(e - envs));
        first = 0;
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    printf("\n");
}

/* 链表长度（带步数上限，学生写出环时不死循环） */
static MOS_UNUSED int count_env_list(struct Env_list *head, int sched)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0;
    while (e != NULL && steps < (int)NENV) {
        if (!env_in_range(e)) { break; }
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    return steps;
}

/* 空闲物理页数（带步数上限） */
static MOS_UNUSED int count_free_pages(void)
{
    struct Page *p = LIST_FIRST(&page_free_list);
    int steps = 0;
    while (p != NULL && steps < (int)NPAGE_MAX) {
        if (!page_in_range(p)) { break; }
        p = LIST_NEXT(p, pp_link);
        steps++;
    }
    return steps;
}

/* 打印链表前 k 个下标（k 由 stdin 给出，带步数上限） */
static MOS_UNUSED void print_env_list_n(struct Env_list *head, int sched, int k)
{
    struct Env *e = LIST_FIRST(head);
    int steps = 0, first = 1;
    while (e != NULL && steps < (int)NENV && steps < k) {
        if (!env_in_range(e)) { break; }
        if (!first) { printf(" "); }
        printf("%d", (int)(e - envs));
        first = 0;
        e = sched ? LIST_NEXT(e, env_sched_link) : LIST_NEXT(e, env_link);
        steps++;
    }
    printf("\n");
}