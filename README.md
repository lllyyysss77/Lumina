# Lumina

Lumina 是一个面向多模型、多供应商场景的 LLM API Gateway。它提供统一中继入口、协议互转、模型分组路由、供应商转发、失败切换、熔断与隔离保护、统计聚合引擎，以及一套可直接使用的管理后台。

当前版本：`v0.4.0`

许可证：`AGPL-3.0`

## 项目定位

这个项目不是 SDK，也不是单一厂商适配层，而是一个放在业务入口前面的网关：

- 对上游应用暴露统一 API，支持多协议互转
- 对下游供应商做路由、选择、失败切换和保护
- 对运行态做统计聚合、计费、日志和观测
- 对运维侧提供后台管理、熔断器管控和请求排障能力

## 核心能力

- 统一中继 OpenAI Chat Completions、OpenAI Responses、OpenAI Images Generations、Anthropic Messages、Gemini Models API
- 协议互转：OpenAI Chat / Responses / Anthropic Messages 双向转换，上游可用一种协议调用另一种协议的模型
- 按"对外模型名"做模型分组，将请求路由到一组 Provider
- 在组内基于 SAPR / Top-K Softmax 做加权选择，并接入 weight
- 支持多供应商自动失败切换，限制最大 failover 次数
- 提供熔断器、HALF_OPEN 探测、Bulkhead 并发隔离和运行态恢复
- 维护 Provider 运行态评分、成功率、延迟 EMA、请求计数和状态持久化
- 统计聚合引擎：小时/天级预聚合表 + Redis 实时计数 + 历史回填
- 仪表盘健康热力图：按 15 分钟粒度展示成功率分布
- API Key 消费配额管理，支持额度上限与用量追踪
- 记录完整请求日志，并将详情元数据与正文载荷分离查询
- 暴露 Actuator / Prometheus 指标，并在前端仪表盘展示关键观测数据

## 系统架构概览

请求主路径可以概括为：

`API Key 鉴权 -> 配额校验 -> 模型分组解析 -> 协议转换(可选) -> 候选 Provider 选择 -> 熔断 / Bulkhead 判定 -> HTTP Relay -> usage / cost 统计 -> 异步日志写入 -> 仪表盘与运行态持久化`

当前代码中已经落地的几个关键优化点：

- 热路径缓存：缓存 group config、API key 校验结果、model price，多数请求可降到 0 次读库
- 阻塞 ORM 移出 event loop：关键 MyBatis 调用通过 `Mono.fromCallable(...).subscribeOn(boundedElastic())` 下沉
- 共享 Relay HTTP Client：连接池、连接超时、响应超时、空闲回收和 pending acquire 已集中配置
- 日志链路有界化：有界队列 + 批量写入 + 异步 flush，避免同步日志写库拖慢主链路
- 启动恢复修正：持久化状态若为 `HALF_OPEN`，启动时会归一化处理，不再直接按脏状态恢复
- 统计聚合层：仪表盘查询走预聚合表，消除全表扫描

## 功能清单

### 中继协议

- `GET /v1/models` — 列出可用模型（含真实模型元数据）
- `POST /v1/chat/completions` — OpenAI Chat Completions
- `POST /v1/responses` — OpenAI Responses
- `POST /v1/images/generations` — OpenAI Images Generations
- `POST /v1/messages` — Anthropic Messages
- `POST /v1/messages/count_tokens` — Token 计数
- `POST /v1beta/models/{modelAction}` — Gemini Models API

### 协议互转

支持以下方向的自动转换：

- OpenAI Chat <-> OpenAI Responses
- OpenAI Chat <-> Anthropic Messages
- OpenAI Responses <-> Anthropic Messages

上游应用可用任意一种协议格式访问任意模型，网关自动完成协议适配。

### 控制面

- 供应商管理（含 API Key 安全存储与同步）
- 模型分组与分组项管理（支持按名称条件查询、分页）
- API Key 管理（启用/停用切换、消费配额、过期校验、用量追踪）
- 模型价格管理（支持同一模型多上游供应商价格选择）
- 系统设置管理（含自用模式开关）
- 熔断器状态查看、人工接管、释放接管

### 统计引擎

- 小时/天级预聚合表 (`stats_hourly` / `stats_daily`)
- Redis 实时计数器，提供近实时的请求数、Token 用量、费用数据
- 历史数据回填任务，支持按需重建聚合统计
- 仪表盘直接从聚合层读取，避免全表扫描

### 观测与排障

- 仪表盘概览：总请求数、总 Token 数、费用、平均延迟、成功率
- 24 小时请求流量趋势
- 模型 Token 使用排行
- 供应商统计排行
- 健康热力图：按 15 分钟粒度展示成功率分布
- Provider 运行态列表：分页、按供应商 / 模型 / 状态筛选、按总请求数倒序
- 观测面板：缓存命中、failover、熔断、bulkhead rejection、日志队列等指标
- 请求日志列表与详情（含请求 IP、协议类型）

## 快速开始

### 方式一：Docker Compose

#### 使用本机 MySQL 启动

推荐直接执行部署脚本：

```bash
./deploy-docker.sh
```

脚本会自动完成：

- 检查 Docker / Docker Compose
- 检查并确认本机 MySQL 数据库可用
- 检查宿主机 Redis
- 创建日志目录
- 构建镜像并启动容器
- 验证应用端口是否就绪
- 输出容器自启和 Docker 日志轮转配置

默认使用：

- MySQL：`127.0.0.1:3306/lumina`
- MySQL 用户名/密码：`root` / `root123`
- Redis：`127.0.0.1:6379`
- 后台端口：`8080`

如需覆盖数据库账号：

```bash
LUMINA_MYSQL_USER=root LUMINA_MYSQL_PASSWORD=your_password ./deploy-docker.sh
```

也可以手动使用 Compose：

```bash
docker compose up -d
```

默认会：

- 构建 `lumina:0.4.0`
- 使用宿主机 `127.0.0.1:3306/lumina` MySQL 数据库
- 默认读取 MySQL 用户名/密码：`root` / `root123`
- 设置 `restart: unless-stopped`，Docker 服务启动后自动拉起 Lumina
- 复用宿主机 `127.0.0.1:6379` Redis；如果 Redis 不存在，启动脚本会启动容器内 Redis

如需覆盖数据库账号：

```bash
LUMINA_MYSQL_USER=root LUMINA_MYSQL_PASSWORD=your_password docker compose up -d
```

#### MySQL 部署

```bash
docker compose -f docker-compose-mysql.yml up -d
```

说明：

- `docker-compose-mysql.yml` 会同时启动 MySQL 和 Lumina
- 应用容器内同样会启动本地 Redis
- 首次启动会自动创建默认管理员

默认后台地址：

- `http://localhost:8080`

默认后台账号：

- 用户名：`admin`
- 密码：`admin123`

### 方式二：本地开发

环境要求：

- JDK `17+`
- Maven `3.9+` 或使用项目自带 `./mvnw`
- Node.js `20+`
- `pnpm`
- Redis `6+`
- MySQL `8+` 或 SQLite

#### 后端启动

构建：

```bash
./mvnw clean package -DskipTests
```

使用 SQLite：

```bash
mkdir -p data
java -jar target/lumina-0.4.0.jar --spring.profiles.active=sqlite
```

使用 MySQL：

```bash
java -jar target/lumina-0.4.0.jar --spring.profiles.active=mysql
```

如果你不使用 profile，也可以直接覆盖以下环境变量：

```bash
export SPRING_DATASOURCE_DRIVER=org.sqlite.JDBC
export SPRING_DATASOURCE_URL=jdbc:sqlite:./data/lumina.db
export SPRING_DATASOURCE_USERNAME=
export SPRING_DATASOURCE_PASSWORD=
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379
```

#### 前端启动

```bash
cd lumina-web
pnpm install
pnpm dev
```

## 配置说明

### 数据库

基础配置位于 [application.yaml](src/main/resources/application.yaml)。

支持两类后端存储：

- MySQL：默认生产建议
- SQLite：适合单机、测试和轻量部署

相关配置文件：

- [application-mysql.yaml](src/main/resources/application-mysql.yaml)
- [application-sqlite.yaml](src/main/resources/application-sqlite.yaml)

数据库迁移脚本位于 [db/migration/](src/main/resources/db/migration/)，当前共 7 个版本（V001 - V007），分别提供 MySQL 和 SQLite 两套。应用启动时由 `DatabaseInitializer` 自动执行。

### Redis

当前 Redis 主要用于：

- JWT token 存取与注销
- 运行时状态辅助能力
- 统计实时计数器

容器部署下，Redis 由 [startup.sh](startup.sh) 在应用容器内拉起；本地开发请自行准备 Redis 实例。

### 安全配置

- CORS 允许来源通过 `LUMINA_ALLOWED_ORIGINS` 环境变量配置
- JWT 密钥通过环境变量注入，不硬编码
- 用户密码使用 BCrypt 加密存储
- API Key 在供应商编辑和 API 响应中脱敏

### Lumina 核心配置

在 [LuminaProperties.java](src/main/java/com/lumina/config/LuminaProperties.java) 与 [application.yaml](src/main/resources/application.yaml) 中可见以下关键配置：

- `lumina.circuit-breaker.*`
  - 最小触发请求数
  - 错误率阈值
  - 连续失败阈值
  - 慢调用阈值与慢调用率
  - HALF_OPEN 探测参数
  - failover 最大次数
  - provider 最大并发数
- `lumina.cache.*`
  - `group-config-ttl-seconds`
  - `api-key-ttl-seconds`
  - `model-price-ttl-seconds`
- `lumina.relay.*`
  - `max-connections`
  - `pending-acquire-max-count`
  - `pending-acquire-timeout-ms`
  - `connect-timeout-ms`
  - `response-timeout-ms`
  - `max-idle-time-seconds`
  - `max-life-time-seconds`
- `lumina.logging.*`
  - `queue-capacity`
  - `batch-size`
  - `flush-interval-ms`
  - `success-payload-sample-rate`

## API 使用

### 统一调用方式

使用 Lumina 生成的 API Key，直接请求网关暴露的兼容端点即可：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer LMN_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Hello"}
    ],
    "stream": true
  }'
```

Anthropic：

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer LMN_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-7-sonnet",
    "messages": [
      {"role": "user", "content": "Summarize this text"}
    ]
  }'
```

Gemini：

```bash
curl http://localhost:8080/v1beta/models/gemini-2.5-pro:generateContent \
  -H "Authorization: Bearer LMN_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemini-2.5-pro",
    "contents": [{"role": "user", "parts": [{"text": "Hello"}]}]
  }'
```

### 协议互转示例

用 OpenAI 格式调用 Anthropic 模型：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer LMN_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-3-7-sonnet",
    "messages": [
      {"role": "user", "content": "Hello from OpenAI format"}
    ]
  }'
```

网关会自动将请求转换为 Anthropic Messages 格式发送给上游，响应也会转回 OpenAI 格式。

## 管理后台与可观测性

后台前端位于 [lumina-web](lumina-web)。

当前界面已覆盖：

- Dashboard 概览卡片与健康热力图
- Provider 排行与模型 Token 使用
- Provider 运行态观测列表
- 熔断器管控面板
- 供应商、分组、价格、设置管理
- 令牌管理：独立页面，支持启用/停用切换、用量统计、配额内联编辑
- 请求日志列表与详情弹窗（含请求 IP、协议类型）

### 指标接口

Actuator 与 Prometheus 暴露在：

- `GET /api/v1/actuator/health`
- `GET /api/v1/actuator/info`
- `GET /api/v1/actuator/metrics`
- `GET /api/v1/actuator/prometheus`

### 业务观测接口

- `GET /api/v1/dashboard/overview`
- `GET /api/v1/dashboard/traffic`
- `GET /api/v1/dashboard/model-token-usage`
- `GET /api/v1/dashboard/provider-stats`
- `GET /api/v1/dashboard/observability`
- `GET /api/v1/dashboard/health-heatmap`
- `GET /api/v1/circuit-breaker/management/list`
- `GET /api/v1/circuit-breaker/management/recent-events`

## 日志与存储说明

当前请求日志表结构定义见 [lumina.sql](src/main/resources/db/migration/lumina.sql) 与 [RequestLog.java](src/main/java/com/lumina/entity/RequestLog.java)。

当前实现特征：

- `request_content`、`response_content` 使用 `longtext`
- 请求正文与响应正文当前为全量保留，支持定期清理策略（正文清理、元数据永久保留）
- 列表页不查正文，只查元数据
- 详情页拆成两步：
  - `GET /api/v1/request-logs/{id}` 读取元数据
  - `GET /api/v1/request-logs/{id}/payloads` 按需读取正文
- 日志写入为单队列、批量 flush、异步落库
- 日志记录包含请求 IP 和协议类型

这意味着：

- 详情接口性能已经明显好于直接整行读取 `longtext`
- 但在高流量场景下，`request_logs` 仍然会快速膨胀
- 如果你要承载长期、全量、正文级留存，当前数据库模型不是终态设计

生产建议：

- 缩短在线热日志保留周期
- 将全文正文转移到对象存储或专用日志存储
- 保留 DB 里的索引字段、统计字段和检索摘要

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5.9 + WebFlux |
| Java | 17 |
| 构建 | Maven 3.9+ / `./mvnw` |
| 数据库 | MySQL 8.0 / SQLite 3.45.1.0 |
| ORM | MyBatis Plus 3.5.15 |
| 缓存 | Redis 6+ |
| 认证 | JWT (JJWT) + Spring Security |
| HTTP Client | OkHttp 4.12.0 |
| Token 计数 | JTokkit 1.1.0 |
| 监控 | Actuator + Micrometer Prometheus |
| 前端框架 | React 19 + TypeScript 5.2 |
| 构建工具 | Vite 5.0 |
| 样式 | Tailwind CSS 3.3 |
| 图表 | Recharts 3.6 |
| 图标 | Lucide React |
| 包管理 | pnpm |
| 容器 | Docker (Alpine) + Docker Compose |

## 项目结构

```text
lumina/
├── src/main/java/com/lumina/
│   ├── config/                # 启动、数据源、安全、属性、WebClient 等配置
│   ├── controller/            # Relay、Dashboard、Circuit Breaker、Logs 等接口
│   ├── service/               # 业务服务与核心调度逻辑
│   ├── converter/             # 协议互转框架（OpenAI/Anthropic/Responses）
│   ├── state/                 # Provider 运行态、熔断状态机与恢复逻辑
│   ├── stats/                 # 统计聚合引擎（Accumulator、Redis Reader、Rebuild）
│   ├── logging/               # 请求日志上下文与异步写入链路
│   ├── metrics/               # Micrometer 指标注册
│   └── mapper/                # MyBatis Mapper
├── src/main/resources/
│   ├── application*.yaml      # 主配置与环境配置
│   ├── db/migration/          # MySQL / SQLite 迁移脚本（V001 - V007）
│   └── mapper/                # XML 查询
├── lumina-web/
│   ├── components/            # Dashboard、Logs、Providers、Settings 等界面
│   ├── services/              # 前端 API 封装
│   ├── constants.ts           # 前端元信息
│   └── metadata.json          # 前端版本信息
├── Dockerfile
├── docker-compose.yml
├── docker-compose-mysql.yml
└── startup.sh
```

## 已知限制

- `request_logs` 全量正文仍在数据库内，长期容量压力明显
- 主数据访问层仍是阻塞式 MyBatis；虽然热路径已避开 event loop，但还不是全链路 reactive
- Redis 当前以内嵌进程方式跑在应用容器里，适合单机部署，不适合严格隔离的生产架构
- 高等级企业级日志归档、冷热分层、全文检索链路尚未内建

## 许可证

本项目采用 [AGPL-3.0](LICENSE) 开源协议。
