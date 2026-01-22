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
            String jdbcUrl = dataSource.getConnection().getMetaData().getURL();
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
}
