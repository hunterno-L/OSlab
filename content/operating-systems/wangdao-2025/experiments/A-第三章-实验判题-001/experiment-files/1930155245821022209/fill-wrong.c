/* ===== 学生填写区：实现 lib/env.c 的 load_icode_mapper() ===== */

/* 典型错误解（三个缺陷都能被用例观测到）：
 *   缺陷 1：首页的目的页内偏移恒为 0，镜像第 0 字节被放到页首而不是 va 处，
 *           整段镜像在地址空间里前移了 off 字节（入口取指会拿到错误字节）。
 *           观测点：用例 2 的 D 6144（参考解 50462976，本解 0）与 D 4096（参考解 0，本解 50462976）。
 *   缺陷 2：在 page_insert 之外又 p->pp_ref++，同一页被计数两次。
 *           观测点：R 恒为 2 而非 1；用例 5 重复映射后旧页 pp_ref 只降到 1，
 *           永远不会被 page_free 回收，空闲页数 C 也对不上。
 *   缺陷 3：end 用 ROUNDDOWN 向下取整，段尾不足一页的部分整页丢失。
 *           观测点：用例 2 的 Q 12288 / D 14332（参考解有映射，本解 4294967295）。 */
static int load_icode_mapper(void *binary, uint32_t size, uint32_t va,
                             int perm, struct Env *e)
{
    uint32_t start, end, v, copied = 0;
    if (size == 0) { return 0; }
    start = ROUNDDOWN(va, BY2PG);
    end = ROUNDDOWN(va + size, BY2PG);          /* 缺陷 3 */
    for (v = start; v < end; v += BY2PG) {
        struct Page *p = NULL;
        uint32_t want;
        int r = page_alloc(&p);
        if (r != 0) { return r; }
        want = BY2PG;                            /* 缺陷 1：首页不跳偏移 */
        if (want > size - copied) { want = size - copied; }
        if (binary != NULL && want > 0) {
            memcpy(page2kva(p), (unsigned char *)binary + copied, want);
        }
        copied += want;
        r = page_insert(e->env_pgdir, p, v, perm);
        if (r != 0) { return r; }
        p->pp_ref++;                             /* 缺陷 2：重复计数 */
    }
    return 0;
}

/* ===== 填写区结束 ===== */