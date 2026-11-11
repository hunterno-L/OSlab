static void pgfault(uint32_t va)
{
    uint32_t addr = ROUNDDOWN(va, BY2PG);
    uint32_t perm = user_pte(addr) & 0xFFFu;
    if ((perm & PTE_COW) == 0) { return; }
    if (syscall_mem_alloc(0, UXSTACKTOP - BY2PG, PTE_V | PTE_R) != 0) { return; }
    if (user_memcpy(UXSTACKTOP - BY2PG, addr, BY2PG) != 0) { return; }
    if (syscall_mem_map(0, UXSTACKTOP - BY2PG, 0, addr, perm) != 0) { return; }
    syscall_mem_unmap(0, UXSTACKTOP - BY2PG);
}