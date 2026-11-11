// 一次性交叉校验：把实验 1.5 的参考解 _Printf 与宿主 libc snprintf 在同一组
// (格式串, 参数) 上逐用例比对，证明参考解语义确实等于 C 标准 printf 的声称子集。
// 这不是期望输出的来源（期望输出仍由 verify-experiment-harness.mjs 从参考解真实运行导出），
// 而是对「参考解自身正确性」的独立验证（harness-spec §9）。
//
// 用法：node artifacts/experiment-judge/ch1/xcheck-printf.mjs <1.5 实验目录>
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const dir = process.argv[2];
if (!dir) {
  console.error("usage: node artifacts/experiment-judge/ch1/xcheck-printf.mjs <experiment-dir>");
  process.exit(2);
}

const CFLAGS = ["-std=c99", "-Wall", "-Wextra", "-Werror", "-O2", "-pipe"];
const spec = JSON.parse(readFileSync(join(dir, "cases.json"), "utf8"));
const ref = readFileSync(join(dir, "reference.c"), "utf8").replace("int main(void)", "int harness_main(void)");

// 与驱动一致的转义解码
function decode(s) {
  let out = "";
  for (let i = 0; i < s.length; i++) {
    if (s[i] === "\\" && i + 1 < s.length) {
      const e = s[i + 1];
      if (e === "n") { out += "\n"; i++; continue; }
      if (e === "t") { out += "\t"; i++; continue; }
      if (e === "r") { out += "\r"; i++; continue; }
      if (e === "\\") { out += "\\"; i++; continue; }
    }
    out += s[i];
  }
  return out;
}

function cstr(s) {
  return '"' + s.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\t/g, "\\t").replace(/\r/g, "\\r") + '"';
}

// 含未识别转换字符的用例无法与 libc 比对（libc 对未定义转换的行为本身是 UB），
// 这类用例的契约由 harness 显式定义，跳过并如实报出。
function hasUnknownConv(fmt) {
  const re = /%[-0+ ]*(\d+)?(\.(\d+))?([a-zA-Z%])/g;
  let m;
  while ((m = re.exec(fmt)) !== null) {
    if (!"dxcs%".includes(m[4])) return true;
  }
  return false;
}

const blocks = [];
let checked = 0;
let skipped = 0;
spec.cases.forEach((c, idx) => {
  const lines = c.input.split("\n");
  const fmt = decode(lines[0]);
  const n = parseInt(lines[1], 10);
  const args = [];
  for (let k = 0; k < n; k++) {
    const m = /^([is])\s+(.*)$/.exec(lines[2 + k]);
    if (!m) throw new Error(`bad arg line in case ${c.name}: ${lines[2 + k]}`);
    args.push(m[1] === "i" ? { t: "i", v: m[2] } : { t: "s", v: m[2] });
  }
  if (hasUnknownConv(fmt)) {
    skipped++;
    blocks.push(`  printf("SKIP  [%d] ${c.name}: 含未识别转换字符，libc 行为未定义，契约由 harness 定义\\n", ${idx + 1});`);
    return;
  }
  checked++;
  const decls = args.map((a, k) => a.t === "i"
    ? `  a[${k}].is_str=0; a[${k}].num=(int32_t)${a.v}; a[${k}].str[0]=0;`
    : `  a[${k}].is_str=1; a[${k}].num=0; strcpy(a[${k}].str, ${cstr(a.v)});`).join("\n");
  // libc 侧需要真实类型：按参数种类在生成期决定 int / char* 实参（不能用运行时三目，类型不同）
  const libcArgs = args.map((a, k) => (a.t === "s" ? `libc_str(${k})` : `libc_int(${k})`)).join(", ");
  blocks.push(`  {
    static struct Arg a[${Math.max(n, 1)}];
    struct ArgList ap;
    char lb[8192];
    const char *f = ${cstr(fmt)};
    int same;
${decls}
    ap.a=a; ap.n=${n}; ap.i=0;
    out_len=0;
    _Printf(f, &ap);
    out_buf[out_len]=0;
    g_a=a; g_n=${n};
    snprintf(lb, sizeof lb, f, ${libcArgs});
    same = (out_len == (int)strlen(lb)) && (memcmp(out_buf, lb, (size_t)out_len) == 0);
    printf("%s [%d] ${c.name}\\n", same ? "MATCH " : "DIFFER", ${idx + 1});
    if (!same) { printf("   harness: %s\\n   libc   : %s\\n", out_buf, lb); rc=1; }
  }`);
});

const program = `${ref}

/* ---- 交叉校验驱动 ---- */
static struct Arg *g_a;
static int g_n;
static int libc_int(int k) { return (k < g_n) ? (int)g_a[k].num : 0; }
static char *libc_str(int k) { return (k < g_n) ? g_a[k].str : (char *)""; }

int main(void)
{
  int rc = 0;
${blocks.join("\n")}
  printf("checked=${checked} skipped=${skipped}\\n");
  return rc;
}
`;

const work = mkdtempSync(join(tmpdir(), "oslab-xcheck-"));
try {
  const src = join(work, "main.c");
  const exe = join(work, process.platform === "win32" ? "main.exe" : "main");
  writeFileSync(src, program);
  execFileSync("gcc", [...CFLAGS, src, "-o", exe], { stdio: "pipe" });
  const out = execFileSync(exe, [], { encoding: "utf8", stdio: "pipe" });
  process.stdout.write(out);
  process.exit(out.includes("DIFFER") ? 1 : 0);
} finally {
  rmSync(work, { recursive: true, force: true });
}