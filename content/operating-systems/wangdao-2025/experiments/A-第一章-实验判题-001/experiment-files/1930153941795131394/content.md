# 实验 1.2 深入理解 ELF 文件格式

## 实验背景

在本实验中，你将通过阅读和分析 ELF 文件的结构，理解 ELF 文件的组成部分及其在程序执行中的作用。你将学习如何使用 `readelf` 工具来查看 ELF 文件的详细信息，并编写代码来解析 ELF 文件的特定部分。

## 知识要点

1. **ELF 文件格式**：
   - 理解 ELF 文件的基本概念和结构。
   - 了解 ELF 文件的五种主要组成部分：ELF Header、Program Header Table、Section Header Table、Segments、Sections。
2. **ELF 文件的编译和链接过程**：
   - 理解从多个 C 文件到单个可执行文件的编译和链接过程。
   - 了解链接器如何将多个目标文件链接成可执行文件。
3. **使用 `readelf` 工具**：
   - 学习如何使用 `readelf` 工具查看 ELF 文件的详细信息。
   - 理解 `readelf` 输出的各个部分的含义。
4. **解析 ELF 文件**：
   - 学习如何编写代码来解析 ELF 文件的特定部分，如 Section Header Table。

**扩写（ELF32 公开规范）**：**Section** 是链接视角，**Segment** 是加载视角（`p_memsz − p_filesz` 就是 `.bss`）。ELF32 header 固定 52 字节、Section Header 固定 40 字节（`e_shentsize` 可更大），全部小端。`sh_addr` 在记录内**偏移 12 字节**处；第 i 条记录位于 `e_shoff + i * e_shentsize`——**乘法不能漏**。

## 关联源码

| 文件 | 作用 | 需要关注的符号 |
| --- | --- | --- |
| `readelf.c` | 本实验要补全的解析器 | readelf(u_char *binary, int size) |
| `kerelf.h` | ELF32 结构体定义 | Elf32_Ehdr、Elf32_Shdr、e_shoff、e_shentsize、e_shnum、sh_addr |
| `main.c` | 读入镜像并调用 readelf | binary、size |

**内容来源**：`readelf.c`/`kerelf.h` 的**原指导 Listing 未随工作区提供**（`originalCode`/`answer` 实测均 0 字节）。harness 结构体按公开 ELF32 规范定义，是**等价**实现；输出格式 `"%x:d:0x%x\n"` 逐字采用北航原文实验任务 3 给出的格式。

## 实验任务

**实验任务**

1. **阅读和理解 ELF 文件结构**：
   - 阅读实验材料，理解 ELF 文件的五个主要组成部分及其作用。
2. **使用 `readelf` 工具查看 ELF 文件信息**：
   - 使用 `readelf` 工具查看 `kerelf.h`、`readelf.c` 和 `main.c` 文件的 ELF 信息。
   - 记录并分析 `readelf` 输出的 Section Header Table 信息。
3. **编写代码解析 ELF 文件**：
   - 编写代码实现 `readelf.c` 文件中缺少的功能，输出 ELF 文件的所有 Section Header 的序号和地址信息。
   - 对每个 Section Header，输出格式为："%x:d:0x%x\n"，两个标识符分别代表序号和地址。
4. **验证解析结果**：
   - 运行编写的代码，验证输出的 Section Header 信息是否正确。
   - 比较 `readelf` 工具的输出和你编写的代码的输出，确保两者一致。

**每步验证**：识别 ELF 结构，运行 `readelf`，补全解析并对照各节地址。

## 本平台判题说明

- 判题方式：**Tier A**：主机端等价 harness，判真实目标函数 `readelf()`，不是代理题。
- 你需要填写：`static void readelf(u_char *binary, int size)`（`starter.c` 填写区）。把 `binary` 解释为 `Elf32_Ehdr*`，按 `e_shoff + i*e_shentsize` 定位每条 `Elf32_Shdr` 并打印。
- 判题输入：首行「e_shoff e_shentsize e_shnum」（52<=e_shoff<=4096、40<=e_shentsize<=64，均 4 字节对齐，1<=e_shnum<=16），随后 e_shnum 行十六进制字节流（每行 2*e_shentsize 个 hex 字符，小端，sh_addr 在记录内偏移 12 字节）。驱动据此合成 ELF32 映像后调用 `readelf(binary,size)`。
- 判题输出：每个 Section Header 一行「%x:d:0x%x」，即「序号:d:0x地址」：序号十六进制小写、地址 0x 前缀十六进制小写。**地址为 0 的节也必须输出。**
- 宿主环境适配：32 位地址用 `uint32_t`（MinGW 是 LLP64、Linux 是 LP64，`long` 宽度不同，禁用 `%ld`/`%p`）；多字节字段用 `memcpy` 而非指针强转；映像放在 `static uint32_t img_store[]` 上保证对齐。
- 与真实内核的关系：合成的是合法 ELF32 映像，`readelf()` 逻辑与解析真实 `vmlinuz` 一致；本地仍应用 `mips-linux-gnu-readelf -S` 比对。
- 内容来源标注：原指导 Listing **未随工作区提供**，ELF 结构体与合成映像为按公开规范构造的**等价**环境，非北航原文转录。

契约原文（逐字引自本目录 `cases.json` 的 `description`）：

```text
驱动 readelf.c 的 readelf()，解析合成 ELF32 映像的 Section Header Table。输入首行「e_shoff e_shentsize e_shnum」（十进制；52<=e_shoff<=4096 且 4 字节对齐，40<=e_shentsize<=64 且 4 字节对齐，1<=e_shnum<=16），随后 e_shnum 行，每行是一个 Section Header 的十六进制字节流（恰好 2*e_shentsize 个 hex 字符，小端，sh_addr 在记录内偏移 12 字节）。驱动据此合成完整 ELF32 映像后调用 readelf(binary,size)。输出：对每个 Section Header 按北航原文格式逐行打印「%x:d:0x%x」，即「序号:d:0x地址」（序号为十六进制小写、地址为 0x 前缀十六进制小写），地址为 0 的节也必须输出，不得跳过。
```

## 本地完整验证

本地完整验证（选填）：`make` 出 `gzemul/vmlinuz` 后，`mips-linux-gnu-readelf -S` 记下每节 Addr，再运行自己写的 `readelf` 解析同一镜像，两份输出逐行 `diff`。真实镜像 `e_shentsize` 通常是 40，但代码不能硬编码 40。

## 常见错误

1. 偏移漏乘 `e_shentsize`（`wrong.c`：`off = e_shoff + i`）：第 1 节起全部错位，`shnum8-nonzero-shoff256` 输出 8 行 `0x0`。
2. 记录长度硬编码成 40：`shnum16-stride48-shoff128` 用 `e_shentsize=48`，必然读错。
3. 跳过 `sh_addr == 0` 的节：`zero-addr-sections-not-skipped` 要求 4 行全输出。
