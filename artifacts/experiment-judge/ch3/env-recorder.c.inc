/* ---- 第三章/第四章共享：lcontext / env_pop_tf 的宿主记录替身 ----
 * 从 env-pcb 段拆出，只为源码预算：这两个替身仅被 env_run 调用，用户态实验
 * （CH4_USER_DRIVER）已裁掉 env-run，故 mk.sh 在该模式下连本片段一起排除。
 * rec_* 数组与 MAXREC 仍留在 env-pcb 段（复位与驱动 Y 操作符要用）。 */

/* struct Trapframe 只可能落在 envs[] 的 env_tf 或两个现场全局量上 */
static MOS_UNUSED int tf_in_range(struct Trapframe *tf)
{
    unsigned char *b = (unsigned char *)tf;
    if (tf == NULL) { return 0; }
    if (b == (unsigned char *)&timestack_tf || b == (unsigned char *)&kernsp_tf) { return 1; }
    if (b >= (unsigned char *)envs && b < (unsigned char *)(envs + NENV)) {
        return ((uint32_t)(b - (unsigned char *)envs) % (uint32_t)sizeof(struct Env)) == 0;
    }
    return 0;
}

/* 真实内核：汇编写 CP0 Context 寄存器切换地址空间。宿主：只记录。 */
static MOS_UNUSED void lcontext(uint32_t pgdir_pa)
{
    if (rec_n < MAXREC) {
        rec_kind[rec_n] = 0;
        rec_arg[rec_n] = pgdir_pa;
        rec_cur[rec_n] = env_idx(curenv);
    }
    rec_n++;
}

/* 真实内核：汇编恢复 32 个寄存器并 eret 进入用户态。宿主：只记录。 */
static MOS_UNUSED void env_pop_tf(struct Trapframe *tf)
{
    if (rec_n < MAXREC) {
        rec_kind[rec_n] = 1;
        rec_arg[rec_n] = tf_in_range(tf) ? tf->cp0_epc : ~0u;
        rec_cur[rec_n] = env_idx(curenv);
    }
    rec_n++;
}