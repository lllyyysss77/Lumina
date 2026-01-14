-- ============================================
-- 17. Provider 运行态评分与熔断状态表
-- ============================================
CREATE TABLE `provider_runtime_stats` (
  `provider_id` VARCHAR(255) NOT NULL COMMENT 'Provider 唯一标识 (baseUrl_keyHash_model)',
  `provider_name` VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',

  `success_rate_ema` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '成功率 EMA',
  `latency_ema_ms` DOUBLE NOT NULL DEFAULT 0 COMMENT '延迟 EMA',
  `score` DOUBLE NOT NULL DEFAULT 100 COMMENT '当前评分',

  `total_requests` INT NOT NULL DEFAULT 0 COMMENT '总请求数',
  `success_requests` INT NOT NULL DEFAULT 0 COMMENT '成功请求数',
  `failure_requests` INT NOT NULL DEFAULT 0 COMMENT '失败请求数',

  `circuit_state` VARCHAR(20) NOT NULL DEFAULT 'CLOSED' COMMENT '熔断状态',
  `circuit_opened_at` BIGINT NOT NULL DEFAULT 0 COMMENT '熔断开启时间戳',

  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`provider_id`),
  KEY `idx_score` (`score`),
  KEY `idx_circuit_state` (`circuit_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider 运行态评分与熔断状态';
