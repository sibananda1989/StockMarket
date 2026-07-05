-- Migration: Add composite index on signal_record for signal history queries
-- Improves performance of getSignalHistory by indexing (stock_id, recorded_at)
-- Run this manually: mysql -u root -p stockmarket < V3__add_signal_record_composite_index.sql

ALTER TABLE signal_records ADD INDEX idx_stock_recorded (stock_id, recorded_at);
