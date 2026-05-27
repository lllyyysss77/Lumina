-- Migration V007: Add request IP and protocol conversion to request logs
ALTER TABLE `request_logs` ADD COLUMN `request_ip` varchar(64) DEFAULT NULL COMMENT '请求客户端IP';
ALTER TABLE `request_logs` ADD COLUMN `protocol_conversion` varchar(64) DEFAULT NULL COMMENT '协议转换路径，如 OPENAI_RESPONSES→ANTHROPIC';
