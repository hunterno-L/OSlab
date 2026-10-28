#include <stdio.h>
int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 0;
    for (int i = 0; i < n; i++) {
        char s[128];
        if (scanf("%127s", s) != 1) return 0;
        printf("pid=%d name=%s\n", i, s);
    }
    return 0;
}
