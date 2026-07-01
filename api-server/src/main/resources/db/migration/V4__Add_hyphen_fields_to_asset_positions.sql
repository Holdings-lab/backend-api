-- 하이픈 잔고조회(0539) itemDetail 필드

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS item_code VARCHAR(20);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS item_name VARCHAR(100);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS product_code VARCHAR(10);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS overseas_yn VARCHAR(1);

UPDATE asset_positions
SET item_code = symbol
WHERE item_code IS NULL AND symbol IS NOT NULL;
