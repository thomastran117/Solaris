-- Backs the "helpful" vote feature. One row per (review, user) lets us enforce idempotency
-- via the unique constraint while the denormalised helpful_count on product_reviews keeps
-- the read path cheap.

CREATE TABLE review_votes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vote_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_vote_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    INDEX idx_vote_review (review_id),
    INDEX idx_vote_user (user_id)
);
