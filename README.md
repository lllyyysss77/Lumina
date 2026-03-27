# Lumina

Lumina 是一个面向多模型、多供应商场景的 LLM API Gateway。它提供统一中继入口、模型分组路由、供应商转发、失败切换、熔断与隔离保护、请求计费统计，以及一套可直接使用的管理后台。

当前建议版本：`v0.4.0`

许可证：`AGPL-3.0`

## 项目定位

这个项目不是 SDK，也不是单一厂商适配层，而是一个放在业务入口前面的网关：

- 对上游应用暴露统一 API
- 对下游供应商做路由、选择、失败切换和保护
- 对运行态做统计、计费、日志和观测
- 对运维侧提供后台管理、熔断器管控和请求排障能力

## 核心能力

- 统一中继 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Gemini Models API
- 按“对外模型名”做模型分组，将请求路由到一组 Provider
- 在组内基于 SAPR / Top-K Softmax 做加权选择，并接入 weight
- 支持多供应商自动失败切换，限制最大 failover 次数
- 提供熔断器、HALF_OPEN 探测、Bulkhead 并发隔离和运行态恢复
- 维护 Provider 运行态评分、成功率、延迟 EMA、请求计数和状态持久化
- 统计请求数、成功率、成本、模型 Token 使用量、供应商排名
- 记录完整请求日志，并将详情元数据与正文载荷分离查询
- 暴露 Actuator / Prometheus 指标，并在前端仪表盘展示关键观测数据

## 系统架构概览

请求主路径可以概括为：

`API Key 鉴权 -> 模型分组解析 -> 候选 Provider 选择 -> 熔断 / Bulkhead 判定 -> HTTP Relay -> usage / cost 统计 -> 异步日志写入 -> 仪表盘与运行态持久化`

当前代码中已经落地的几个关键优化点：

- 热路径缓存：缓存 group config、API key 校验结果、model price，多数请求可降到 0 次读库
- 阻塞 ORM 移出 event loop：关键 MyBatis 调用通过 `Mono.fromCallable(...).subscribeOn(boundedElastic())` 下沉
- 共享 Relay HTTP Client：连接池、连接超时、响应超时、空闲回收和 pending acquire 已集中配置
- 日志链路有界化：有界队列 + 批量写入 + 异步 flush，避免同步日志写库拖慢主链路
- 启动恢复修正：持久化状态若为 `HALF_OPEN`，启动时会归一化处理，不再直接按脏状态恢复

## 功能清单

### 中继协议

- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/responses`
- `POST /v1/messages`
- `POST /v1beta/models/{modelAction}`

### 控制面

- 供应商管理
- 模型分组与分组项管理
- API Key 管理
- 模型价格管理与同步
- 系统设置管理
- 熔断器状态查看、人工接管、释放接管

### 观测与排障

- 仪表盘概览：总请求数、总 Token 数、费用、平均延迟、成功率
- 24 小时请求流量趋势
- 模型 Token 使用排行
- 供应商统计排行
- Provider 运行态列表：分页、按供应商 / 模型 / 状态筛选、按总请求数倒序
- 观测面板：缓存命中、failover、熔断、bulkhead rejection、日志队列等指标
- 请求日志列表与详情

## 快速开始

### 方式一：Docker Compose

#### SQLite 零依赖启动

```bash
docker compose up -d
```

默认会：

- 构建 `lumina:0.4.0`
- 将 SQLite 数据库存到宿主机挂载目录
- 在应用容器内通过 `startup.sh` 启动本地 Redis

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

基础配置位于 [application.yaml](/home/jojo/projects/java/lumina/src/main/resources/application.yaml)。

支持两类后端存储：

- MySQL：默认生产建议
- SQLite：适合单机、测试和轻量部署

相关配置文件：

- [application-mysql.yaml](/home/jojo/projects/java/lumina/src/main/resources/application-mysql.yaml)
- [application-sqlite.yaml](/home/jojo/projects/java/lumina/src/main/resources/application-sqlite.yaml)

### Redis

当前 Redis 主要用于：

- JWT token 存取与注销
- 运行时状态辅助能力

容器部署下，Redis 由 [startup.sh](/home/jojo/projects/java/lumina/startup.sh) 在应用容器内拉起；本地开发请自行准备 Redis 实例。

### Lumina 核心配置

在 [LuminaProperties.java](/home/jojo/projects/java/lumina/src/main/java/com/lumina/config/LuminaProperties.java) 与 [application.yaml](/home/jojo/projects/java/lumina/src/main/resources/application.yaml) 中可见以下关键配置：

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

## 管理后台与可观测性

后台前端位于 [lumina-web](/home/jojo/projects/java/lumina/lumina-web)。

当前界面已覆盖：

- Dashboard 概览卡片
- Provider 排行与模型 Token 使用
- Provider 运行态观测列表
- 熔断器管控面板
- 供应商、分组、价格、设置管理
- 请求日志列表与详情弹窗

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
- `GET /api/v1/circuit-breaker/management/list`
- `GET /api/v1/circuit-breaker/management/recent-events`

## 日志与存储说明

当前请求日志表结构定义见 [lumina.sql](/home/jojo/projects/java/lumina/src/main/resources/db/migration/lumina.sql) 与 [RequestLog.java](/home/jojo/projects/java/lumina/src/main/java/com/lumina/entity/RequestLog.java)。

当前实现特征：

- `request_content`、`response_content` 使用 `longtext`
- 请求正文与响应正文当前为全量保留
- 列表页不查正文，只查元数据
- 详情页拆成两步：
  - `GET /api/v1/request-logs/{id}` 读取元数据
  - `GET /api/v1/request-logs/{id}/payloads` 按需读取正文
- 日志写入为单队列、批量 flush、异步落库

这意味着：

- 详情接口性能已经明显好于直接整行读取 `longtext`
- 但在高流量场景下，`request_logs` 仍然会快速膨胀
- 如果你要承载长期、全量、正文级留存，当前数据库模型不是终态设计

生产建议：

- 缩短在线热日志保留周期
- 将全文正文转移到对象存储或专用日志存储
- 保留 DB 里的索引字段、统计字段和检索摘要

## 项目结构

```text
lumina/
├── src/main/java/com/lumina/
│   ├── config/                # 启动、数据源、安全、属性、WebClient 等配置
│   ├── controller/            # Relay、Dashboard、Circuit Breaker、Logs 等接口
│   ├── service/               # 业务服务与核心调度逻辑
│   ├── state/                 # Provider 运行态、熔断状态机与恢复逻辑
│   ├── logging/               # 请求日志上下文与异步写入链路
│   ├── metrics/               # Micrometer 指标注册
│   └── mapper/                # MyBatis Mapper
├── src/main/resources/
│   ├── application*.yaml      # 主配置与环境配置
│   ├── db/migration/          # MySQL / SQLite 初始化脚本
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

## 版本说明

`v0.4.0` 相比此前 `0.3.0`，重点变化是：

- 完成热路径缓存改造，显著降低 relay 主链路读库频率
- 将关键阻塞数据库调用移出 WebFlux event loop
- 为 relay 引入共享可调优 HTTP client
- 补齐 bulkhead override、生效状态恢复与运行态观测
- 重做请求日志详情读取路径，避免详情接口直接拖整行 `longtext`
- 前端补齐 Provider 运行态分页、筛选、请求计数和总 Token 展示

## 许可证

本项目采用 [AGPL-3.0](LICENSE) 开源协议。
