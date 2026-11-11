/* ===== 学生填写区：实现 lib/env.c 的 load_icode() ===== */

/* 典型错误解（四个缺陷都能被用例观测到）：
 *   缺陷 1：不检查 load_elf 的返回值，加载失败也继续设入口、分配栈。
 *           观测点：用例 4 的 N（参考解 cp0_epc=0，本解 4096）与 Q 栈页。
 *   缺陷 2：栈页映射到 USTACKTOP 而不是 USTACKTOP-BY2PG，把内核段首页的映射覆盖掉。
 *           观测点：用例 1/3 的 Q 1073737728（参考解有栈页，本解 ~0）
 *                   与 Q 1073741824（参考解是内核段 pa 0，本解是栈页）。
 *   缺陷 3：在 page_insert 之外又 p->pp_ref++，栈页被计数两次。
 *           观测点：R 栈页 = 2 而非 1。
 *   缺陷 4：漏掉 regs[29] = USTACKTOP，进程带着垃圾栈指针被调度。
 *           观测点：用例 5 先 X 污染 sp，N 的 sp 字段（参考解 1073741824，本解 3735928559）。 */
static void load_icode(struct Env *e, uint32_t entry)
{
    struct Page *p = NULL;
    load_elf(e, load_icode_mapper);                  /* 缺陷 1：忽略返回值 */
    e->env_tf.cp0_epc = entry;
    if (page_alloc(&p) != 0) { return; }
    p->pp_ref++;                                     /* 缺陷 3 */
    if (page_insert(e->env_pgdir, p, USTACKTOP, PTE_R | PTE_V) != 0) { return; }  /* 缺陷 2 */
                                                     /* 缺陷 4：没有设 sp */
}

/* ===== 填写区结束 ===== */