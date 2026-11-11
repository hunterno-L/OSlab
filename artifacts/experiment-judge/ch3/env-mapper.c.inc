/* ---- 平台提供的 load_icode_mapper（实验 3.6 考查时排除本片段） ----
 * MOS 公开语义：把 [va, va+size) 这段镜像内容装进进程 e 的地址空间。
 *   - 起止地址都要对齐到页：start = ROUNDDOWN(va, BY2PG)，end = ROUND(va+size, BY2PG)；
 *   - 每覆盖一页就 page_alloc 一页，把 binary 中对应字节拷进去；
 *   - 【关键】镜像第 0 字节必须落在虚拟地址 va 处，而不是所在页的页首：
 *     首页的目的页内偏移 dst = va - start，其余页 dst = 0；
 *     首页最多拷 BY2PG - dst 字节，末页只拷剩余字节，未拷到的部分保持 page_alloc 的清零；
 *   - 用 page_insert(e->env_pgdir, p, v, perm) 建立映射。
 *
 * 【引用计数约定】page_insert 自身会 pp_ref++（实验 2.7 已验证的契约），
 * 所以 mapper 不再额外递增，否则同一页会被计数两次。
 * 每页净增 1，这正是题面「为每个段分配内存页，并增加引用计数」的落点。 */

static MOS_UNUSED int load_icode_mapper(void *binary, uint32_t size, uint32_t va,
                                        int perm, struct Env *e)
{
    uint32_t start, end, off, v, copied = 0;
    if (!env_in_range(e) || !pgdir_ok(e->env_pgdir)) { return -E_INVAL; }
    if (size == 0) { return 0; }
    if (va >= UTOP) { return -E_INVAL; }
    start = ROUNDDOWN(va, BY2PG);
    end = ROUND(va + size, BY2PG);
    off = va - start;
    for (v = start; v < end; v += BY2PG) {
        struct Page *p = NULL;
        uint32_t dst, want;
        int r = page_alloc(&p);
        if (r != 0) { return r; }
        dst = (v == start) ? off : 0u;
        want = BY2PG - dst;
        if (want > size - copied) { want = size - copied; }
        if (binary != NULL && want > 0) {
            memcpy(page2kva(p) + dst, (unsigned char *)binary + copied, want);
        }
        copied += want;
        r = page_insert(e->env_pgdir, p, v, perm);
        if (r != 0) { return r; }
    }
    return 0;
}