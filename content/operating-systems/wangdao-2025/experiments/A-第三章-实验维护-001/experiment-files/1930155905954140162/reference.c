#include <stdio.h>
int main(void) {
    int c;
    if (scanf("%d", &c) != 1) return 0;
    const char *n;
    if (c == 0) n = "Interrupt";
    else if (c >= 1 && c <= 3) n = "TLB";
    else if (c >= 4 && c <= 7) n = "AddressError";
    else if (c == 8) n = "Syscall";
    else if (c == 9) n = "Breakpoint";
    else if (c == 10) n = "Reserved";
    else if (c == 11) n = "CoproUnusable";
    else if (c == 12) n = "Overflow";
    else if (c == 13) n = "Trap";
    else n = "Unknown";
    printf("%s\n", n);
    return 0;
}
