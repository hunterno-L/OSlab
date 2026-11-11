#!/bin/sh
# 拼装、派生与验证助手（只读复用 ch2 片段）
# usage: sh artifacts/experiment-judge/ch3/mk.sh <3|4> <id> [额外片段名(不含 .c.inc)...]
# 例:    sh artifacts/experiment-judge/ch3/mk.sh 3 1930154686162456577 ch3/env-init
set -e
CH="$1"; ID="$2"; shift 2
J=artifacts/experiment-judge
if [ "$CH" = "3" ]; then
  DIR="content/operating-systems/wangdao-2025/experiments/A-第三章-实验判题-001/experiment-files/$ID"
else
  DIR="content/operating-systems/wangdao-2025/experiments/A-第四章-实验判题-001/experiment-files/$ID"
fi
if [ "$ID" = "1908900051330306049" ]; then
  BASE="$J/ch3/env-boot-pre.c.inc $J/ch3/env-pcbdef.c.inc $J/ch3/env-boot-vm.c.inc"
else
  BASE="$J/ch2/env-list.c.inc $J/ch2/env-pageinit.c.inc $J/ch2/env-alloc.c.inc $J/ch2/env-walk.c.inc $J/ch2/env-map.c.inc $J/ch3/env-pcbdef.c.inc $J/ch3/env-pcb.c.inc $J/ch3/env-recorder.c.inc $J/ch3/env-insert.c.inc"
fi
EXTRA=""
for f in "$@"; do EXTRA="$EXTRA $J/$f.c.inc"; done
node scripts/assemble-harness.mjs "$DIR/starter.c" $BASE $EXTRA "$J/ch$CH/body-$ID.c"
node scripts/derive-harness-variants.mjs "$DIR"
node scripts/verify-experiment-harness.mjs "$DIR"
