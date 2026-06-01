-- V009: 供应商多协议类型支持
-- 1. providers.type 改为 varchar 支持多值
-- 2. 新增 provider_endpoints 表
-- 3. model_group_items 增加 protocol_type 列

-- MySQL
ALTER TABLE `providers` MODIFY COLUMN `type` varchar(50) NOT NULL DEFAULT '0';

CREATE TABLE IF NOT EXISTS `provider_endpoints` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `provider_id` bigint unsigned NOT NULL,
    `protocol_type` tinyint NOT NULL,
    `base_url` varchar(500) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_type` (`provider_id`, `protocol_type`),
    CONSTRAINT `fk_endpoints_provider` FOREIGN KEY (`provider_id`) REFERENCES `providers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `model_group_items` ADD COLUMN `protocol_type` tinyint DEFAULT NULL;

-- 迁移已有数据：为每个现有 provider 创建一条 endpoint 记录
INSERT INTO `provider_endpoints` (`provider_id`, `protocol_type`, `base_url`)
    SELECT `id`, `type`, `base_url` FROM `providers`
    WHERE `type` IS NOT NULL;
