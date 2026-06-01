-- 添加 prompt cache token 字段
ALTER TABLE request_logs ADD COLUMN cache_read_tokens INTEGER DEFAULT NULL;
ALTER TABLE request_logs ADD COLUMN cache_creation_tokens INTEGER DEFAULT NULL;

ALTER TABLE stats_hourly ADD COLUMN total_cache_read_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stats_hourly ADD COLUMN total_cache_creation_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stats_hourly ADD COLUMN cache_hit_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stats_daily ADD COLUMN total_cache_read_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stats_daily ADD COLUMN total_cache_creation_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE stats_daily ADD COLUMN cache_hit_count INTEGER NOT NULL DEFAULT 0;
