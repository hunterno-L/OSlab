#include <stdio.h>
int main(void) {
    int n, e;
    if (scanf("%d %d", &n, &e) != 2) return 0;
    int dep[64][64] = {0}, indeg[64] = {0};
    for (int i = 0; i < e; i++) {
        int u, v;
        if (scanf("%d %d", &u, &v) != 2) return 0;
        dep[v][u] = 1;
        indeg[u]++;
    }
    int done[64] = {0};
    for (int k = 0; k < n; k++) {
        int pick = -1;
        for (int i = 0; i < n; i++) {
            if (!done[i] && indeg[i] == 0) { pick = i; break; }
        }
        if (pick < 0) { printf("CYCLE\n"); return 0; }
        done[pick] = 1;
        printf(k ? " %d" : "%d", pick);
        for (int j = 0; j < n; j++) {
            if (dep[pick][j]) indeg[j]--;
        }
    }
    printf("\n");
    return 0;
}
