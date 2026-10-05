#!/bin/sh
set -eu

# The supplied dump does not contain USE oslab_db. MySQL's entrypoint creates
# MYSQL_DATABASE first, then this script imports the dump into that database.
mysql --protocol=socket -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < /seed/oslab_db.sql

# 智能学习闭环增量契约（learning_* 6 张 P0 表，见 docs/learning-contract-audit.md）
if [ -f /seed/oslab_upgrade_v3_learning.sql ]; then
    mysql --protocol=socket -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < /seed/oslab_upgrade_v3_learning.sql
fi

# 演示种子数据（可重复执行：INSERT IGNORE 幂等）
if [ -f /seed/oslab_demo_seed_v3.sql ]; then
    mysql --protocol=socket -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < /seed/oslab_demo_seed_v3.sql
fi
