package com.lumina.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MyBatis Plus 配置类
 *
 * @author Lumina
 */
@Configuration
@MapperScan("com.lumina.mapper")
public class MybatisPlusConfig {

    /**
     * MyBatis Plus 拦截器配置
     * 添加分页插件，自动检测数据库类型
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 根据当前数据源动态选择数据库类型
        DbType dbType = DataSourceConfig.getMybatisPlusDbType();
        // 添加分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }

    /**
     * 配置 DatabaseIdProvider，用于多数据库支持
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("SQLite", "sqlite");
        provider.setProperties(properties);
        return provider;
    }
}
