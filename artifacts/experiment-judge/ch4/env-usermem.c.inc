/* ---- 第四章共享：用户态访存 / 页表观察窗口的宿主等价 ----
 *
 * 【宿主环境说明】工作区未随附 user/fork.c、user/pgfault.c 原文。
 * phys[] 只有 1MB 而用户虚拟地址是 32 位（如 UXSTACKTOP=0x3F000000），不能直接当指针
 * 解引用。真实用户代码的 memcpy 与 vpt[] 页表观察窗口，在宿主分别等价于 user_memcpy
 * 与 user_pte，两者都经 env 的页表翻译到 phys[]。依赖 env-user 段的全局 env。
 */

#define VPN(va) (((uint32_t)(va)) >> 12)

/* vpt[VPN(va)] 的宿主等价：读 env 自己页表里 va 处的页表项。无页表/无映射返回 0
 * （真实硬件会再次缺页，宿主不能真的 fault，故用 0 表示该项无效，调用方一律先查 PTE_V）。 */
static MOS_UNUSED uint32_t user_pte(uint32_t va)
{
    uint32_t p;
    if (env == NULL || !env_in_range(env)) { return 0; }
    p = env_pte(env->env_pgdir, va);
    return (p == ~0u) ? 0u : p;
}

/* 用户态 memcpy 的宿主等价：逐字节经 env 页表翻译后在 phys[] 内搬运。
 * env_va2pa 只给出物理页基址，页内偏移必须自己补上，否则跨页/页内拷贝都会错位。
 * 任一端未映射即返回 -E_INVAL；n 由驱动限制在 BY2PG 以内，故 base+offset 不会越出 arena。 */
static MOS_UNUSED int user_memcpy(uint32_t dstva, uint32_t srcva, uint32_t n)
{
    uint32_t i;
    if (env == NULL || !env_in_range(env)) { return -E_BAD_ENV; }
    for (i = 0; i < n; i++) {
        uint32_t sv = srcva + i, dv = dstva + i;
        uint32_t sp = env_va2pa(env->env_pgdir, sv);
        uint32_t dp = env_va2pa(env->env_pgdir, dv);
        if (sp == ~0u || dp == ~0u) { return -E_INVAL; }
        phys[dp + (dv & (BY2PG - 1u))] = phys[sp + (sv & (BY2PG - 1u))];
    }
    return 0;
}

/* 宿主等价常量：真实 MOS 把 pgfault 的 32 位用户态入口地址传给 sys_set_pgfault_handler。
 * 宿主函数指针是 64 位，截断后不落在 [0, UTOP)，会被判成 -E_INVAL，故平台给出 pgfault
 * 在用户地址空间中的入口地址常量：fork 传它，检查驱动用同一常量核对 env_pgfault_handler。 */
#define PGFAULT_ENTRY 0x00401000u