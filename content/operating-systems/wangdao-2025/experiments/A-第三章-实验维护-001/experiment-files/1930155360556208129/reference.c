#include <stdio.h>
int main(void) {
    int argc;
    if (scanf("%d", &argc) != 1) return 0;
    printf("argc=%d\n", argc);
    for (int i = 0; i < argc; i++) {
        char s[256];
        if (scanf("%255s", s) != 1) return 0;
        printf("argv[%d]=%s\n", i, s);
    }
    return 0;
}
