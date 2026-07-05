-- Migration: Widen value column to prevent overflow for unbounded indicators like OBV
-- The existing DECIMAL(10,4) column (max 99,999.9999) is too narrow for OBV which
-- accumulates daily volume into values that easily reach millions or billions.
-- ddl-auto=update does NOT widen existing columns in MySQL.
-- Run this manually: mysql -u root -p stockmarket < V2__widen_indicator_value_column.sql

ALTER TABLE technical_indicators MODIFY COLUMN value DECIMAL(18,0) NOT NULL;