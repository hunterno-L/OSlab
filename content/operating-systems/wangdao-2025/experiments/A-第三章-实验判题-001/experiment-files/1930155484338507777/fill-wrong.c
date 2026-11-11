/* ===== 学生填写区：实现 lib/env.c 的 env_create() 与 env_create_priority() ===== */

/* 典型错误解（四个缺陷都能被用例观测到）：
 *   缺陷 1：env_create 用了默认优先级 0，而不是/MOS 约定的 1。
 *           观测点：用例 1/2 的 T 字段 pri（参考解 1，本解 0）。
 *   缺陷 2：忘记把 env_status 置成 ENV_RUNNABLE，进程停在 env_alloc 预设的 NOT_RUNNABLE。
 *           观测点：所有用例的 T 字段 status（参考解 2，本解 1）——调度器永远选不中它。
 *   缺陷 3：没有调用 load_icode，镜像没装、入口没设、栈页没映射。
 *           观测点：用例 1/3 的 T 字段 cp0_epc（参考解=入口，本解 0）、
 *                   Q 段地址 / Q 栈页（参考解有映射，本解 4294967295）、空闲页数 C。
 *   缺陷 4：忽略空镜像守卫，binary=NULL 或 size=0 也照样创建进程。
 *           观测点：用例 5 的 Z/Y 之后就绪链表头 H 与空闲PCB数 F（参考解不变，本解多建了进程）。
 * 仍保留的正确部分：检查 env_alloc 返回值、头插就绪链表——故用例 4 的耗尽边界本解也能过，
 * 但用例 4 同时观测首个成功创建的 T/Q/C，缺陷 1/2/3 仍让它失败。 */
static void env_create_priority(void *binary, uint32_t size, int priority)
{
    struct Env *e = NULL;
    (void)binary;                                    /* 缺陷 4：没有空镜像守卫 */
    (void)size;
    if (env_alloc(&e, 0) != 0) { return; }
    if (!env_in_range(e)) { return; }
    /* 缺陷 3：没有 load_icode(e, elf_entry); */
    e->env_pri = (uint32_t)priority;
    /* 缺陷 2：没有 e->env_status = ENV_RUNNABLE; */
    LIST_INSERT_HEAD(&env_sched_list, e, env_sched_link);
}

static void env_create(void *binary, uint32_t size)
{
    env_create_priority(binary, size, 0);            /* 缺陷 1：默认优先级应为 1 */
}

/* ===== 填写区结束 ===== */