/* ===== 学生填写区：实现 lib/env.c 的 load_icode_mapper() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.6「把每个段映射到正确虚拟地址 / 按段大小分配内存页 /
 * 增加引用计数」+ MOS 公开语义构造的等价示例。
 *
 * 正确性自行验证要点（已用 5 条用例实测）：
 *   - 镜像第 0 字节必须落在 va 处而不是页首：用例 2 的 va=6144（页内偏移 2048），
 *     D 6144 必须读到镜像第 0 字节起的字 0x03020100=50462976，而 D 4096 必须是 0（清零区）；
 *   - 末页只拷剩余字节：用例 2 的 D 14332 = 0xFFFEFDFC（镜像第 8188..8191 字节），
 *     D 14336 = 0（超出段尾的部分保持清零）；
 *   - end 必须 ROUND 向上取整，否则最后不足一页的部分整页丢失；
 *   - page_insert 已经 pp_ref++，这里【不能】再 ++：用例 1/5 的 R 必须恒为 1，
 *     且用例 5 重复映射同一 va 时旧页必须能被 page_remove 回收到空闲链表；
 *   - size==0 直接返回 0，不消耗物理页（用例 3 的 C 不变）；
 *   - page_alloc 失败原样上抛 -E_NO_MEM（用例 4 只给 4 页物理内存）。
 *
 * 【本参考解曾有的真实缺陷，已修正】初版把首页内容 memcpy 到页首（dst 恒为 0），
 * 使整段镜像在地址空间里前移了 off 字节 —— 程序入口取指会拿到错误字节。
 * 由用例 2 的 D 6144 / D 4096 实测值与手算相反暴露，现改为 dst = (v==start) ? off : 0。 */
static int load_icode_mapper(void *binary, uint32_t size, uint32_t va,
                             int perm, struct Env *e)
{
    uint32_t start, end, off, v, copied = 0;
    if (size == 0) { return 0; }
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

/* ===== 填写区结束 ===== */