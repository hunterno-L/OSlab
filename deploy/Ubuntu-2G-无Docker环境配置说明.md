# Ubuntu 2G 内存 · 无 Docker 环境配置说明

> 适用对象：Ubuntu 22.04 / 24.04 LTS，2 核 2 GB 内存的云服务器或虚拟机。
> 目标：不使用 Docker，全部组件以 apt 包或 systemd 服务方式运行。
> 相关文档：[裸机部署实录与运维手册](裸机部署实录与运维手册-39.106.136.15.md)（含 Docker 判题沙箱的同规格实例）、[部署指南](部署指南.md)（Docker Compose 方案）。

---

## 1. 结论先行

| 项目 | 结论 |
| --- | --- |
| 能否在 2 GB 内存运行 | 可以，但只能运行精简栈，且必须配置 2 GB swap |
| 需要哪些组件 | Nginx + Spring Boot + MySQL + Redis + Neo4j，全部裸机 |
| 省略哪些组件 | Elasticsearch、Docker 判题沙箱 |
| 功能影响 | 平台主链路（课程、知识图谱、题目、学习记录、教学管理、模拟演示）完整可用；**代码编程实验不可用**；旧帖子搜索不可用 |
| 能否在服务器上构建 | 不建议。Maven 与 npm 构建峰值会超过 2 GB，请在本机构建后上传产物 |
| 推荐配置 | 4 GB 内存可显著降低 OOM 风险；2 GB 属于可运行下限 |

**无 Docker 的直接后果**：后端生产环境只允许 `CODESANDBOX_TYPE=remote`，而官方远程沙箱本身依赖 Docker 启动一次性 worker。没有 Docker 就没有可信执行环境，因此代码提交会安全地判为"系统错误"，不会误判为通过。详见第 12 节。

---

## 2. 环境要求

### 2.1 硬件

| 项目 | 最低 | 建议 |
| --- | --- | --- |
| CPU | 2 核 | 2 核 |
| 内存 | 2 GB | 4 GB |
| swap | 2 GB | 2 GB |
| 磁盘 | 25 GB 可用 | 40 GB |
| 带宽 | 1 Mbps | 3 Mbps 以上 |

磁盘占用参考：后端 jar 约 150 MB，前端 dist 约 80 MB，MySQL 数据约 200 MB，Neo4j 数据约 100 MB，加上系统与日志，首次部署后约占用 5–8 GB。

### 2.2 软件版本

| 软件 | 版本 | 用途 |
| --- | --- | --- |
| Ubuntu Server | 22.04 / 24.04 LTS x86_64 | 操作系统 |
| Nginx | 1.18+（apt 自带） | 静态站与 `/api` 反向代理 |
| MySQL | 8.0.x（apt 自带） | 主数据库 `oslab_db` |
| Redis | 7.x（apt 自带） | Session 与缓存 |
| Neo4j | 4.4.x Community | 知识图谱 |
| OpenJDK | 17（后端）+ 11（Neo4j） | 运行时 |
| 构建机 | JDK 17+、Node.js 20 LTS+、npm 10+ | 本地打包 |

注意：Neo4j 4.4 只支持 Java 8/11，不能用 Java 17 运行；后端需要 Java 17。两种 JDK 可以共存，通过 `JAVA_HOME` 区分。

### 2.3 端口

| 端口 | 服务 | 暴露范围 |
| --- | --- | --- |
| 22 | SSH | 按需限制来源 IP |
| 80 | Nginx | 公网 |
| 443 | Nginx（可选 HTTPS） | 公网 |
| 8121 | Spring Boot | 仅 127.0.0.1 |
| 3306 | MySQL | 仅 127.0.0.1 |
| 6379 | Redis | 仅 127.0.0.1 |
| 7687 / 7474 | Neo4j Bolt / HTTP | 仅 127.0.0.1 |

云服务器还需在安全组中只放行 22、80（以及可选的 443），其余端口不要对公网开放。

### 2.4 时区与编码

统一使用 `Asia/Shanghai` 时区与 UTF-8 编码，数据库字符集为 `utf8mb4`。

---

## 3. 架构与内存预算

```
浏览器
  │  http://<服务器IP>   (80)
  ▼
Nginx
  ├── /            → /var/www/oslab 静态 SPA
  └── /api/        → 127.0.0.1:8121 (Spring Boot, prod)
                        ├── MySQL   127.0.0.1:3306
                        ├── Redis   127.0.0.1:6379
                        └── Neo4j   127.0.0.1:7687
```

| 组件 | 关键配置 | 常驻内存（实测量级） |
| --- | --- | --- |
| 系统 + systemd + journald | — | 150–200 MB |
| Nginx | `worker_processes 1` | 10–20 MB |
| MySQL | buffer pool 128 MB，关闭 performance_schema | 250–350 MB |
| Redis | `maxmemory 96mb` | 10–20 MB |
| Neo4j | heap 128–192 MB，pagecache 128 MB | 330–450 MB |
| Spring Boot | `-Xmx256m -XX:+UseSerialGC` | 350–450 MB |
| **合计** | | **约 1.1–1.5 GB** |

剩余内存留给页缓存与突发流量，2 GB 物理内存 + 2 GB swap 可以稳定运行；若并发较高或需要判题，请升级到 4 GB。

---

## 4. 系统准备

### 4.1 基础设置

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget gnupg lsb-release unzip ufw

sudo timedatectl set-timezone Asia/Shanghai
timedatectl
```

### 4.2 swap 与内核参数

2 GB 内存必须配置 swap，否则导入数据库或构建缓存时容易触发 OOM Killer。

```bash
# 创建 2 GB swap 文件
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 降低换出倾向，保证热数据留在内存
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-oslab.conf
echo 'vm.overcommit_memory=1' | sudo tee -a /etc/sysctl.d/99-oslab.conf
echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.d/99-oslab.conf
sudo sysctl --system

free -h
swapon --show
```

### 4.3 防火墙

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

数据库与后端端口不开放，靠"只监听 127.0.0.1"保证不可达。

---

## 5. MySQL

### 5.1 安装与低内存调优

```bash
sudo apt install -y mysql-server
```

先调优再导入数据，避免导入过程内存冲高：

```bash
sudo tee /etc/mysql/mysql.conf.d/zz-oslab.cnf > /dev/null <<'EOF'
[mysqld]
bind-address            = 127.0.0.1
character-set-server    = utf8mb4
collation-server        = utf8mb4_unicode_ci
default-time-zone       = '+08:00'

# 2 GB 内存下的保守配置
innodb_buffer_pool_size = 128M
innodb_buffer_pool_instances = 1
innodb_redo_log_capacity = 64M
innodb_flush_method     = O_DIRECT
max_connections         = 60
table_open_cache        = 200
tmp_table_size          = 16M
max_heap_table_size     = 16M
performance_schema      = OFF
skip-name-resolve
EOF

sudo systemctl restart mysql
sudo systemctl enable mysql
```

说明：`innodb_redo_log_capacity` 需要 MySQL 8.0.30 及以上；更早的版本改用 `innodb_log_file_size=64M`。`performance_schema=OFF` 可省下上百 MB 内存，代价是失去细粒度性能视图。

### 5.2 创建数据库与应用账号

```bash
sudo mysql <<'SQL'
CREATE DATABASE IF NOT EXISTS oslab_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'oslab_app'@'localhost' IDENTIFIED BY '<MySQL应用密码>';
CREATE USER IF NOT EXISTS 'oslab_app'@'127.0.0.1' IDENTIFIED BY '<MySQL应用密码>';
GRANT ALL PRIVILEGES ON oslab_db.* TO 'oslab_app'@'localhost';
GRANT ALL PRIVILEGES ON oslab_db.* TO 'oslab_app'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
```

### 5.3 导入数据

**推荐：导入五章部署包。** 把 `deploy/database-20261028/` 上传到 `/opt/oslab/db/`，其中 `oslab_db-deploy.sql.gz` 已包含第一至第五章的单元、916 道知识题和 95 张教学资源图，且已脱敏：

```bash
gunzip -c /opt/oslab/db/oslab_db-deploy.sql.gz \
  | sudo mysql --default-character-set=utf8mb4 oslab_db

sudo mysql oslab_db -e "SELECT COUNT(*) AS units FROM learning_unit;"   # 104
```

包内不含 `DROP TABLE`，只能导入空库；细节与校验见包内 `README.md`。

**备选：从初始种子导入。** 把仓库的 `数据库文件/` 目录上传到服务器 `/opt/oslab/db/`，按下面的顺序执行 11 个文件。顺序与 `deploy/mysql-init/10-load-oslab-db.sh` 一致，不可调换。种子只含第一章单元，导入后还需通过 `/teaching/content` 补齐第二至第五章。

```bash
cd /opt/oslab/db
for f in \
  oslab_db.sql \
  oslab_upgrade_v3_learning.sql \
  oslab_upgrade_v4_flow.sql \
  oslab_upgrade_v5_teaching.sql \
  oslab_demo_seed_v3.sql \
  oslab_seed_v4_content.sql \
  oslab_upgrade_v6_courses.sql \
  oslab_upgrade_v7_identity.sql \
  oslab_upgrade_v8_teaching_tasks.sql \
  oslab_upgrade_v9_learning_activities.sql \
  oslab_upgrade_v10_classes.sql
do
  echo "==> $f"
  sudo mysql --default-character-set=utf8mb4 oslab_db < "$f" || exit 1
done
```

注意：

- 基础库 `oslab_db.sql` 不含 `USE oslab_db`，必须像上面这样指定库名导入。
- 不要执行 `oslab_upgrade_v2.sql`，基础库已包含其产物。
- 存量库升级使用 `oslab_upgrade_existing_check.sql` 做校验，不要对已有库重复执行 v4 的 `ALTER`。
- 导入完成后确认基础数据：

```bash
sudo mysql oslab_db -e "
SELECT COUNT(*) AS admin_users FROM user WHERE userRole='admin' AND isDelete=0;
SELECT COUNT(*) AS questions FROM learning_question;
SELECT COUNT(*) AS node_registry FROM knowledge_node_registry;"
```

管理员账号必须至少有一个（种子中的 `nana` 满足该条件），否则 v6 内容种子的 `updateUserId` 取不到值。若走种子路径，五章教学单元需要管理员登录后通过 `/teaching/content` 导入对应资源包；部署包路径已包含这些内容。

### 5.4 关于认证插件

MySQL 8 默认使用 `caching_sha2_password`，非 SSL 连接需要获取服务端公钥。本文的 JDBC URL 已带 `allowPublicKeyRetrieval=true`，无需修改服务端认证插件。

---

## 6. Redis

### 6.1 安装与配置

```bash
sudo apt install -y redis-server
```

编辑 `/etc/redis/redis.conf`，确认或修改以下项：

```conf
bind 127.0.0.1 ::1
protected-mode yes
port 6379
requirepass <Redis强密码>
maxmemory 96mb
maxmemory-policy noeviction
appendonly no
supervised systemd
```

说明：

- `noeviction` 防止内存满时静默淘汰 Session 导致用户随机掉线；96 MB 对本站点用量绰绰有余。
- `appendonly no` 降低磁盘写入；Redis 只存 Session 与缓存，重启后重新登录即可。
- 如果希望重启保留登录态，可改为 `appendonly yes`。

```bash
sudo systemctl restart redis-server
sudo systemctl enable redis-server
redis-cli -a '<Redis强密码>' --no-auth-warning ping   # 期望 PONG
```

### 6.2 Ubuntu 24.04 专项

部分 24.04 环境会出现 Redis 在 systemd 下启动失败、手动运行正常，报 `libjemalloc.so.2: failed to map segment from shared object`。这是 systemd 的 Landlock 文件执行限制导致，可用 drop-in 规避：

```bash
sudo mkdir -p /etc/systemd/system/redis-server.service.d
sudo tee /etc/systemd/system/redis-server.service.d/oslab.conf > /dev/null <<'EOF'
[Service]
MemoryDenyWriteExecute=false
NoExecPaths=
ExecPaths=
EOF
sudo systemctl daemon-reload
sudo systemctl reset-failed redis-server
sudo systemctl restart redis-server
```

Redis 不执行不可信代码，其余加固项（`ProtectSystem`、`NoNewPrivileges` 等）保持默认即可。

---

## 7. Neo4j

### 7.1 安装 Java 11 与 Neo4j 4.4

```bash
sudo apt install -y openjdk-11-jre-headless

wget -qO- https://debian.neo4j.com/neotechnology.gpg.key \
  | sudo gpg --dearmor -o /etc/apt/keyrings/neotechnology.gpg
echo 'deb [signed-by=/etc/apt/keyrings/neotechnology.gpg] https://debian.neo4j.com stable 4.4' \
  | sudo tee /etc/apt/sources.list.d/neo4j.list

sudo apt update
sudo apt install -y neo4j
```

把 Neo4j 固定在 Java 11，避免后续安装 Java 17 后管理工具崩溃：

```bash
sudo mkdir -p /etc/systemd/system/neo4j.service.d
sudo tee /etc/systemd/system/neo4j.service.d/oslab-java.conf > /dev/null <<'EOF'
[Service]
Environment="JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64"
EOF
sudo systemctl daemon-reload
```

### 7.2 低内存配置

编辑 `/etc/neo4j/neo4j.conf`：

```conf
dbms.default_listen_address=127.0.0.1
dbms.connector.bolt.listen_address=127.0.0.1:7687
dbms.connector.http.listen_address=127.0.0.1:7474
dbms.security.auth_enabled=true

dbms.memory.heap.initial_size=128m
dbms.memory.heap.max_size=192m
dbms.memory.pagecache.size=128m
```

### 7.3 导入图谱数据

**推荐：导入五章完整图谱。** `deploy/database-20261028/neo4j-graph-full.cypher` 含 346 个节点、425 条关系。脚本使用 `CREATE`，只能导入空图，且不要与下面的 `graph.db.dump` 或 7.4 的章节脚本叠加。

```bash
sudo systemctl stop neo4j

sudo rm -rf /var/lib/neo4j/data/databases/neo4j \
            /var/lib/neo4j/data/databases/system \
            /var/lib/neo4j/data/transactions/neo4j \
            /var/lib/neo4j/data/transactions/system

sudo -u neo4j env \
  JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  NEO4J_CONF=/etc/neo4j \
  NEO4J_HOME=/usr/share/neo4j \
  neo4j-admin set-initial-password '<Neo4j强密码>'

sudo systemctl start neo4j
until ss -lnt | grep -q '127.0.0.1:7687'; do sleep 2; done

sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' \
  -f /opt/oslab/db/neo4j-graph-full.cypher

sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' \
  "MATCH (n:Entity) RETURN count(n) AS nodes;"   # 346
```

**备选：使用旧基础图谱 `graph.db.dump`**（150 节点，需再执行 7.4）。首次导入会清空 Neo4j 自带库，仅对全新实例执行：

```bash
sudo systemctl stop neo4j

sudo rm -rf /var/lib/neo4j/data/databases/neo4j \
            /var/lib/neo4j/data/databases/system \
            /var/lib/neo4j/data/transactions/neo4j \
            /var/lib/neo4j/data/transactions/system

sudo chmod 644 /opt/oslab/db/graph.db.dump
sudo -u neo4j env \
  JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  NEO4J_CONF=/etc/neo4j \
  NEO4J_HOME=/usr/share/neo4j \
  neo4j-admin load --from=/opt/oslab/db/graph.db.dump --database=neo4j --force

sudo -u neo4j env \
  JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  NEO4J_CONF=/etc/neo4j \
  NEO4J_HOME=/usr/share/neo4j \
  neo4j-admin set-initial-password '<Neo4j强密码>'

sudo systemctl start neo4j
sudo systemctl enable neo4j
```

必须同时删除 `databases/system` 与 `transactions/system`。Neo4j 4.4 默认把认证信息存在 system 库，只删业务库会导致初始密码永远不生效。

### 7.4 补齐历史章节标签（可选）

**使用 7.3 的完整图谱包时跳过本节**，完整包已包含章节标签。若走 `graph.db.dump` 路径，基础 dump 是 2025-06 的图谱，仓库额外提供两个幂等 Cypher 文件：

```bash
sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' \
  -f /opt/oslab/db/oslab_graph_chapters.cypher

sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' \
  -f /opt/oslab/db/oslab_upgrade_v6_courses_graph.cypher
```

验证：

```bash
sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' \
  "MATCH (n:Entity) RETURN count(n);"
```

课程章节节点由平台内容导入时同步（`CourseGraphSyncService`），不需要手工创建。

---

## 8. 后端

### 8.1 运行时与目录

```bash
sudo apt install -y openjdk-17-jre-headless
sudo groupadd --system oslab 2>/dev/null || true
sudo useradd --system --gid oslab --home /opt/oslab --shell /usr/sbin/nologin oslab 2>/dev/null || true

sudo mkdir -p /opt/oslab/{app,server,data/task-files,backup}
sudo chown -R oslab:oslab /opt/oslab
```

把本机构建出的 `target/springboot-init-0.0.1-SNAPSHOT.jar` 上传为 `/opt/oslab/app/oslab-backend.jar`。

本机构建命令（在仓库的 `后端代码/osLab-backend` 目录）：

```bash
./mvnw -B -ntp -DskipTests package
```

### 8.2 环境变量文件

创建 `/opt/oslab/server/oslab.env`，权限 600，由 systemd 读取。所有值必须写全，不能依赖 `application-prod.yml` 中面向 Docker 的默认主机名（`mysql`、`redis`、`neo4j`）。

```bash
sudo tee /opt/oslab/server/oslab.env > /dev/null <<'EOF'
TZ=Asia/Shanghai
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8121

SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/oslab_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
SPRING_DATASOURCE_USERNAME=oslab_app
SPRING_DATASOURCE_PASSWORD=<MySQL应用密码>

SPRING_REDIS_HOST=127.0.0.1
SPRING_REDIS_PORT=6379
SPRING_REDIS_DATABASE=1
SPRING_REDIS_PASSWORD=<Redis强密码>
SPRING_REDIS_TIMEOUT=5s

SPRING_NEO4J_URI=bolt://127.0.0.1:7687
SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j
SPRING_NEO4J_AUTHENTICATION_PASSWORD=<Neo4j强密码>

# 未安装 Elasticsearch，指向回环以使旧帖子搜索快速失败，不影响启动
SPRING_ELASTICSEARCH_URIS=http://127.0.0.1:9200

# 同源部署，不开启跨域
CORS_ALLOWED_ORIGINS=

# 无沙箱：留空，代码提交会安全失败
CODESANDBOX_TYPE=remote
CODESANDBOX_REMOTE_URL=
CODESANDBOX_REMOTE_AUTH_SECRET=
CODESANDBOX_REMOTE_TIMEOUT_MS=30000

# AI 可选，不配置时相关能力自动降级
AI_ARK_API_KEY=
AI_ARK_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
AI_ARK_MODEL=deepseek-v3-250324

KNIFE4J_ENABLE=false
KNIFE4J_BASIC_ENABLE=false
OSLAB_TASK_STORAGE_DIR=/opt/oslab/data/task-files
EOF

sudo chown root:oslab /opt/oslab/server/oslab.env
sudo chmod 640 /opt/oslab/server/oslab.env
```

### 8.3 systemd 服务

```bash
sudo tee /etc/systemd/system/oslab-backend.service > /dev/null <<'EOF'
[Unit]
Description=OSLab Spring Boot backend
After=network-online.target mysql.service redis-server.service neo4j.service
Wants=network-online.target

[Service]
Type=simple
User=oslab
Group=oslab
EnvironmentFile=/opt/oslab/server/oslab.env
WorkingDirectory=/opt/oslab
ExecStart=/usr/lib/jvm/java-17-openjdk-amd64/bin/java \
  -Xms128m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=256m \
  -Dfile.encoding=UTF-8 \
  -jar /opt/oslab/app/oslab-backend.jar --spring.profiles.active=prod
Restart=always
RestartSec=5
SuccessExitStatus=143
TimeoutStopSec=25
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now oslab-backend
```

启动约需 15–30 秒，随后检查：

```bash
systemctl status oslab-backend --no-pager
curl -fsS http://127.0.0.1:8121/api/health/liveness
free -m
```

`TimeoutStopSec=25` 用于兜底：Spring 启动失败时可能卡在关闭阶段，SIGKILL 让 `Restart=always` 能真正生效。

---

## 9. 前端

### 9.1 本机构建

2 GB 机器不要跑 webpack 构建。在本机仓库的 `前端代码/osLab-frontend` 目录执行：

```bash
npm ci
npm run build
tar czf oslab-dist.tar.gz -C dist .
```

要求 Node.js 20 LTS 及以上（Vue CLI 5）。构建完成后只需上传 `oslab-dist.tar.gz`。

### 9.2 上传与解压

```bash
sudo mkdir -p /var/www/oslab
sudo tar xzf oslab-dist.tar.gz -C /var/www/oslab
sudo chown -R root:www-data /var/www/oslab
sudo find /var/www/oslab -type d -exec chmod 755 {} \;
sudo find /var/www/oslab -type f -exec chmod 644 {} \;
```

### 9.3 Nginx

```bash
sudo apt install -y nginx
```

`/etc/nginx/nginx.conf` 的 `events` 与全局部分保持默认，仅建议把 worker 数固定为 1：

```conf
worker_processes 1;
```

新建 `/etc/nginx/sites-available/oslab.conf`：

```nginx
server {
    listen 80 default_server;
    server_name _;

    root /var/www/oslab;
    index index.html;
    client_max_body_size 12m;

    gzip on;
    gzip_min_length 1k;
    gzip_types text/css application/javascript application/json image/svg+xml;

    # SPA 壳与 API 默认重验证，避免微信等内核缓存旧页面
    add_header Cache-Control "no-cache";

    # 带内容 hash 的产物缓存一年（注意正则整体加引号）
    location ~* "[-.][0-9a-f]{8}\.(js|css|png|jpg|jpeg|gif|svg|woff2?|ttf|eot)$" {
        add_header Cache-Control "public, max-age=31536000, immutable";
        try_files $uri =404;
    }

    location ^~ /api/ {
        proxy_pass http://127.0.0.1:8121;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_buffering off;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用站点并检查：

```bash
sudo ln -sf /etc/nginx/sites-available/oslab.conf /etc/nginx/sites-enabled/oslab.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
sudo systemctl enable nginx
```

如后续绑定域名并启用 HTTPS，把 80 端口配置改为跳转 443，证书用 certbot 申请即可；平台同源访问，不需要额外改后端配置。

---

## 10. 启动顺序与验收

### 10.1 启动顺序

```bash
sudo systemctl restart mysql redis-server neo4j

# 首次启动先等 Neo4j 就绪（约 20–60 秒）
until ss -lnt | grep -q '127.0.0.1:7687'; do sleep 2; done

sudo systemctl restart oslab-backend
sudo systemctl reload nginx
systemctl is-active mysql redis-server neo4j oslab-backend nginx
```

### 10.2 验收清单

```bash
# 1) 后端存活
curl -fsS http://127.0.0.1:8121/api/health/liveness

# 2) 首页与 API 代理
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/health/liveness

# 3) 登录（演示账号创建见 11.4）
curl -s -X POST http://127.0.0.1/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"teacher_demo","userPassword":"oslab123456"}'
```

预期：健康检查返回存活状态；首页与 API 均为 200；登录返回 `"code":0`。

使用五章部署包时，再核对数据量：`learning_unit=104`、`learning_question=916`、Neo4j `Entity=346`。

浏览器侧再确认四项：

- 落地页 `/welcome` 正常渲染，登录弹层可打开与关闭；
- 学生登录后进入 `/graph`，知识与图谱可视化有数据；
- 课程目录、单元学习、题目作答可正常打开；
- 管理员登录后 `/teaching/overview` 与 `/teaching/content` 可访问。

### 10.3 资源观察

```bash
free -m
systemctl status oslab-backend neo4j mysql redis-server nginx --no-pager
ss -lntp | grep -E ':(80|8121|3306|6379|7474|7687)\b'
```

`ss` 结果应只有 80 监听 `0.0.0.0`/`::`，其余全部为 `127.0.0.1`。

---

## 11. 日常运维

### 11.1 日志

```bash
journalctl -u oslab-backend -n 100 --no-pager
journalctl -u oslab-backend -f
journalctl -u neo4j -n 100 --no-pager
tail -f /var/log/nginx/error.log
```

日志中的 Elasticsearch 连接失败只影响旧帖子搜索接口；代码沙箱未配置的报错只出现在提交代码时。这两类日志均不影响教学主链路。

### 11.2 重启

```bash
sudo systemctl restart oslab-backend
sudo systemctl restart nginx
```

若后端启动失败后反复无响应，先彻底清理再启动：

```bash
sudo systemctl stop oslab-backend
sudo pkill -9 -f oslab-backend.jar || true
sudo systemctl reset-failed oslab-backend
sudo systemctl start oslab-backend
```

### 11.3 备份

```bash
# MySQL
sudo mysqldump --single-transaction --routines --triggers oslab_db \
  | gzip > /opt/oslab/backup/oslab_db_$(date +%F).sql.gz

# Neo4j（需短暂停机）
sudo systemctl stop neo4j
sudo tar -C /var/lib/neo4j/data -czf /opt/oslab/backup/neo4j-$(date +%F).tar.gz \
  databases/neo4j transactions/neo4j
sudo systemctl start neo4j

# 站点与配置
sudo tar czf /opt/oslab/backup/oslab-site-$(date +%F).tar.gz \
  /var/www/oslab /opt/oslab/server
```

### 11.4 创建管理员与演示账号

种子中的 `nana` 是管理员但密码未知。需要可登录的账号时，先注册再提权：

```bash
curl -s -X POST http://127.0.0.1/api/user/register \
  -H 'Content-Type: application/json' \
  -d '{"userAccount":"teacher_demo","userPassword":"oslab123456","checkPassword":"oslab123456"}'

sudo mysql oslab_db -e "
UPDATE user SET userRole='admin', userName='演示教师'
WHERE userAccount='teacher_demo';"
```

学生账号重复注册步骤即可，不需要提权。重复执行会因账号已存在而失败，属正常现象。

### 11.5 升级发版

1. 本机重新构建 jar 与 dist；
2. 上传并替换 `/opt/oslab/app/oslab-backend.jar` 与 `/var/www/oslab` 内容；
3. `sudo systemctl restart oslab-backend`；
4. `sudo nginx -t && sudo systemctl reload nginx`；
5. 执行 10.2 的验收命令。

不要为了发版重跑数据库导入脚本，那会把线上数据重置回种子状态。

---

## 12. 无 Docker 的能力边界

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 课程、单元、教学块 | 可用 | 依赖 MySQL |
| 知识图谱、掌握度、补救路径 | 可用 | 依赖 Neo4j |
| 判断题/选择题/填空题 | 可用 | 服务端判分，不经过沙箱 |
| 编程实验提交 | **不可用** | 无沙箱，提交记录终态为"系统错误" |
| 交互模拟演示 | 可用 | 纯前端确定性动画，不写判题事件 |
| 旧帖子搜索 | 不可用 | 省略 Elasticsearch |
| AI 导师 | 可选 | 配置 `AI_ARK_API_KEY` 后可用，否则降级 |

为什么不能退化成"本机直接执行"：后端 `CodeSandboxFactory` 在运行期只接受 `remote`，`example`/`thirdParty` 等实现仅用于教学示例且不隔离不可信代码；强行接入等同于让学生代码直接跑在服务器上，属于严重安全风险。**没有隔离环境时应保持判题不可用，而不是绕过隔离。**

如果以后需要恢复判题，有两种合规做法：

1. 在本机安装 Docker，仅运行仓库自带的沙箱服务（常驻约 50 MB，判题瞬时 worker 限 256 MB），其余组件仍保持裸机；
2. 把沙箱部署到另一台机器，通过内网或 SSH 隧道把 `CODESANDBOX_REMOTE_URL` 指过去，并配置共享密钥 `CODESANDBOX_REMOTE_AUTH_SECRET`。

---

## 13. 故障排查

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| 服务随机被杀，`dmesg` 出现 `Out of memory` | 内存不足 | 确认 swap 已启用；把 Neo4j pagecache 降到 64M、MySQL buffer pool 降到 96M；或升级到 4G |
| 后端报 `UnknownHostException: redis` / `mysql` / `neo4j` | `oslab.env` 漏配变量 | 按 8.2 补全全部 `SPRING_*` 变量后重启 |
| MySQL 报 `Public Key Retrieval is not allowed` | JDBC 缺参数 | URL 追加 `allowPublicKeyRetrieval=true` |
| Neo4j 一直 `Unauthorized`，改密码无效 | system 库残留旧凭据 | 停服务，删除 `databases/system` 与 `transactions/system` 后重新初始化 |
| `neo4j-admin` 报 `NEO4J_CONF path doesn't exist` | 手动执行未带环境变量 | 使用 7.3 中带 `NEO4J_CONF`/`NEO4J_HOME` 的完整命令 |
| 安装 Java 17 后 Neo4j 管理工具崩溃 | 默认 JVM 被切换 | 确认 7.1 的 Java 11 drop-in 已生效 |
| Redis 在 systemd 下 exit 127 | Ubuntu 24.04 Landlock 限制 | 按 6.2 添加 drop-in |
| 后端启动失败后再也起不来 | 进程卡在 Spring shutdown | 按 11.2 的 stop + pkill + reset-failed 处理 |
| `nginx -t` 报 `unknown directive "8}\.(js..."` | 正则中的大括号未加引号 | 整个正则用双引号包裹 |
| 刷新前端子路由 404 | 缺少 SPA 回退 | 确认 `try_files $uri $uri/ /index.html;` |
| 80 端口被占用 | 其他 Web 服务 | `ss -lntp \| grep :80` 找到进程后停用，或改用其他端口并同步安全组 |
| 页面能开但接口 502 | 后端未启动 | `systemctl status oslab-backend` 与 `journalctl -u oslab-backend -n 100` |
| 修改配置后登录态全部丢失 | Redis 重启且未开 AOF | 属预期；如需保留，将 `appendonly` 改为 `yes` |

---

## 14. 安全检查清单

- [ ] 公网仅开放 22、80（及可选 443）；3306、6379、7474、7687、8121 均只监听 127.0.0.1
- [ ] MySQL、Redis、Neo4j 均使用独立强密码，且未写入仓库或文档
- [ ] `/opt/oslab/server/oslab.env` 权限为 600 或 640，属主为 root
- [ ] 后端以 `oslab` 系统用户运行，不以 root 运行
- [ ] `KNIFE4J_ENABLE=false`，生产环境不暴露接口文档
- [ ] `CORS_ALLOWED_ORIGINS` 留空（同源部署）
- [ ] SSH 使用密钥登录，禁用密码登录（可选但强烈建议）
- [ ] 已配置数据库与图谱的定期备份，并验证过恢复流程
- [ ] 判题沙箱未配置时，确认代码实验入口的失败提示符合预期，不产生"伪通过"

---

## 15. 命令速查

```bash
# 状态与日志
systemctl status mysql redis-server neo4j oslab-backend nginx --no-pager
journalctl -u oslab-backend -f
free -m && swapon --show

# 重启后端
systemctl restart oslab-backend

# 健康检查
curl -fsS http://127.0.0.1:8121/api/health/liveness
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/

# 数据库
sudo mysql oslab_db
redis-cli -a '<Redis强密码>' --no-auth-warning ping
sudo -u neo4j env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  cypher-shell -a bolt://127.0.0.1:7687 -u neo4j -p '<Neo4j强密码>' "RETURN 1;"

# Nginx
nginx -t && systemctl reload nginx
```

---

## 16. 文件清单

| 路径 | 内容 |
| --- | --- |
| `/opt/oslab/app/oslab-backend.jar` | 后端产物 |
| `/opt/oslab/server/oslab.env` | 运行环境变量与密钥 |
| `/opt/oslab/db/*.sql` | 11 个数据库初始化/增量脚本 |
| `/opt/oslab/db/graph.db.dump` | Neo4j 基础图谱 |
| `/opt/oslab/db/*.cypher` | 图谱章节标签补齐脚本 |
| `/opt/oslab/db/oslab_db-deploy.sql.gz` | 五章脱敏数据库包（推荐，替代 11 个种子 SQL） |
| `/opt/oslab/db/neo4j-graph-full.cypher` | 五章完整图谱包（推荐，替代 graph.db.dump） |
| `/opt/oslab/data/task-files` | 教学任务附件目录 |
| `/var/www/oslab` | 前端静态站 |
| `/etc/systemd/system/oslab-backend.service` | 后端服务单元 |
| `/etc/nginx/sites-available/oslab.conf` | 站点配置 |
| `/etc/mysql/mysql.conf.d/zz-oslab.cnf` | MySQL 低内存配置 |
| `/etc/neo4j/neo4j.conf` | Neo4j 低内存与回环配置 |
