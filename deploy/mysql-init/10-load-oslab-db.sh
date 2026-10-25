#!/bin/sh
set -eu

# The supplied dump does not contain USE oslab_db. MySQL's entrypoint creates
# MYSQL_DATABASE first, then this script imports the dump into that database.
#
# 加载顺序（docs/OSLab项目最终检查与收尾执行计划.md §6.1，task C-1 补齐 v4/v5）：
#   1. 基础 oslab_db.sql（已内置 uk_userAccount，因此本脚本不执行 v2）
#   2. oslab_upgrade_v3_learning.sql    学习闭环 6 张 P0 表
#   3. oslab_upgrade_v4_flow.sql        测评会话 / 作答幂等 / 事件快照
#                                       （新库首装时列必然不存在，ALTER 必成功）
#   4. oslab_upgrade_v5_teaching.sql    节点共享学习素材表
#   5. oslab_demo_seed_v3.sql           题目/映射演示种子（INSERT IGNORE 幂等）
#   6. oslab_seed_v4_content.sql        内容种子（INSERT IGNORE / ON DUPLICATE KEY）
#   7. oslab_upgrade_v6_courses.sql     课程/内容/进度与真实目录草稿
#   8. oslab_upgrade_v7_identity.sql    稳定身份/题目修订/活动上下文
#   9. oslab_upgrade_v8_teaching_tasks.sql 学习任务/私有执行副本/任务附件与证据来源
#  10. oslab_upgrade_v9_learning_activities.sql 普通演示运行记录（与掌握证据分离）
# 十个文件均为必需：缺挂载即失败退出，不再静默跳过（失败即失败，不吞错）。
# 已有数据卷不会重跑本目录脚本；存量库请使用
#   数据库文件/oslab_upgrade_existing_check.sql
# 做信息 schema 校验后的条件增量（不要对旧库重复执行 v4 的 ALTER）。

run_mysql() {
    mysql --protocol=socket -uroot "-p${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" "$@"
}

load_required_seed() {
    seed_path="$1"
    seed_label="$2"
    if [ ! -f "$seed_path" ]; then
        echo "[FATAL] 缺少必需的 SQL 文件挂载: $seed_path ($seed_label)" >&2
        exit 1
    fi
    echo "[load] $seed_label"
    run_mysql < "$seed_path"
}

echo "=== OSLab 数据库初始化：开始加载 ==="
load_required_seed /seed/oslab_db.sql                    "1/9 基础库 oslab_db.sql"
load_required_seed /seed/oslab_upgrade_v3_learning.sql   "2/9 v3 学习闭环表"
load_required_seed /seed/oslab_upgrade_v4_flow.sql       "3/9 v4 测评会话/幂等/快照"
load_required_seed /seed/oslab_upgrade_v5_teaching.sql   "4/9 v5 教学素材表"
load_required_seed /seed/oslab_demo_seed_v3.sql          "5/9 v3 演示种子"
load_required_seed /seed/oslab_seed_v4_content.sql       "6/9 v4 内容种子"
load_required_seed /seed/oslab_upgrade_v6_courses.sql    "7/9 v6 课程/内容/进度草稿"
load_required_seed /seed/oslab_upgrade_v7_identity.sql   "8/9 v7 稳定身份/修订/提交上下文"
load_required_seed /seed/oslab_upgrade_v8_teaching_tasks.sql "9/10 v8 学习任务/执行副本/任务附件"
load_required_seed /seed/oslab_upgrade_v9_learning_activities.sql "10/10 v9 普通演示运行记录"

# ---------------------------------------------------------------------
# 加载后契约校验（§6.1.2-6）：逐项核对 information_schema，任一不符即整体失败。
# ---------------------------------------------------------------------
expect_scalar() {
    sql_text="$1"
    expected="$2"
    label="$3"
    actual=$(run_mysql -N -B -e "$sql_text")
    if [ "$actual" != "$expected" ]; then
        echo "[FATAL] 契约校验失败: $label (期望 $expected, 实际 $actual)" >&2
        exit 1
    fi
    echo "[ok] $label = $actual"
}

expect_min() {
    sql_text="$1"
    minimum="$2"
    label="$3"
    actual=$(run_mysql -N -B -e "$sql_text")
    if [ -z "$actual" ] || [ "$actual" -lt "$minimum" ]; then
        echo "[FATAL] 契约校验失败: $label (要求 >= $minimum, 实际 $actual)" >&2
        exit 1
    fi
    echo "[ok] $label = $actual (>= $minimum)"
}

# 1) v3 六张 P0 表全部存在
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_question','learning_question_node','learning_attempt','learning_event','learning_mastery','learning_review')" \
    6 "v3 learning_* 六张表存在"

# 2) v4 测评会话表 + v5 素材表存在
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_assessment_run','learning_node_content')" \
    2 "learning_assessment_run / learning_node_content 表存在"

# 3) learning_attempt.assessmentRunId / submissionKey 列存在
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_attempt' AND COLUMN_NAME IN ('assessmentRunId','submissionKey')" \
    2 "learning_attempt.assessmentRunId/submissionKey 列存在"

# 4) learning_attempt 的 uk_user_submission 唯一索引存在
expect_scalar \
    "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_attempt' AND INDEX_NAME='uk_user_submission'" \
    1 "learning_attempt.uk_user_submission 索引存在"

# 5) learning_event.snapshotBefore / snapshotAfter / eventGroupKey 列存在
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_event' AND COLUMN_NAME IN ('snapshotBefore','snapshotAfter','eventGroupKey')" \
    3 "learning_event.snapshotBefore/snapshotAfter/eventGroupKey 列存在"

# 6) learning_event 的 idx_user_group 索引存在
expect_scalar \
    "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_event' AND INDEX_NAME='idx_user_group'" \
    1 "learning_event.idx_user_group 索引存在"

# 7) 基础库版本正确：uk_userAccount 已内置（v2 产物，不允许重复执行）
expect_scalar \
    "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND INDEX_NAME='uk_userAccount'" \
    1 "user.uk_userAccount 索引存在（基础库版本正确）"

# 8) 内容种子依赖（§6.1.6）：存在有效管理员，updateUserId 子查询才能取到值
expect_min \
    "SELECT COUNT(*) FROM \`user\` WHERE userRole='admin' AND isDelete=0" \
    1 "存在有效管理员账号（内容种子 updateUserId 依赖）"

# 9) 种子行数核对
expect_scalar \
    "SELECT COUNT(*) FROM learning_question WHERE id BETWEEN 910001 AND 910026" \
    26 "题目种子 910001-910026 共 26 道"
expect_scalar \
    "SELECT COUNT(*) FROM learning_question_node WHERE id BETWEEN 911001 AND 911050" \
    50 "题目-节点映射种子 911001-911050 共 50 条"
expect_scalar \
    "SELECT COUNT(*) FROM learning_node_content" \
    12 "节点素材种子共 12 条"

expect_scalar \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('course_catalog','course_section','learning_content_chunk','learning_unit','learning_unit_position','knowledge_node_registry','knowledge_node_relation','experiment_node')" \
    8 "v6/v7 课程、内容、位置及稳定身份核心表存在"
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_attempt','question_submit') AND COLUMN_NAME IN ('sourceUnitCode','sourceActivityCode')" \
    4 "知识题/实验真实提交的单元活动上下文存在"
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_task','learning_task_item','learning_task_recipient','learning_task_item_work','learning_task_submission','learning_task_file')" \
    6 "v8 学习任务六张表存在"
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_question','question') AND COLUMN_NAME='resourceScope'" \
    2 "v8 共享/任务资源作用域列存在"
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('learning_attempt','question_submit','learning_event') AND COLUMN_NAME='sourceTaskCode'" \
    3 "v8 任务来源字段存在"
expect_scalar \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_activity_record'" \
    1 "v9 普通演示运行记录表存在"
echo "=== OSLab 数据库初始化：加载与契约校验全部通过 ==="
