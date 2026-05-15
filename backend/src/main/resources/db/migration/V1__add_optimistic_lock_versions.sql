-- Adds @Version columns to entities mutated under concurrent load so that lost-update
-- races (refund denormalisation, status transitions, coupon redemption caps, marketplace
-- approval) fail loudly with an OptimisticLockException instead of silently overwriting.

ALTER TABLE orders     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sub_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE coupons    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
