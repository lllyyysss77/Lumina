-- Migration V006: Add API key spending quota
ALTER TABLE `api_keys` ADD COLUMN `max_amount` decimal(10,4) DEFAULT NULL COMMENT '最大消费额度，NULL表示无限制';
