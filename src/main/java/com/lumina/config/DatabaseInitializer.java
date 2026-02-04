package com.lumina.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;

/**
 * 数据库初始化器
 * 自动初始化 SQLite 数据库
 *
 * @author Lumina
 */
@Slf4j
@Component
@Order(1) // 确保在其他 CommandLineRunner 之前执行
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (DataSourceConfig.isSQLite()) {
            log.info("Detected SQLite database, checking initialization...");
            initializeSQLiteDatabase();
            runMigrations();
        } else {
            log.info("Using MySQL database, skipping auto-initialization");
        }
    }

    /**
     * 初始化 SQLite 数据库
     */
    private void initializeSQLiteDatabase() {
        try {
            // 检查数据库文件是否存在
            String jdbcUrl;
            try (Connection conn = dataSource.getConnection()) {
                jdbcUrl = conn.getMetaData().getURL();
            }
            String dbPath = jdbcUrl.replace("jdbc:sqlite:", "");

            File dbFile = new File(dbPath);
            boolean isNewDatabase = !dbFile.exists();

            if (isNewDatabase) {
                log.info("SQLite database file not found, creating new database at: {}", dbPath);

                // 确保父目录存在
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                    log.info("Created database directory: {}", parentDir.getAbsolutePath());
                }
            }

            // 检查是否需要初始化表结构
            boolean needsInitialization = checkIfNeedsInitialization();

            if (needsInitialization) {
                log.info("Initializing SQLite database schema...");
                executeSQLiteInitScript();
                log.info("SQLite database initialized successfully!");
            } else {
                log.info("SQLite database already initialized, skipping schema creation");
            }

        } catch (Exception e) {
            log.error("Failed to initialize SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * 检查是否需要初始化数据库
     */
    private boolean checkIfNeedsInitialization() {
        try {
            // 尝试查询 users 表，如果表不存在会抛出异常
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            return false;
        } catch (Exception e) {
            // 表不存在，需要初始化
            return true;
        }
    }

    /**
     * 执行 SQLite 初始化脚本
     */
    private void executeSQLiteInitScript() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/lumina_sqlite.sql");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // 跳过注释和空行
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("/*")) {
                    continue;
                }

                sqlBuilder.append(line).append(" ");

                // 如果遇到分号，执行 SQL 语句
                if (line.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    if (!sql.isEmpty()) {
                        try {
                            jdbcTemplate.execute(sql);
                        } catch (Exception e) {
                            log.warn("Failed to execute SQL: {}", sql.substring(0, Math.min(100, sql.length())));
                            log.warn("Error: {}", e.getMessage());
                        }
                    }
                    sqlBuilder = new StringBuilder();
                }
            }

            // 执行剩余的 SQL
            String remainingSql = sqlBuilder.toString().trim();
            if (!remainingSql.isEmpty()) {
                jdbcTemplate.execute(remainingSql);
            }
        }
    }

    /**
     * 运行数据库迁移脚本
     */
    private void runMigrations() {
        try {
            // 确保 migration_records 表存在
            ensureMigrationTableExists();

            // 获取所有迁移脚本并按版本号排序
            org.springframework.core.io.Resource[] resources =
                new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath:db/migration/V*.sql");

            for (org.springframework.core.io.Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                // 从文件名提取版本号 (V001__xxx.sql -> 1)
                String versionStr = filename.substring(1, filename.indexOf("__"));
                int version = Integer.parseInt(versionStr);

                // 检查该版本是否已执行
                if (isMigrationExecuted(version)) {
                    log.debug("Migration V{} already executed, skipping", String.format("%03d", version));
                    continue;
                }

                log.info("Executing migration V{}: {}", String.format("%03d", version), filename);
                executeMigrationScript(resource);
                recordMigration(version);
                log.info("Migration V{} completed successfully", String.format("%03d", version));
            }

        } catch (Exception e) {
            log.error("Failed to run migrations", e);
            // 不抛出异常，避免影响应用启动
        }
    }

    /**
     * 确保迁移记录表存在
     */
    private void ensureMigrationTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM migration_records", Integer.class);
        } catch (Exception e) {
            // 表不存在，创建它
            log.info("Creating migration_records table");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS migration_records (" +
                "  version INTEGER PRIMARY KEY," +
                "  status INTEGER NOT NULL," +
                "  executed_at DATETIME NOT NULL DEFAULT (datetime('now'))" +
                ")"
            );
        }
    }

    /**
     * 检查迁移是否已执行
     */
    private boolean isMigrationExecuted(int version) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_records WHERE version = ? AND status = 1",
                Integer.class,
                version
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 记录迁移执行
     */
    private void recordMigration(int version) {
        jdbcTemplate.update(
            "INSERT INTO migration_records (version, status, executed_at) VALUES (?, 1, datetime('now'))",
            version
        );
    }

    /**
     * 执行迁移脚本
     */
    private void executeMigrationScript(org.springframework.core.io.Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // 跳过注释和空行
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("/*")) {
                    continue;
                }

                sqlBuilder.append(line).append(" ");

                // 如果遇到分号，执行 SQL 语句
                if (line.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    if (!sql.isEmpty()) {
                        try {
                            jdbcTemplate.execute(sql);
                            log.debug("Executed: {}", sql.substring(0, Math.min(100, sql.length())));
                        } catch (Exception e) {
                            log.error("Failed to execute SQL: {}", sql);
                            throw e;
                        }
                    }
                    sqlBuilder = new StringBuilder();
                }
            }

            // 执行剩余的 SQL
            String remainingSql = sqlBuilder.toString().trim();
            if (!remainingSql.isEmpty()) {
                jdbcTemplate.execute(remainingSql);
            }
        }
    }
}
