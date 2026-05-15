-- Round 2 fix: add the @Version column that round 1 added to Order/SubOrder/Coupon
-- but missed on Return. Without it, two concurrent approveReturn calls both pass the
-- REQUESTED state-check and both issue a Stripe refund.

ALTER TABLE returns ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
