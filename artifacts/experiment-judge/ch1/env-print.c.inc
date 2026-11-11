/* 操作系统实验 1.5 实现内核中的 printf 函数 —— 主机端等价判题环境
 * 真实源码：MOS lib/print.c 的 _Printf()、drivers/gzconsole/console.c 的字符输出
 * 等价适配（不改变语义）：
 *   1. 内核无 C 标准库，_Printf 通过 print_char() 把字符送到控制台；
 *      主机端 print_char() 把字符写入一个有界缓冲，由驱动统一输出，语义等价。
 *   2. 真实签名是 _Printf(const char *fmt, va_list ap)，用 va_arg(ap,int) / va_arg(ap,char*) 取参。
 *      主机端用 struct ArgList + arg_int()/arg_str() 作为 va_list/va_arg 的等价替身
 *      （沙箱单文件、stdin 驱动，无法 portable 地构造 va_list）。取参顺序与语义完全一致。
 *   3. 整数一律 int32_t/uint32_t：MinGW(LLP64) 与 Linux(LP64) 下宽度一致（harness-spec §7）。
 *   4. 支持的转换说明符子集在 cases.json 与实验说明中逐字给出，语义与 C 标准 printf 一致。
 * 本段由平台提供，学生只填写标记区域内的函数。
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define MOS_UNUSED __attribute__((unused))

#define MAXARG   8
#define ARGSTR   64
#define FMTMAX   200
#define OUTMAX   4096
#define LINEMAX  256

struct Arg {
    int is_str;
    int32_t num;
    char str[ARGSTR];
};

/* va_list 的主机等价物：一个参数游标 */
struct ArgList {
    const struct Arg *a;
    int n;
    int i;
};

/* va_arg(ap, int) 的等价物；参数耗尽时返回 0（确定性，不崩溃） */
MOS_UNUSED static int arg_int(struct ArgList *ap)
{
    if (ap->i >= ap->n) return 0;
    return (int)ap->a[ap->i++].num;
}

/* va_arg(ap, char *) 的等价物；参数耗尽时返回空串 */
MOS_UNUSED static const char *arg_str(struct ArgList *ap)
{
    if (ap->i >= ap->n) return "";
    return ap->a[ap->i++].str;
}

/* 控制台字符输出的主机端等价物：有界缓冲，防止错误实现撑爆沙箱 64KB 输出上限 */
static char out_buf[OUTMAX];
static int out_len;

MOS_UNUSED static void print_char(char c)
{
    if (out_len < OUTMAX - 1) out_buf[out_len++] = c;
}

/* 便携的 int32 解析：不用 strtol（MinGW 的 long 是 32 位，溢出行为与 Linux 不同） */
MOS_UNUSED static int parse_i32(const char *s, int32_t *out)
{
    int neg = 0, i = 0;
    uint32_t acc = 0, limit;
    if (s[0] == '-') { neg = 1; i = 1; }
    else if (s[0] == '+') { i = 1; }
    if (s[i] == '\0') return 0;
    limit = neg ? 2147483648u : 2147483647u;
    for (; s[i] != '\0'; i++) {
        uint32_t d;
        if (s[i] < '0' || s[i] > '9') return 0;
        d = (uint32_t)(s[i] - '0');
        if (acc > limit / 10u) return 0;
        acc = acc * 10u;
        if (acc > limit - d) return 0;
        acc += d;
    }
    *out = neg ? (int32_t)(0u - acc) : (int32_t)acc;
    return 1;
}

/* 读一整行；超长（缓冲装满仍无换行且未到 EOF）视为非法输入 */
MOS_UNUSED static int read_line(char *buf, int cap)
{
    int c = EOF;
    int len = 0;
    for (;;) {
        c = fgetc(stdin);
        if (c == EOF) break;
        if (c == '\n') break;
        if (len < cap - 1) buf[len++] = (char)c;
        else return 0;
    }
    if (len > 0 && buf[len - 1] == '\r') len--;
    buf[len] = '\0';
    if (c == EOF && len == 0) return 0;
    return 1;
}