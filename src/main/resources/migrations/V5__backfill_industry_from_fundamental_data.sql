-- Backfill industry from fundamental_data for existing stocks
-- Only updates stocks where industry is currently NULL and fundamental_data.industry exists
UPDATE stocks s
JOIN fundamental_data fd ON s.id = fd.stock_id
SET s.industry = fd.industry
WHERE s.industry IS NULL
  AND fd.industry IS NOT NULL
  AND fd.industry != '';
