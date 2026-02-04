# Lumina 数据库配置指南

Lumina 现在支持两种数据库：**MySQL** 和 **SQLite**，可以根据部署需求灵活选择。

## 数据库选择

### MySQL（推荐用于生产环境）
- ✅ 高性能、支持高并发
- ✅ 适合多用户、大数据量场景
- ❌ 需要额外部署 MySQL 服务

### SQLite（推荐用于开发/测试/小型部署）
- ✅ 零依赖、开箱即用
- ✅ 单文件数据库、易于备份
- ✅ 适合 Docker 单容器部署
- ❌ 不支持高并发写入

---

## 快速开始

### 方式 1：使用 SQLite（零依赖）

#### 本地运行
```bash
# 使用环境变量指定 SQLite
export SPRING_DATASOURCE_URL=jdbc:sqlite:./data/lumina.db
export SPRING_DATASOURCE_DRIVER=org.sqlite.JDBC

# 或者使用 Spring Profile
mvn spring-boot:run -Dspring-boot.run.profiles=sqlite
```

#### Docker 部署（推荐）
```bash
# 使用 SQLite 配置启动
docker-compose -f docker-compose.yml up -d

# 数据会持久化到 Docker volume: lumina-sqlite-data
```

### 方式 2：使用 MySQL

#### 本地运行
```bash
# 1. 启动 MySQL 并创建数据库
mysql -u root -p
CREATE DATABASE lumina CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. 导入初始化脚本
mysql -u root -p lumina < src/main/resources/db/migration/lumina.sql

# 3. 配置环境变量
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/lumina?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password

# 4. 启动应用
mvn spring-boot:run
```

#### Docker 部署
```bash
# 使用 MySQL 配置启动（包含 MySQL 容器）
docker-compose up -d
```

---

## 配置详解

### 环境变量配置

| 环境变量 | 说明 | MySQL 示例 | SQLite 示例 |
|---------|------|-----------|------------|
| `SPRING_DATASOURCE_DRIVER` | 数据库驱动 | `com.mysql.cj.jdbc.Driver` | `org.sqlite.JDBC` |
| `SPRING_DATASOURCE_URL` | 数据库连接 URL | `jdbc:mysql://localhost:3306/lumina` | `jdbc:sqlite:./data/lumina.db` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `root` | （留空） |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | `your_password` | （留空） |

### Spring Profile 配置

在 `application.yaml` 中修改：
```yaml
spring:
  profiles:
    active: sqlite  # 或 mysql
```

或使用命令行参数：
```bash
java -jar lumina.jar --spring.profiles.active=sqlite
```

---

## 数据库切换

### 从 MySQL 切换到 SQLite

1. **导出 MySQL 数据**（可选）
   ```bash
   mysqldump -u root -p lumina > backup.sql
   ```

2. **修改配置**
   ```bash
   # 方式 1：修改环境变量
   export SPRING_DATASOURCE_URL=jdbc:sqlite:./data/lumina.db
   export SPRING_DATASOURCE_DRIVER=org.sqlite.JDBC

   # 方式 2：修改 application.yaml
   spring:
     profiles:
       active: sqlite
   ```

3. **重启应用**
   - SQLite 数据库会自动初始化
   - 默认管理员账号：`admin/admin123`

### 从 SQLite 切换到 MySQL

1. **准备 MySQL 数据库**
   ```bash
   mysql -u root -p
   CREATE DATABASE lumina CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE lumina;
   SOURCE src/main/resources/db/migration/lumina.sql;
   ```

2. **修改配置**
   ```bash
   export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/lumina
   export SPRING_DATASOURCE_DRIVER=com.mysql.cj.jdbc.Driver
   export SPRING_DATASOURCE_USERNAME=root
   export SPRING_DATASOURCE_PASSWORD=your_password
   ```

3. **重启应用**

---

## Docker 部署详解

### SQLite 模式（零依赖）

**docker-compose.yml**
```yaml
services:
  app:
    environment:
      SPRING_DATASOURCE_URL: jdbc:sqlite:/app/data/lumina.db
      SPRING_DATASOURCE_DRIVER: org.sqlite.JDBC
    volumes:
      - ./data:/app/data
```

**特点：**
- 不需要 MySQL 容器
- 数据库文件存储在 `/app/data/lumina.db`
- 通过 Docker volume 持久化数据

**启动命令：**
```bash
docker-compose -f docker-compose.yml up -d
```

### MySQL 模式（完整部署）

**docker-compose-mysql.yml**
```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: lumina
      MYSQL_DATABASE: lumina

  app:
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/lumina
```

**启动命令：**
```bash
docker-compose -f docker-compose-mysql.yml up -d
```

---

## 数据备份与恢复

### SQLite 备份
```bash
# 备份（直接复制数据库文件）
cp ./data/lumina.db ./backup/lumina_$(date +%Y%m%d).db

# 或使用 Docker volume
docker run --rm -v lumina-sqlite-data:/data -v $(pwd):/backup alpine \
  cp /data/lumina.db /backup/lumina_backup.db

# 恢复
cp ./backup/lumina_backup.db ./data/lumina.db
```

### MySQL 备份
```bash
# 备份
mysqldump -u root -p lumina > lumina_backup.sql

# 或使用 Docker
docker exec lumina-mysql mysqldump -u root -plumina lumina > lumina_backup.sql

# 恢复
mysql -u root -p lumina < lumina_backup.sql
```

---

## 常见问题

### Q1: SQLite 数据库文件在哪里？
**A:** 默认位置是 `./data/lumina.db`，可以通过环境变量 `SPRING_DATASOURCE_URL` 修改。

### Q2: SQLite 支持多少并发？
**A:** SQLite 适合读多写少的场景，单个写操作会锁定整个数据库。如果需要高并发写入，建议使用 MySQL。

### Q3: 如何查看当前使用的数据库？
**A:** 查看应用启动日志：
```
INFO  c.l.config.DataSourceConfig - Using MySQL database
# 或
INFO  c.l.config.DataSourceConfig - Detected SQLite database
```

### Q4: 数据库初始化失败怎么办？
**A:**
- **SQLite**: 删除 `./data/lumina.db` 文件，重启应用自动重建
- **MySQL**: 重新执行初始化脚本 `lumina.sql`

### Q5: 可以在运行时切换数据库吗？
**A:** 不可以。需要修改配置并重启应用。

---

## 性能建议

### SQLite 优化
- 定期执行 `VACUUM` 清理数据库
- 避免大量并发写入操作
- 考虑使用 WAL 模式（Write-Ahead Logging）

### MySQL 优化
- 调整连接池大小（`maximum-pool-size`）
- 启用查询缓存
- 定期优化表结构

---

## 技术实现

### 自动数据库检测
应用启动时会自动检测 `SPRING_DATASOURCE_URL`：
- 包含 `sqlite` → 使用 SQLite 配置
- 其他 → 使用 MySQL 配置

### 自动初始化
- **SQLite**: 首次启动时自动创建表结构（`DatabaseInitializer`）
- **MySQL**: 需要手动执行 SQL 脚本

### 兼容性
- MyBatis Plus 自动适配不同数据库的分页语法
- 使用标准 SQL 语法确保跨数据库兼容

---

## 相关文件

- `src/main/resources/db/migration/lumina.sql` - MySQL 初始化脚本
- `src/main/resources/db/migration/lumina_sqlite.sql` - SQLite 初始化脚本
- `src/main/java/com/lumina/config/DataSourceConfig.java` - 数据源配置
- `src/main/java/com/lumina/config/DatabaseInitializer.java` - SQLite 自动初始化
- `docker-compose.yml` - SQLite 部署配置（零依赖）
- `docker-compose-mysql.yml` - MySQL 部署配置
