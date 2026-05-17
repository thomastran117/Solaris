-- Stores up to 5 photo URLs per review. Photos are uploaded directly to S3 via the
-- existing presign flow; only the resulting public URL is persisted here.

CREATE TABLE review_media (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    url VARCHAR(1024) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_review FOREIGN KEY (review_id) REFERENCES product_reviews(id) ON DELETE CASCADE,
    INDEX idx_media_review (review_id, position)
);
