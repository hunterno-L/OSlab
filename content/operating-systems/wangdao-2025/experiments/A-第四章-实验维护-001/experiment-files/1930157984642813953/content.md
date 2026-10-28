### 任务描述

`sys_set_env_status` 设置进程状态。给定进程号与状态码：0=FREE，1=NOT_RUNNABLE，2=RUNNABLE。

### 输入格式

一行 `id status`。

### 输出格式

一行 `env=<id> status=<名称>`。

### 样例

输入：

```
3 2
```

输出：

```
env=3 status=RUNNABLE
```
