package backend.services.impl.promotions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.repositories.CouponRepository;

import java.time.Instant;

/**
 * Periodically hard-deletes coupon rows whose {@code endDate} has passed.
 * The scheduler interval is controlled by {@code app.coupon.expiry.interval-ms}
 * (default 1 hour). The real-time expiry check in {@code CouponServiceImpl#toResponse}
 * means coupons appear as {@code EXPIRED} immediately — this job handles permanent cleanup.
 */
@Component
public class CouponExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpiryScheduler.class);

    private final CouponRepository couponRepository;

    public CouponExpiryScheduler(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Scheduled(fixedDelayString = "${app.coupon.expiry.interval-ms:3600000}")
    @Transactional
    public void deleteExpiredCoupons() {
        int deleted = couponRepository.deleteAllExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("[COUPON EXPIRY] Deleted {} expired coupon(s)", deleted);
        }
    }
}
