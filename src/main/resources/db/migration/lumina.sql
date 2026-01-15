/*
 Navicat Premium Dump SQL

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44-0ubuntu0.24.04.2)
 Source Host           : localhost:3306
 Source Schema         : lumina

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44-0ubuntu0.24.04.2)
 File Encoding         : 65001

 Date: 15/01/2026 17:06:03
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
-- Table structure for llm_models
-- ----------------------------
DROP TABLE IF EXISTS `llm_models`;
CREATE TABLE `llm_models` (
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `input_price` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '输入价格（每百万Token）',
  `output_price` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '输出价格（每百万Token）',
  `cache_read_price` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '缓存读取价格（每百万Token）',
  `cache_write_price` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '缓存写入价格（每百万Token）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM模型信息表';

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
) ENGINE=InnoDB AUTO_INCREMENT=122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组项目表';

-- ----------------------------
-- Table structure for model_groups
-- ----------------------------
DROP TABLE IF EXISTS `model_groups`;
CREATE TABLE `model_groups` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称（对外暴露的模型名）',
  `balance_mode` tinyint NOT NULL COMMENT '负载均衡模式：1-轮询，2-随机，3-故障转移，4-加权',
  `match_regex` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '匹配正则表达式',
  `first_token_timeout` int DEFAULT NULL COMMENT '首个Token超时时间（秒）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组表';

-- ----------------------------
-- Table structure for provider_runtime_stats
-- ----------------------------
DROP TABLE IF EXISTS `provider_runtime_stats`;
CREATE TABLE `provider_runtime_stats` (
  `provider_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Provider 唯一标识 (baseUrl_keyHash_model)',
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `success_rate_ema` double NOT NULL DEFAULT '1' COMMENT '成功率 EMA',
  `latency_ema_ms` double NOT NULL DEFAULT '0' COMMENT '延迟 EMA',
  `score` double NOT NULL DEFAULT '100' COMMENT '当前评分',
  `total_requests` int NOT NULL DEFAULT '0' COMMENT '总请求数',
  `success_requests` int NOT NULL DEFAULT '0' COMMENT '成功请求数',
  `failure_requests` int NOT NULL DEFAULT '0' COMMENT '失败请求数',
  `circuit_state` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLOSED' COMMENT '熔断状态',
  `circuit_opened_at` bigint NOT NULL DEFAULT '0' COMMENT '熔断开启时间戳',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`provider_id`),
  KEY `idx_score` (`score`),
  KEY `idx_circuit_state` (`circuit_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider 运行态评分与熔断状态';

-- ----------------------------
-- Table structure for providers
-- ----------------------------
DROP TABLE IF EXISTS `providers`;
CREATE TABLE `providers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '供应商名称',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '供应商类型：0-OpenAI Chat, 1-OpenAI Response, 2-Anthropic, 3-Gemini, 4-Volcengine',
  `base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Base URL，如：https://api.openai.com/v1',
  `api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'api请求密钥',
  `model_name` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
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
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';

-- ----------------------------
-- Table structure for request_logs
-- ----------------------------
DROP TABLE IF EXISTS `request_logs`;
CREATE TABLE `request_logs` (
  `id` bigint unsigned NOT NULL COMMENT '日志ID（Snowflake ID）',
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '请求唯一ID',
  `request_time` bigint DEFAULT NULL COMMENT '请求时间戳（秒）',
  `request_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'chat_completions' COMMENT 'chat_completions / responses / messages',
  `request_model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求的模型名称',
  `provider_id` bigint unsigned DEFAULT NULL COMMENT '实际使用的渠道ID',
  `provider_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `actual_model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际使用的模型名称',
  `is_stream` tinyint(1) DEFAULT '0' COMMENT '是否流式请求',
  `input_tokens` int DEFAULT '0' COMMENT '输入Token数',
  `output_tokens` int DEFAULT '0' COMMENT '输出Token数',
  `first_token_time` int DEFAULT '0' COMMENT '首字时间（毫秒）',
  `first_token_ms` int DEFAULT '0' COMMENT '首token延迟(毫秒)',
  `total_time` int DEFAULT '0' COMMENT '总用时（毫秒）',
  `total_time_ms` int DEFAULT '0' COMMENT '总耗时(毫秒)',
  `cost` decimal(10,4) DEFAULT '0.0000' COMMENT '消耗费用',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAIL',
  `request_content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '请求内容（JSON）',
  `response_content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '响应内容（JSON）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `error_stage` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CONNECT / HTTP / DECODE / TIMEOUT',
  `retry_count` int DEFAULT '0' COMMENT '故障转移次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_request_time` (`request_time`),
  KEY `idx_provider_id` (`provider_id`),
  KEY `idx_request_model` (`request_model_name`),
  KEY `idx_request_id` (`request_id`),
  KEY `idx_provider` (`provider_id`),
  KEY `idx_time` (`request_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请求日志表';

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

SET FOREIGN_KEY_CHECKS = 1;
