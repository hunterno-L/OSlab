#include <stdio.h>
int main(void) {
    int n, q;
    if (scanf("%d %d", &n, &q) != 2) return 0;
    int pid[64], burst[64], rem[64], head = 0, tail = 0, qq[4096];
    for (int i = 0; i < n; i++) {
        if (scanf("%d %d", &pid[i], &burst[i]) != 2) return 0;
        rem[i] = burst[i];
        qq[tail++] = i;
    }
    int first = 1;
    while (head < tail) {
        int i = qq[head++];
        int run = rem[i] < q ? rem[i] : q;
        rem[i] -= run;
        if (rem[i] > 0) {
            qq[tail++] = i;
        } else {
            printf(first ? "%d" : " %d", pid[i]);
            first = 0;
        }
    }
    printf("\n");
    return 0;
}
