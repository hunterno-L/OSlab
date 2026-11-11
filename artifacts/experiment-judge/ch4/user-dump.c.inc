/* ---- 第四章：用户态整页内容观察件（只随 pgfault 实验装配） ----
 *
 * 为什么需要它：page_alloc 会把新页清零，若源页也全是 0，则「复制了整页」与
 * 「根本没复制」在页表项与空闲页数上完全同形，pgfault 的 user_memcpy 那一步就判不出来。
 * 本件打印整页字节校验和与前 8 字节，配合驱动 W 操作符从内核段搬入可辨认的非零内容
 * （va=UTOP+8192 映射到 PA 8192，即启动期第一张页表 —— bump 分配器从 fm 起分配，
 *   其前四项为 3/4099/8195/12291；va=UTOP 本身映射到全零的 PA 0，不能做内容源），
 * 使「内容逐字保留」成为可判题的断言。
 * 依赖 env-user 段的全局 env 与 env-usermem 段的翻译设施。 */

static MOS_UNUSED void dump_user_page(uint32_t va)
{
    uint32_t base = ROUNDDOWN(va, BY2PG), sum = 0, i, pa;
    if (env == NULL || !env_in_range(env)) { printf("Z %u -1\n", base); return; }
    pa = env_va2pa(env->env_pgdir, base);
    if (pa == ~0u) { printf("Z %u -1\n", base); return; }
    for (i = 0; i < BY2PG; i++) { sum += phys[pa + i]; }
    printf("Z %u %u", base, sum);
    for (i = 0; i < 8u; i++) { printf(" %u", phys[pa + i]); }
    printf("\n");
}