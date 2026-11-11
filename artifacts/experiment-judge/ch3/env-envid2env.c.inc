/* ---- 平台提供的 lib/env.c envid2env（实验 3.3 考查时排除本片段） ----
 * MOS 公开语义：
 *   envid == 0        -> 返回当前进程 curenv；
 *   ENVX(envid)       -> 取低 LOG2NENV 位作为 envs 下标；
 *   槽位空闲或 env_id 不匹配（生成号已变） -> -E_BAD_ENV；
 *   checkperm 非 0    -> 只有当前进程自己或当前进程的子进程才有权操作。
 * 前置条件：checkperm 非 0 时 curenv 必须非空（真实内核里系统调用总在进程上下文中发生，
 * 检查驱动在输入边界强制这一点）。 */

static MOS_UNUSED int envid2env(uint32_t envid, struct Env **penv, int checkperm)
{
    struct Env *e;
    *penv = NULL;
    if (envid == 0) {
        *penv = curenv;
        return 0;
    }
    e = &envs[ENVX(envid)];
    if (e->env_status == ENV_FREE || e->env_id != envid) { return -E_BAD_ENV; }
    if (checkperm && e != curenv && e->env_id != curenv->env_parent_id) { return -E_BAD_ENV; }
    *penv = e;
    return 0;
}