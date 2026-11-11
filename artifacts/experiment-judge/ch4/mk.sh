#!/bin/sh
# 第四章 harness 生成 + 拼装 + 派生 + 三门验证助手
# usage: sh artifacts/experiment-judge/ch4/mk.sh <id> <排除的片段名|-> [额外片段名...]
#   片段名不含 .c.inc，例如 ch4/sys-memalloc、ch4/env-user
#   第二个参数是被本实验当作学生填写目标、因而要从平台基座里排除的片段；无排除写 -
#   额外片段里出现 ch4/env-user  -> 用户态实验：前置 config-user，裁掉 yield/IPC 段，
#                                   并启用驱动的 V/D/X/W 操作符
#   排除或额外片段里出现 ch4/env-traps -> 前置 config-trap，启用驱动的 M 操作符
#   排除或额外片段里出现 ch4/user-pgfault -> 前置 config-pgfault，启用驱动的 X/Z 操作符
#   出现 ch4/user-fork -> 前置 config-fork，启用 V；出现 ch4/user-duppage -> 前置 config-duppage，启用 D
#   （四个用户态操作符各自门控：不装配对应函数的实验里该操作符按未知输入拒绝，
#     避免出现隐式声明或「声明了 static 却始终没定义」的编译错误）
# 例: sh artifacts/experiment-judge/ch4/mk.sh 1930156493454495745 ch4/env-handlesys
set -e
ID="$1"; EXCL="$2"; shift 2
J=artifacts/experiment-judge
DIR="content/operating-systems/wangdao-2025/experiments/A-第四章-实验判题-001/experiment-files/$ID"

BASE="$J/ch2/env-list.c.inc $J/ch2/env-pageinit.c.inc $J/ch2/env-alloc.c.inc \
$J/ch2/env-walk.c.inc $J/ch2/env-map.c.inc \
$J/ch3/env-pcbdef.c.inc $J/ch3/env-pcb.c.inc $J/ch3/env-recorder.c.inc $J/ch3/env-insert.c.inc \
$J/ch3/env-init.c.inc $J/ch3/env-envid2env.c.inc $J/ch3/env-setupvm.c.inc \
$J/ch3/env-envalloc.c.inc $J/ch3/env-run.c.inc $J/ch3/env-sched.c.inc \
$J/ch4/env-sysno.c.inc $J/ch4/sys-memalloc.c.inc $J/ch4/sys-memmap.c.inc \
$J/ch4/sys-memunmap.c.inc $J/ch4/sys-yield.c.inc $J/ch4/sys-ipc.c.inc \
$J/ch4/sys-envalloc.c.inc $J/ch4/sys-setpgfault.c.inc $J/ch4/sys-setstatus.c.inc \
$J/ch4/env-systable.c.inc $J/ch4/env-handlesys.c.inc"

USER=0
TRAP=0
PGFAULT=0
FORK=0
DUPPAGE=0
if [ "$EXCL" = "ch4/env-traps" ]; then TRAP=1; fi
if [ "$EXCL" = "ch4/user-pgfault" ]; then PGFAULT=1; fi
if [ "$EXCL" = "ch4/user-fork" ]; then FORK=1; fi
if [ "$EXCL" = "ch4/user-duppage" ]; then DUPPAGE=1; fi
for f in "$@"; do
  if [ "$f" = "ch4/env-user" ]; then USER=1; fi
  if [ "$f" = "ch4/env-traps" ]; then TRAP=1; fi
  if [ "$f" = "ch4/user-pgfault" ]; then PGFAULT=1; fi
  if [ "$f" = "ch4/user-fork" ]; then FORK=1; fi
  if [ "$f" = "ch4/user-duppage" ]; then DUPPAGE=1; fi
done

# 用户态实验的源码预算裁剪：fork/duppage/pgfault 从不触发 yield 与 IPC，
# 去掉这两段及其依赖的 env_run/sched_yield，以及只被 env_run 调用的
# lcontext/env_pop_tf 记录替身（env-recorder），可腾出约 7.5KB（详见 env-sysno 段）。
if [ "$USER" = "1" ]; then
  KEPT=""
  for f in $BASE; do
    case "$f" in
      "$J/ch3/env-run.c.inc"|"$J/ch3/env-sched.c.inc"|"$J/ch3/env-recorder.c.inc"|"$J/ch4/sys-yield.c.inc"|"$J/ch4/sys-ipc.c.inc") ;;
      *) KEPT="$KEPT $f" ;;
    esac
  done
  BASE="$KEPT"
fi

# 缺页实验（4.11）的源码预算裁剪：page_fault_handler 由驱动的 M 操作符直接调用，
# 全程不经系统调用分发，故整个 ch4 系统调用层（调用号/9 个 sys_*/分发表/handle_sys）
# 都可省掉，腾出约 12KB；驱动的 K 操作符同步用 #ifndef CH4_TRAP_DRIVER 关掉。
# 被考查函数只依赖 ch2/ch3 的页表与物理页设施，行为与完整平台逐字一致。
if [ "$TRAP" = "1" ]; then
  KEPT=""
  for f in $BASE; do
    case "$f" in
      "$J/ch4/"*) ;;
      *) KEPT="$KEPT $f" ;;
    esac
  done
  BASE="$KEPT"
fi

if [ "$EXCL" != "-" ]; then
  KEPT=""
  for f in $BASE; do
    if [ "$f" != "$J/$EXCL.c.inc" ]; then KEPT="$KEPT $f"; fi
  done
  BASE="$KEPT"
fi

CONFIG=""
if [ "$USER" = "1" ]; then CONFIG="$CONFIG $J/ch4/config-user.c.inc"; fi
if [ "$TRAP" = "1" ]; then CONFIG="$CONFIG $J/ch4/config-trap.c.inc"; fi
if [ "$PGFAULT" = "1" ]; then CONFIG="$CONFIG $J/ch4/config-pgfault.c.inc"; fi
if [ "$FORK" = "1" ]; then CONFIG="$CONFIG $J/ch4/config-fork.c.inc"; fi
if [ "$DUPPAGE" = "1" ]; then CONFIG="$CONFIG $J/ch4/config-duppage.c.inc"; fi

EXTRA=""
for f in "$@"; do EXTRA="$EXTRA $J/$f.c.inc"; done

node "$J/ch4/gen-body.mjs" "$ID"
node scripts/assemble-harness.mjs "$DIR/starter.c" $CONFIG $BASE $EXTRA "$J/ch4/body-$ID.c"
node scripts/derive-harness-variants.mjs "$DIR"
node scripts/verify-experiment-harness.mjs "$DIR"
node scripts/probe-harness-robustness.mjs --probes "$J/ch4/probes.json" "$DIR"