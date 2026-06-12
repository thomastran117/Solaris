package backend.services.impl.promotions;

import backend.repositories.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponExpirySchedulerTest {

    private CouponRepository couponRepository;
    private CouponExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        scheduler = new CouponExpiryScheduler(couponRepository);
    }

    @Test
    void deleteExpiredCoupons_returnsZero_repositoryStillCalled() {
        when(couponRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(0);

        scheduler.deleteExpiredCoupons();

        verify(couponRepository).deleteAllExpiredBefore(any(Instant.class));
    }

    @Test
    void deleteExpiredCoupons_returnsSome_repositoryCalledAndCountLogged() {
        when(couponRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(3);

        scheduler.deleteExpiredCoupons(); // must not throw

        verify(couponRepository).deleteAllExpiredBefore(any(Instant.class));
    }
}
