-- Migration: Widen indicator_type column to accommodate new indicator enum values
-- The existing column was created with a narrow width by Hibernate auto-DDL.
-- ddl-auto=update does NOT widen existing columns in MySQL.
-- Run this manually: mysql -u root -p stockmarket < V1__widen_indicator_type_column.sql

ALTER TABLE technical_indicators MODIFY COLUMN indicator_type VARCHAR(50) NOT NULL;
