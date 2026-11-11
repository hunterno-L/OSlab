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
}