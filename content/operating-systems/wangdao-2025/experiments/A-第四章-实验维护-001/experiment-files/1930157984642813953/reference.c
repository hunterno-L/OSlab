#include <stdio.h>
int main(void) {
    long id;
    int st;
    if (scanf("%ld %d", &id, &st) != 2) return 0;
    const char *s = st == 0 ? "FREE" : st == 1 ? "NOT_RUNNABLE" : "RUNNABLE";
    printf("env=%ld status=%s\n", id, s);
    return 0;
}
