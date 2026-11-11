// 一次性生成器：为实验 1.2（ELF 解析）合成 cases.json 的输入。
// 只生成【输入】；期望输出仍由 verify-experiment-harness.mjs 从参考解真实运行导出。
// 用法：node artifacts/experiment-judge/ch1/gen-elf-cases.mjs > <实验目录>/cases.json
import { writeFileSync } from "node:fs";

const SHENT = 40; // Elf32_Shdr 真实长度

function shdr({ name = 0, type = 0, flags = 0, addr = 0, off = 0, size = 0, link = 0, info = 0, align = 0, entsize = 0 }) {
  const fields = [name, type, flags, addr, off, size, link, info, align, entsize];
  const b = Buffer.alloc(SHENT);
  fields.forEach((v, i) => b.writeUInt32LE(v >>> 0, i * 4));
  return b;
}

const NULLSEC = () => shdr({});
const TEXT = (addr, size) => shdr({ name: 1, type: 1, flags: 6, addr, off: 0x1000, size, align: 4 });
const DATA = (addr, size) => shdr({ name: 7, type: 1, flags: 3, addr, off: 0x2000, size, align: 4 });
const BSS = (addr, size) => shdr({ name: 13, type: 8, flags: 3, addr, off: 0x3000, size, align: 4 });
const RODATA = (addr, size) => shdr({ name: 19, type: 1, flags: 2, addr, off: 0x4000, size, align: 4 });
const STRTAB = (addr) => shdr({ name: 27, type: 3, flags: 0, addr, off: 0x5000, size: 64, align: 1 });
const SYMTAB = (addr) => shdr({ name: 35, type: 2, flags: 0, addr, off: 0x6000, size: 128, link: 6, info: 1, align: 4, entsize: 16 });

// 把 40 字节记录补齐到 shentsize（真实链接器用 0 填充）
function pad(buf, shentsize) {
  if (buf.length === shentsize) return buf;
  const out = Buffer.alloc(shentsize);
  buf.copy(out);
  return out;
}

function makeCase(name, shoff, shentsize, secs) {
  const lines = secs.map((s) => pad(s, shentsize).toString("hex"));
  return { name, input: `${shoff} ${shentsize} ${secs.length}\n${lines.join("\n")}\n` };
}

const cases = [
  makeCase("shnum3-shoff52-text-data-bss", 52, 40, [
    TEXT(0x80000000, 0x2000), DATA(0x80010000, 0x400), BSS(0x80020000, 0x800),
  ]),
  makeCase("shnum8-nonzero-shoff256", 256, 40, [
    NULLSEC(), TEXT(0x80000000, 0x2000), DATA(0x80010000, 0x400), BSS(0x80020000, 0x800),
    RODATA(0x80030000, 0x200), STRTAB(0), SYMTAB(0), STRTAB(0),
  ]),
  makeCase("shnum16-stride48-shoff128", 128, 48,
    Array.from({ length: 16 }, (_, i) => (i % 5 === 4 ? NULLSEC() : TEXT(0x80000000 + i * 0x1000, 0x1000)))),
  makeCase("zero-addr-sections-not-skipped", 64, 40, [
    NULLSEC(), TEXT(0x80000000, 0x2000), BSS(0, 0x800), STRTAB(0),
  ]),
  makeCase("single-section-minimal", 52, 40, [TEXT(0x80000000, 0x1000)]),
  makeCase("max-shoff4096-stride64", 4096, 64, [
    TEXT(0x80000000, 0x1000), DATA(0x80001000, 0x100), BSS(0x80002000, 0x200),
  ]),
];

const spec = {
  description:
    "驱动 readelf.c 的 readelf()，解析合成 ELF32 映像的 Section Header Table。输入首行「e_shoff e_shentsize e_shnum」（十进制；52<=e_shoff<=4096 且 4 字节对齐，40<=e_shentsize<=64 且 4 字节对齐，1<=e_shnum<=16），随后 e_shnum 行，每行是一个 Section Header 的十六进制字节流（恰好 2*e_shentsize 个 hex 字符，小端，sh_addr 在记录内偏移 12 字节）。驱动据此合成完整 ELF32 映像后调用 readelf(binary,size)。输出：对每个 Section Header 按北航原文格式逐行打印「%x:d:0x%x」，即「序号:d:0x地址」（序号为十六进制小写、地址为 0x 前缀十六进制小写），地址为 0 的节也必须输出，不得跳过。",
  cases,
};

const out = process.argv[2];
const text = JSON.stringify(spec, null, 2) + "\n";
if (out) writeFileSync(out, text);
else process.stdout.write(text);