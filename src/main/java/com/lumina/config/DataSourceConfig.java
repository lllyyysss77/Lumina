package com.lumina.config;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据源配置类
 * 支持 MySQL 和 SQLite 数据库类型检测
 */
@Component
public class DataSourceConfig {

    private static String datasourceUrl;

    @Value("${spring.datasource.url}")
    public void setDatasourceUrl(String url) {
        DataSourceConfig.datasourceUrl = url;
    }

    /**
     * 获取当前数据库的 MyBatis Plus DbType
     */
    public static DbType getMybatisPlusDbType() {
        if (datasourceUrl != null && datasourceUrl.contains("sqlite")) {
            return DbType.SQLITE;
        }
        return DbType.MYSQL;
    }

    /**
     * 判断是否为 SQLite 数据库
     */
    public static boolean isSQLite() {
        return datasourceUrl != null && datasourceUrl.contains("sqlite");
    }
}
