/*
 Lumina MySQL Database Schema (Complete)
 Version: 0.4.0
 Includes all migrations through V005

 Usage:
   mysql -u root -p lumina < lumina.sql
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt加密）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- Table structure for settings
-- ----------------------------
DROP TABLE IF EXISTS `settings`;
CREATE TABLE `settings` (
  `setting_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置键名',
  `setting_value` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置值',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置表';

-- ----------------------------
-- Table structure for api_keys
-- ----------------------------
DROP TABLE IF EXISTS `api_keys`;
CREATE TABLE `api_keys` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'API密钥ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密钥名称',
  `api_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API密钥值',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：0-禁用，1-启用',
  `expired_at` bigint DEFAULT NULL COMMENT '过期时间戳（秒），NULL表示永不过期',
  `max_amount` decimal(10,4) DEFAULT NULL COMMENT '最大消费额度，NULL表示无限制',
  `supported_models` text COLLATE utf8mb4_unicode_ci COMMENT '支持的模型列表（逗号分隔），NULL表示无限制',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API密钥表';

-- ----------------------------
-- Table structure for providers
-- ----------------------------
DROP TABLE IF EXISTS `providers`;
CREATE TABLE `providers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '供应商名称',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '供应商类型：0-OpenAI Chat, 1-OpenAI Response, 2-Anthropic, 3-Gemini, 4-new api',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Base URL',
  `api_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'API请求密钥',
  `model_name` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `actual_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际模型名',
  `beta` tinyint(1) DEFAULT '0' COMMENT '是否使用beta功能',
  `auto_sync` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否自动同步：0-否，1-是',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_type` (`type`),
  KEY `idx_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';

-- ----------------------------
-- Table structure for model_groups
-- ----------------------------
DROP TABLE IF EXISTS `model_groups`;
CREATE TABLE `model_groups` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称（对外暴露的模型名）',
  `balance_mode` tinyint NOT NULL COMMENT '负载均衡模式：1-轮询，2-随机，3-故障转移，4-加权',
  `match_regex` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '匹配正则表达式',
  `first_token_timeout` int DEFAULT '45000' COMMENT '首个Token超时时间（毫秒）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组表';

-- ----------------------------
-- Table structure for model_group_items
-- ----------------------------
DROP TABLE IF EXISTS `model_group_items`;
CREATE TABLE `model_group_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `group_id` bigint unsigned NOT NULL COMMENT '分组ID',
  `provider_id` bigint unsigned NOT NULL COMMENT '供应商ID',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级（数字越大优先级越高）',
  `weight` int NOT NULL DEFAULT '1' COMMENT '权重（用于加权负载均衡）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_provider_model` (`group_id`,`provider_id`,`model_name`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_provider_id` (`provider_id`),
  CONSTRAINT `fk_group_items_group` FOREIGN KEY (`group_id`) REFERENCES `model_groups` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_group_items_provider` FOREIGN KEY (`provider_id`) REFERENCES `providers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组项目表';

-- ----------------------------
-- Table structure for llm_models (includes V004, V005 migrations)
-- ----------------------------
DROP TABLE IF EXISTS `llm_models`;
CREATE TABLE `llm_models` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '模型记录ID',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `provider` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '供应商/家族',
  `input_price` decimal(10,2) DEFAULT '0.00' COMMENT '输入价格（每百万Token）',
  `output_price` decimal(10,2) DEFAULT '0.00' COMMENT '输出价格（每百万Token）',
  `context_limit` int DEFAULT '0' COMMENT '上下文限制',
  `output_limit` int DEFAULT '0' COMMENT '输出限制',
  `cache_read_price` decimal(10,2) DEFAULT NULL COMMENT '缓存读取价格',
  `cache_write_price` decimal(10,2) DEFAULT NULL COMMENT '缓存写入价格',
  `is_reasoning` tinyint DEFAULT NULL COMMENT '推理',
  `is_tool_call` tinyint DEFAULT NULL COMMENT '工具调用',
  `is_attachment` tinyint DEFAULT NULL COMMENT '附件支持',
  `is_structured_output` tinyint DEFAULT NULL COMMENT '结构化输出支持',
  `is_temperature` tinyint DEFAULT NULL COMMENT '温度参数支持',
  `is_open_weights` tinyint DEFAULT NULL COMMENT '开源权重',
  `input_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输入类型支持',
  `output_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '输出类型支持',
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型显示名称',
  `family` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型系列',
  `knowledge_cutoff` varchar(25) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识截止日期',
  `release_date` varchar(25) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布日期',
  `input_limit` int DEFAULT NULL COMMENT '输入限制',
  `last_updated_at` varchar(25) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型最后更新时间',
  `is_active` tinyint NOT NULL DEFAULT '0' COMMENT '是否为计费使用的记录：0-否，1-是',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_provider` (`model_name`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM模型信息表';

-- ----------------------------
-- Table structure for provider_runtime_stats
-- ----------------------------
DROP TABLE IF EXISTS `provider_runtime_stats`;
CREATE TABLE `provider_runtime_stats` (
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Provider唯一标识',
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `success_rate_ema` double NOT NULL DEFAULT '1' COMMENT '成功率EMA',
  `latency_ema_ms` double NOT NULL DEFAULT '0' COMMENT '延迟EMA',
  `score` double NOT NULL DEFAULT '100' COMMENT '当前评分',
  `total_requests` int NOT NULL DEFAULT '0' COMMENT '总请求数',
  `success_requests` int NOT NULL DEFAULT '0' COMMENT '成功请求数',
  `failure_requests` int NOT NULL DEFAULT '0' COMMENT '失败请求数',
  `circuit_state` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLOSED' COMMENT '熔断状态',
  `circuit_opened_at` bigint NOT NULL DEFAULT '0' COMMENT '熔断开启时间戳',
  `consecutive_failures` int DEFAULT NULL COMMENT '连续失败次数',
  `open_attempt` int DEFAULT NULL COMMENT '熔断开启尝试次数',
  `next_probe_at` bigint DEFAULT NULL COMMENT '下次探测时间戳',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`provider_id`),
  KEY `idx_score` (`score`),
  KEY `idx_circuit_state` (`circuit_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider运行态评分与熔断状态';

-- ----------------------------
-- Table structure for request_logs
-- ----------------------------
DROP TABLE IF EXISTS `request_logs`;
CREATE TABLE `request_logs` (
  `id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志ID（Snowflake ID）',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '请求唯一ID',
  `request_time` bigint DEFAULT NULL COMMENT '请求时间戳（秒）',
  `request_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'chat_completions' COMMENT '请求类型',
  `request_model_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求的模型名称',
  `provider_id` bigint unsigned DEFAULT NULL COMMENT '实际使用的渠道ID',
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `actual_model_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际使用的模型名称',
  `is_stream` tinyint(1) DEFAULT '0' COMMENT '是否流式请求',
  `input_tokens` int DEFAULT '0' COMMENT '输入Token数',
  `output_tokens` int DEFAULT '0' COMMENT '输出Token数',
  `first_token_time` int DEFAULT '0' COMMENT '首字时间（毫秒）',
  `first_token_ms` int DEFAULT '0' COMMENT '首token延迟(毫秒)',
  `total_time` int DEFAULT '0' COMMENT '总用时（毫秒）',
  `total_time_ms` int DEFAULT '0' COMMENT '总耗时(毫秒)',
  `cost` decimal(10,4) DEFAULT '0.0000' COMMENT '消耗费用',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAIL',
  `request_content` longtext COLLATE utf8mb4_unicode_ci COMMENT '请求内容（JSON）',
  `response_content` longtext COLLATE utf8mb4_unicode_ci COMMENT '响应内容（JSON）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `error_stage` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CONNECT / HTTP / DECODE / TIMEOUT',
  `retry_count` int DEFAULT '0' COMMENT '故障转移次数',
  `api_key` varchar(255) DEFAULT NULL COMMENT '客户端API密钥',
  `request_ip` varchar(64) DEFAULT NULL COMMENT '请求客户端IP',
  `protocol_conversion` varchar(64) DEFAULT NULL COMMENT '协议转换路径，如 OPENAI_RESPONSES→ANTHROPIC',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_request_time` (`request_time`),
  KEY `idx_provider_id` (`provider_id`),
  KEY `idx_request_model` (`request_model_name`),
  KEY `idx_request_id` (`request_id`),
  KEY `idx_api_key` (`api_key`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请求日志表';

-- PLACEHOLDER_STATS_TABLES

-- ----------------------------
-- Table structure for stats_daily (V002 migration)
-- ----------------------------
DROP TABLE IF EXISTS `stats_daily`;
CREATE TABLE `stats_daily` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `stat_date` date NOT NULL COMMENT '统计日期',
  `provider_id` bigint unsigned DEFAULT NULL COMMENT '供应商ID，NULL表示全局汇总',
  `provider_name` varchar(100) DEFAULT NULL COMMENT '供应商名称',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型名称，NULL表示供应商级汇总',
  `total_requests` bigint NOT NULL DEFAULT 0 COMMENT '总请求数',
  `success_count` bigint NOT NULL DEFAULT 0 COMMENT '成功请求数',
  `total_input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '总输入Token数',
  `total_output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '总输出Token数',
  `total_cost` decimal(14,4) NOT NULL DEFAULT 0 COMMENT '总费用',
  `total_latency_ms` bigint NOT NULL DEFAULT 0 COMMENT '总延迟毫秒数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_date_provider_model` (`stat_date`, `provider_id`, `model_name`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按天聚合统计表';

-- ----------------------------
-- Table structure for stats_hourly (V002 migration)
-- ----------------------------
DROP TABLE IF EXISTS `stats_hourly`;
CREATE TABLE `stats_hourly` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `stat_hour` datetime NOT NULL COMMENT '统计小时',
  `provider_id` bigint unsigned DEFAULT NULL COMMENT '供应商ID，NULL表示全局汇总',
  `provider_name` varchar(100) DEFAULT NULL COMMENT '供应商名称',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型名称，NULL表示供应商级汇总',
  `total_requests` bigint NOT NULL DEFAULT 0 COMMENT '总请求数',
  `success_count` bigint NOT NULL DEFAULT 0 COMMENT '成功请求数',
  `total_input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '总输入Token数',
  `total_output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '总输出Token数',
  `total_cost` decimal(14,4) NOT NULL DEFAULT 0 COMMENT '总费用',
  `total_latency_ms` bigint NOT NULL DEFAULT 0 COMMENT '总延迟毫秒数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_hour_provider_model` (`stat_hour`, `provider_id`, `model_name`),
  KEY `idx_stat_hour` (`stat_hour`),
  KEY `idx_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按小时聚合统计表';

-- ----------------------------
-- Table structure for migration_records
-- ----------------------------
DROP TABLE IF EXISTS `migration_records`;
CREATE TABLE `migration_records` (
  `version` int NOT NULL COMMENT '迁移版本号',
  `status` tinyint NOT NULL COMMENT '迁移状态：1-成功，2-失败',
  `executed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据库迁移记录表';

SET FOREIGN_KEY_CHECKS = 1;
