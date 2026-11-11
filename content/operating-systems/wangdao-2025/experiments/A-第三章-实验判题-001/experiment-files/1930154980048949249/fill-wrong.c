/* ===== 学生填写区：实现 lib/env.c 的 env_setup_vm() ===== */

/* 典型错误解（三个缺陷都能被用例观测到）：
 *   缺陷 1：忘记 p->pp_ref++。页目录页引用计数为 0，一旦被 page_free 就回到空闲链表，
 *           下一个进程会拿到同一张页目录 —— 两个进程地址空间互相污染。
 *           观测点：W 的最后一个字段 pp_ref（参考解 1，本解 0）。
 *   缺陷 2：内核段拷贝循环下界写成 PDX(UTOP)+1，漏掉 UTOP 那一页本身（经典 off-by-one）。
 *           观测点：W 的内核段拷贝项数（参考解 767，本解 766）、Q idx UTOP（参考解 0，本解 ~0）。
 *   缺陷 3：漏掉 UVPT 自映射，用户态无法通过 UVPT 窗口观察自己的页表。
 *           观测点：W 的 UVPT 项（参考解 env_cr3|3，本解 0）。 */
static int env_setup_vm(struct Env *e)
{
    uint32_t i;
    int r;
    struct Page *p = NULL;
    r = page_alloc(&p);
    if (r != 0) { return r; }
    e->env_pgdir = (Pde *)page2kva(p);
    e->env_cr3 = page2pa(p);
    for (i = PDX(UTOP) + 1u; i < 1024u; i++) { e->env_pgdir[i] = boot_pgdir[i]; }  /* 缺陷 2 */
    return 0;                                                                     /* 缺陷 1、3 */
}

/* ===== 填写区结束 ===== */