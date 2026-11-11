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
/* 逐个字符扫描 fmt：
 *   - 普通字符直接 print_char() 输出；
 *   - 遇到 '%' 开始解析一个转换说明：%[flags][width][.precision]conv
 *       flags: '-' 左对齐、'0' 零填充、'+' 强制正号、' ' 正数前空格（可组合、可重复）
 *       width: 十进制数字串（0..99），最小字段宽度
 *       precision: '.' 后接十进制数字串（0..99，缺省数字则为 0）
 *       conv: 'd' 十进制有符号、'x' 十六进制小写无符号、'c' 单字符、's' 字符串、'%' 字面百分号
 *   - 用 arg_int(ap) / arg_str(ap) 按出现顺序取参（等价 va_arg）。
 * 语义与 C 标准 printf 在上述子集内一致：
 *   d/x：precision 是最少数字位数（不足补前导 0）；值为 0 且 precision 为 0 时不输出数字；
 *        指定了 precision 时 '0' flag 失效；'-' 与 '0' 同时出现时 '-' 优先；
 *        零填充补在符号之后、数字之前。
 *   s：precision 是最多输出字符数；'0' flag 对 s/c 无效。
 *   c：输出 (char)整数参数；precision 无效。
 *   '%'：输出一个字面 '%'，不取参，flags/width/precision 全部忽略。
 *   未识别的 conv 字符：原样输出 '%' 与该字符，不取参。
 *   fmt 在 conv 之前结束：输出一个 '%' 后停止。 */
static void _Printf(const char *fmt, struct ArgList *ap)
{
    /* finish your code here */
    (void)fmt; (void)ap;
}
/* ===== 填写区结束 ===== */

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