-- Adds quality/moderation columns to product_reviews so we can surface verified-purchase
-- badges, denormalised vote/report counts, and serve a rating-distribution summary without
-- aggregating from scratch on every product fetch. Vote/report/media tables come in later
-- migrations as their services land.

ALTER TABLE product_reviews
    ADD COLUMN verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN helpful_count INT NOT NULL DEFAULT 0,
    ADD COLUMN report_count INT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Existing reviews were created behind the purchase gate, so they qualify as verified.
UPDATE product_reviews SET verified_purchase = TRUE;

CREATE INDEX idx_review_product_status_created
    ON product_reviews (product_id, status, created_at);

CREATE INDEX idx_review_product_rating
    ON product_reviews (product_id, rating);
