/* ===== 学生填写区：实现 lib/env.c 的 envid2env() ===== */

/* 【宿主环境说明】工作区未随附 lib/env.c 原文。以下按题面 3.3「通过 env_id 获取 PCB」+ MOS/JOS 公开语义
 * 构造的等价示例，不是原文逐字转录。
 *
 * 正确性自行验证要点（已用 5 条用例实测）：
 *   - envid==0 必须返回 curenv，不能当成非法 id；
 *   - ENVX 只取低 6 位，所以 4294967295 会落到 envs[63]，
 *     必须靠 env_id 全等比较把它拒掉，只查 ENV_FREE 是不够的；
 *   - 槽位复用后生成号变化，旧 envid 必须失效；
 *   - checkperm 的语义是「自己或自己的父进程」，不是「自己或自己的子进程」。 */
static int envid2env(uint32_t envid, struct Env **penv, int checkperm)
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

/* ===== 填写区结束 ===== */