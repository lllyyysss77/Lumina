-- Migration V006: Add API key spending quota for SQLite
ALTER TABLE `api_keys` ADD COLUMN `max_amount` REAL DEFAULT NULL;
