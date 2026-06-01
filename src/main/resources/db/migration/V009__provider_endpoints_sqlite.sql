-- V009 SQLite: 供应商多协议类型支持

-- SQLite 不支持 MODIFY COLUMN，需要通过重建表来修改类型
-- 但 type 列在 SQLite 中本身就是 affinity 类型，varchar 和 tinyint 兼容
-- 所以只需新增表

CREATE TABLE IF NOT EXISTS `provider_endpoints` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
    `provider_id` INTEGER NOT NULL,
    `protocol_type` INTEGER NOT NULL,
    `base_url` TEXT NOT NULL,
    UNIQUE (`provider_id`, `protocol_type`),
    FOREIGN KEY (`provider_id`) REFERENCES `providers` (`id`) ON DELETE CASCADE
);

ALTER TABLE `model_group_items` ADD COLUMN `protocol_type` INTEGER DEFAULT NULL;

-- 迁移已有数据
INSERT INTO `provider_endpoints` (`provider_id`, `protocol_type`, `base_url`)
    SELECT `id`, `type`, `base_url` FROM `providers`
    WHERE `type` IS NOT NULL;
