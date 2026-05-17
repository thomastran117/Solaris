-- Stores user-submitted abuse reports against reviews. The unique constraint enforces
-- idempotency per reporter so a single user cannot inflate report_count. Once enough
-- distinct reporters file against a review (see app.review.auto-hide-threshold) the
-- review transitions to PENDING_MODERATION and is hidden from public listings until a
-- moderator acts on it.

CREATE TABLE review_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    detail VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    resolved_by BIGINT NULL,
    CONSTRAINT uq_report_review_reporter UNIQUE (review_id, reporter_id),
    CONSTRAINT fk_report_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    INDEX idx_report_status (status, created_at)
);
