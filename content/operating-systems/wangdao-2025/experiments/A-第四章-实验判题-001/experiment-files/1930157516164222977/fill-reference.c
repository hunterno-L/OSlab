static void duppage(uint32_t envid, uint32_t pn)
{
    uint32_t addr = pn << 12;
    uint32_t perm = user_pte(addr) & 0xFFFu;
    if ((perm & PTE_V) == 0) { return; }
    if (perm & PTE_LIBRARY) {
        syscall_mem_map(0, addr, envid, addr, perm);
    } else if (perm & PTE_R) {
        syscall_mem_map(0, addr, envid, addr, perm | PTE_COW);
        syscall_mem_map(0, addr, 0, addr, perm | PTE_COW);
    } else {
        syscall_mem_map(0, addr, envid, addr, perm);
    }
}