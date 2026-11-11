/* ===== 学生填写区：make 依赖图的合法重建顺序（拓扑序） ===== */
/* 参考解：Kahn 拓扑排序。
 * indeg[i] = 目标 i 尚未重建的依赖个数；每轮取编号最小的就绪目标，
 * 保证「同时就绪取编号最小者」的唯一顺序。
 * 找不到就绪目标但仍有未重建目标 → 循环依赖，返回 -1，
 * 并把已成功重建的个数写入 *built（make 会先构建能构建的部分再报环）。 */
int build_order(int n, int dep[MAXT][MAXT], int order[MAXT], int *built)
{
    int indeg[MAXT], done[MAXT];
    int i, cnt = 0;
    memset(indeg, 0, sizeof(indeg));
    memset(done, 0, sizeof(done));
    for (i = 0; i < n; i++) {
        int j;
        for (j = 0; j < n; j++) if (dep[i][j]) indeg[i]++;
    }
    *built = 0;
    while (cnt < n) {
        int pick = -1;
        for (i = 0; i < n; i++)
            if (!done[i] && indeg[i] == 0) { pick = i; break; }
        if (pick < 0) { *built = cnt; return -1; }
        done[pick] = 1;
        order[cnt++] = pick;
        for (i = 0; i < n; i++) if (dep[i][pick]) indeg[i]--;
    }
    *built = cnt;
    return 0;
}