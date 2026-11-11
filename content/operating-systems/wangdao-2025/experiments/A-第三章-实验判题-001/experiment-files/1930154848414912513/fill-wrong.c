/* ===== 学生填写区：实现 lib/env.c 的 envid2env() ===== */

/* 典型错误解：
 *   缺陷 1：只检查 ENV_FREE，漏了 e->env_id != envid 的全等比较。
 *           后果：任何低 6 位相同的整数都能命中该槽位（4294967295 也命中 envs[63]），
 *           且 PCB 被释放重分配后旧 envid 仍然可用 —— 生成号机制完全失效。
 *   缺陷 2：完全不做 checkperm 权限检查，任意进程都能操作别人的 PCB。 */
static int envid2env(uint32_t envid, struct Env **penv, int checkperm)
{
    struct Env *e;
    *penv = NULL;
    (void)checkperm;                       /* 缺陷 2 */
    if (envid == 0) {
        *penv = curenv;
        return 0;
    }
    e = &envs[ENVX(envid)];
    if (e->env_status == ENV_FREE) { return -E_BAD_ENV; }   /* 缺陷 1 */
    *penv = e;
    return 0;
}

/* ===== 填写区结束 ===== */