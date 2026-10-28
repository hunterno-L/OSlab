### 任务描述

`env_alloc` 从空闲链表取出一个 PCB 并初始化为可运行。给定已用数量 n，新进程编号为 n。

### 输入格式

一行整数 `n`。

### 输出格式

两行：`env_id=<n>` 与 `status=RUNNABLE`。

### 样例

输入：

```
0
```

输出：

```
env_id=0
status=RUNNABLE
```
