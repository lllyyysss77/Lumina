# Lumina

Lumina 是一个高性能、轻量级的 LLM API 网关服务，旨在为多个 AI 模型提供商提供统一、安全且具备故障转移能力的接口。

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen.svg)

## 🚀 功能特性


- **统一 API 中继**：支持 OpenAI、Anthropic、Gemini 的标准 API 格式
  - OpenAI Chat Completions (`/v1/chat/completions`)
  - OpenAI Responses API (`/v1/responses`)
  - Anthropic Messages API (`/v1/messages`)
  - Gemini Models API (`/v1beta/models/*`)
  - 支持流式（SSE）和非流式响应

- **模型分组与路由**：
  - 支持模型名称精确匹配，自动路由到指定分组
  - 智能负载均衡：基于 Provider 评分的 Top-K Softmax 加权选择
  - 分组内可配置多个 Provider 作为备份

- **熔断器机制**：
  - 基于错误率、连续失败次数、慢调用率的多维度触发
  - 自动熔断状态管理：CLOSED（正常）→ OPEN（熔断）→ HALF_OPEN（探测）
  - 指数退避恢复策略，支持自动探测和恢复
  - 并发控制（Bulkhead），每个 Provider 可配置最大并发数

- **智能故障转移**：
  - 基于 Provider 评分的 Top-K Softmax 加权选择
  - 支持连接超时、HTTP 错误、限流等场景的自动重试
  - 可配置最大重试次数

- **可观测性**：
  - 实时仪表盘：请求总量、费用统计、平均延迟、成功率
  - 24 小时流量趋势图
  - 模型 Token 使用统计
  - 供应商排名（按调用次数、费用、延迟、成功率）
  - 完整请求日志记录（含 Token 计数、费用、延迟、错误信息）
  - 熔断器状态监控 API

- **认证与鉴权**：
  - 管理后台：JWT 认证（有效期 24 小时）
  - API 调用：API Key 认证（支持启用/禁用控制）

## 🛠️ 技术栈

- **后端**：Spring Boot 3.5.9, Spring WebFlux, Spring Security
- **数据访问**：MyBatis Plus, HikariCP
- **数据库**：MySQL 8.0 / SQLite (自动适配)
- **缓存**：Redis (用于状态记录和频率限制)
- **前端**：React 18, Vite, TypeScript, Pnpm
- **工具**：OkHttp 4.12, JJWT, Lombok

## 📦 快速开始

### 方式一：Docker 部署（推荐）

Lumina 的 Docker 镜像已内置 Redis，您只需关心数据库配置。

#### 1. 使用 SQLite (零配置启动)
```bash
docker compose up -d
```
*数据将保存在容器映射的 `./data` 目录中。*

#### 2. 使用 MySQL (生产推荐)
```bash
docker compose -f docker-compose-mysql.yml up -d
```

**默认凭据**：
- **管理后台**：`http://localhost:8080`
- **用户名**：`admin`
- **密码**：`admin123`

---

### 方式二：本地开发部署

#### 环境要求
- JDK 17+
- Maven 3.6+
- Redis 6.0+ (本地需运行 Redis 服务)
- MySQL 8.0+ 或 SQLite

#### 1. 后端启动
```bash
# 克隆项目
git clone <repository-url>
cd lumina

# 编译并运行 (默认使用 SQLite)
mvn clean package -DskipTests
java -jar target/lumina-0.0.1-SNAPSHOT.jar
```

#### 2. 前端启动
```bash
cd lumina-web
pnpm install
pnpm dev
```

## 🔌 API 使用指南

### 兼容性端点

| 原始 API | Lumina 端点 | 说明 |
|----------|------------|------|
| Anthropic | `POST /v1/messages` | 支持流式与非流式 |
| OpenAI | `POST /v1/chat/completions` | 支持流式与非流式 |
| OpenAI | `POST /v1/responses` | 支持新版 Realtime/Response API 格式 |
| Gemini | `POST /v1beta/models/*` | 完美匹配 Google API 路径 |
| 通用 | `GET /v1/models` | 列出所有可用的模型分组 |

### 调用示例
使用 Lumina 生成的 API Key 调用：
```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer LMN_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'
```

## 📂 项目结构

```text
lumina/
├── src/main/java/com/lumina/
│   ├── config/          # 系统配置（安全、数据源、初始化）
│   ├── controller/      # API 控制器（包括 Relay 转发核心）
│   ├── service/impl/    # 核心逻辑（各提供商的 Executor 实现）
│   ├── state/           # 评分系统与断路器逻辑
│   └── filter/          # 认证过滤器
├── lumina-web/          # 前端项目
│   ├── src/components/  # 业务组件与页面视图
│   ├── src/services/    # API 请求封装
│   └── src/utils/       # 通用工具
└── target/              # 编译输出
```

## 📅 路线图

- [x] 支持流式响应代理
- [ ] 支持多负载均衡模式
- [ ] 支持供应商多KEY管理
- [ ] 完善请求缓存机制
- [ ] 细粒度的速率限制 (Rate Limiting)
- [ ] 支持更多提供商 (Cohere, DeepSeek, Llama.cpp)

## 📄 许可证
本项目采用 [AGPL-3.0 license](LICENSE) 开源。