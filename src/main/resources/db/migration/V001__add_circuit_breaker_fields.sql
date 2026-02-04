-- Migration V001: Add circuit breaker fields to provider_runtime_stats
-- Date: 2026-02-04

-- For SQLite
ALTER TABLE provider_runtime_stats ADD COLUMN consecutive_failures INTEGER DEFAULT NULL;
ALTER TABLE provider_runtime_stats ADD COLUMN open_attempt INTEGER DEFAULT NULL;
ALTER TABLE provider_runtime_stats ADD COLUMN next_probe_at INTEGER DEFAULT NULL;
