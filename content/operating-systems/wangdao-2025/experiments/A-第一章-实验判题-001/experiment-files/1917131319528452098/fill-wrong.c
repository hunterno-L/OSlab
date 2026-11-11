/* ===== 学生填写区：make 依赖图的合法重建顺序（拓扑序） ===== */
/* 典型错误解：按输入顺序输出、完全忽略依赖关系、也不检测循环依赖。
 * 这正是「没读懂 Makefile 依赖关系」的学生会写出的实现：
 * 它把 Makefile 当成一串顺序执行的命令，而不是依赖图。 */
int build_order(int n, int dep[MAXT][MAXT], int order[MAXT], int *built)
{
    int i;
    (void)dep;
    for (i = 0; i < n; i++) order[i] = i;
    *built = n;
    return 0;
}