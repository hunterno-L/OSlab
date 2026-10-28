### 任务描述

在 MIPS 内核启动阶段，`mips_detect_memory()` 负责初始化物理内存相关的全局变量。给定物理内存上限 `maxpa`（字节），计算：

- `npage`：物理页总数，`npage = maxpa / 4096`
- `basemem`：基础内存大小（KB），`basemem = maxpa / 1024`

### 输入格式

一行一个整数 `maxpa`，保证是 4096 的整数倍。

### 输出格式

两行：

```
npage=<npage>
basemem_kb=<basemem>
```

### 样例

输入：

```
67108864
```

输出：

```
npage=16384
basemem_kb=65536
```
