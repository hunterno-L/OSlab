### 任务描述

`sys_env_alloc` 为子进程分配 PCB，子进程号 = 父进程号 + 1。

### 输入格式

一行整数 `parent`。

### 输出格式

两行：`child=<parent+1>` 与 `status=RUNNABLE`。

### 样例

输入：

```
0
```

输出：

```
child=1
status=RUNNABLE
```
