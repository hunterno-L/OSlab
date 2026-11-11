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