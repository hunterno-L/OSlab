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
/* ===== 学生填写区：实现 lib/print.c 的 _Printf() 格式解析与字符输出 ===== */
/* 典型错误解：格式串扫描时把 width 的数字读掉了，却忘记把它传给输出函数
 * （width 恒为 0）—— 即「忽略宽度修饰符」。
 * 结果：%08d / %8x / %-8s 全部退化成不带填充的紧凑输出，
 * 对齐与填充这两项说明明确要求的能力完全丢失。 */

static void emit_field(const char *body, int len, int width, int left, int zero)
{
    int pad = width - len;
    int i;
    if (pad < 0) pad = 0;
    if (left) {
        for (i = 0; i < len; i++) print_char(body[i]);
        for (i = 0; i < pad; i++) print_char(' ');
    } else if (zero) {
        for (i = 0; i < pad; i++) print_char('0');
        for (i = 0; i < len; i++) print_char(body[i]);
    } else {
        for (i = 0; i < pad; i++) print_char(' ');
        for (i = 0; i < len; i++) print_char(body[i]);
    }
}

static void emit_number(uint32_t mag, unsigned base, int neg, int width, int prec,
                        int left, int zero, int plus, int space)
{
    char rev[160];
    char body[160];
    char sign = 0;
    int nd = 0, len = 0, pad, i;
    int minDigits = (prec < 0) ? 1 : prec;
    if (!(mag == 0 && prec == 0)) {
        do {
            rev[nd++] = "0123456789abcdef"[mag % base];
            mag /= base;
        } while (mag != 0);
    }
    while (nd < minDigits) rev[nd++] = '0';
    if (neg) sign = '-';
    else if (plus) sign = '+';
    else if (space) sign = ' ';
    if (sign != 0) body[len++] = sign;
    for (i = nd - 1; i >= 0; i--) body[len++] = rev[i];
    pad = width - len;
    if (pad < 0) pad = 0;
    if (left) {
        for (i = 0; i < len; i++) print_char(body[i]);
        for (i = 0; i < pad; i++) print_char(' ');
    } else if (zero && prec < 0) {
        if (sign != 0) print_char(sign);
        for (i = 0; i < pad; i++) print_char('0');
        for (i = (sign != 0 ? 1 : 0); i < len; i++) print_char(body[i]);
    } else {
        for (i = 0; i < pad; i++) print_char(' ');
        for (i = 0; i < len; i++) print_char(body[i]);
    }
}

static void _Printf(const char *fmt, struct ArgList *ap)
{
    int i = 0;
    while (fmt[i] != '\0') {
        int left = 0, zero = 0, plus = 0, space = 0;
        int width = 0, prec = -1;
        char conv;
        if (fmt[i] != '%') { print_char(fmt[i]); i++; continue; }
        i++;
        for (;;) {
            char f = fmt[i];
            if (f == '-') { left = 1; i++; }
            else if (f == '0') { zero = 1; i++; }
            else if (f == '+') { plus = 1; i++; }
            else if (f == ' ') { space = 1; i++; }
            else break;
        }
        while (fmt[i] >= '0' && fmt[i] <= '9') { width = width * 10 + (fmt[i] - '0'); i++; }
        width = 0;   /* BUG: 读掉了宽度数字却丢弃，等价于忽略宽度修饰符 */
        if (fmt[i] == '.') {
            i++;
            prec = 0;
            while (fmt[i] >= '0' && fmt[i] <= '9') { prec = prec * 10 + (fmt[i] - '0'); i++; }
        }
        conv = fmt[i];
        if (conv == '\0') { print_char('%'); break; }
        i++;
        if (conv == '%') { print_char('%'); continue; }
        if (conv == 'd') {
            int32_t v = (int32_t)arg_int(ap);
            int neg = (v < 0);
            uint32_t mag = neg ? (0u - (uint32_t)v) : (uint32_t)v;
            emit_number(mag, 10u, neg, width, prec, left, zero, plus, space);
        } else if (conv == 'x') {
            emit_number((uint32_t)arg_int(ap), 16u, 0, width, prec, left, zero, 0, 0);
        } else if (conv == 'c') {
            char b[1];
            b[0] = (char)arg_int(ap);
            emit_field(b, 1, width, left, 0);
        } else if (conv == 's') {
            const char *s = arg_str(ap);
            int len = (int)strlen(s);
            if (prec >= 0 && len > prec) len = prec;
            emit_field(s, len, width, left, 0);
        } else {
            print_char('%');
            print_char(conv);
        }
    }
}/* ===== 填写区结束 ===== */

int main(void)
{
    static struct Arg args[MAXARG];
    static struct ArgList ap;
    char fmt[FMTMAX + 2];
    char line[LINEMAX];
    char tok[ARGSTR + 8];
    char dec[FMTMAX + 2];
    int n, i, j, k, run;
    char kc;

    if (!read_line(fmt, FMTMAX + 2)) return 0;
    /* 把 C 字符串字面量转义解码成真实控制字符（stdin 是纯文本，无法直接带换行） */
    j = 0;
    for (i = 0; fmt[i] != '\0'; i++) {
        if (fmt[i] == '\\' && fmt[i + 1] != '\0') {
            char e = fmt[i + 1];
            if (e == 'n') { dec[j++] = '\n'; i++; continue; }
            if (e == 't') { dec[j++] = '\t'; i++; continue; }
            if (e == 'r') { dec[j++] = '\r'; i++; continue; }
            if (e == '\\') { dec[j++] = '\\'; i++; continue; }
        }
        dec[j++] = fmt[i];
    }
    dec[j] = '\0';
    if (j > FMTMAX) return 0;
    /* 输入边界校验：只约束转换说明内部的 width/precision 数字串（最多两位，0..99），
     * 保证输出有界；格式串里的普通数字文本（如 "100%%"）不受限制。
     * 这里只做「跳过 flags / 数字 / .数字 / 转换字符」的输入扫描，不计算任何输出。 */
    i = 0;
    while (i < j) {
        if (dec[i] != '%') { i++; continue; }
        i++;
        while (i < j && (dec[i] == '-' || dec[i] == '0' || dec[i] == '+' || dec[i] == ' ')) i++;
        run = 0;
        while (i < j && dec[i] >= '0' && dec[i] <= '9') {
            run++;
            if (run > 2) return 0;
            i++;
        }
        if (i < j && dec[i] == '.') {
            i++;
            run = 0;
            while (i < j && dec[i] >= '0' && dec[i] <= '9') {
                run++;
                if (run > 2) return 0;
                i++;
            }
        }
        if (i < j) i++;   /* 转换字符 */
    }

    if (!read_line(line, LINEMAX)) return 0;
    if (sscanf(line, "%d", &n) != 1) return 0;
    if (n < 0 || n > MAXARG) return 0;

    for (i = 0; i < n; i++) {
        if (!read_line(line, LINEMAX)) return 0;
        if (sscanf(line, " %c %63s", &kc, tok) != 2) return 0;
        if (kc == 'i') {
            int32_t v;
            if (!parse_i32(tok, &v)) return 0;
            args[i].is_str = 0;
            args[i].num = v;
            args[i].str[0] = '\0';
        } else if (kc == 's') {
            args[i].is_str = 1;
            args[i].num = 0;
            for (k = 0; k < ARGSTR - 1 && tok[k] != '\0'; k++) args[i].str[k] = tok[k];
            args[i].str[k] = '\0';
        } else {
            return 0;
        }
    }

    ap.a = args;
    ap.n = n;
    ap.i = 0;
    out_len = 0;
    _Printf(dec, &ap);
    fwrite(out_buf, 1, (size_t)out_len, stdout);
    fputc('\n', stdout);
    return 0;
}