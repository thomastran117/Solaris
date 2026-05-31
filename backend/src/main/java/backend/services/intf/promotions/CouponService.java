package backend.services.intf.promotions;

import java.util.UUID;
import backend.dtos.requests.coupon.CreateCouponRequest;
import backend.dtos.requests.coupon.UpdateCouponRequest;
import backend.dtos.responses.coupon.CouponResponse;
import backend.dtos.responses.general.PagedResponse;

public interface CouponService {
    PagedResponse<CouponResponse> listCoupons(UUID companyId, UUID ownerId, int page, int size);
    CouponResponse getCoupon(UUID companyId, UUID couponId, UUID ownerId);
    CouponResponse createCoupon(UUID companyId, UUID ownerId, CreateCouponRequest request);
    CouponResponse updateCoupon(UUID companyId, UUID couponId, UUID ownerId, UpdateCouponRequest request);
    void deleteCoupon(UUID companyId, UUID couponId, UUID ownerId);
}
