-- Migration V007: Add request IP and protocol conversion to request logs for SQLite
ALTER TABLE `request_logs` ADD COLUMN `request_ip` varchar(64) DEFAULT NULL;
ALTER TABLE `request_logs` ADD COLUMN `protocol_conversion` varchar(64) DEFAULT NULL;
