package backend.services.impl.promotions;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.coupon.CreateCouponRequest;
import backend.dtos.requests.coupon.UpdateCouponRequest;
import backend.dtos.responses.coupon.CouponResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Coupon;
import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;
import backend.repositories.CompanyRepository;
import backend.repositories.CouponRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.promotions.CouponService;
import backend.models.enums.CompanyCapability;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAccessService companyAccessService;

    public CouponServiceImpl(CouponRepository couponRepository,
                             CompanyRepository companyRepository,
                             CompanyAccessService companyAccessService) {
        this.couponRepository = couponRepository;
        this.companyRepository = companyRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    public PagedResponse<CouponResponse> listCoupons(UUID companyId, UUID ownerId, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedResponse<>(
                couponRepository.findAllByCompanyId(companyId, pageable).map(this::toResponse));
    }

    @Override
    public CouponResponse getCoupon(UUID companyId, UUID couponId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        return toResponse(couponRepository.findByIdAndCompanyId(couponId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + couponId)));
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(UUID companyId, UUID ownerId, CreateCouponRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A coupon with code '" + code + "' already exists");
        }

        DiscountType type = parseType(request.getType());
        validateValue(type, request.getValue());
        validateDates(request.getStartDate(), request.getEndDate());

        Coupon coupon = new Coupon();
        coupon.setCompany(companyRepository.getReferenceById(companyId));
        coupon.setCode(code);
        coupon.setName(request.getName());
        coupon.setType(type);
        coupon.setValue(request.getValue());
        coupon.setStartDate(request.getStartDate());
        coupon.setEndDate(request.getEndDate());
        coupon.setMaxUses(request.getMaxUses());
        coupon.setMaxUsesPerUser(request.getMaxUsesPerUser());
        coupon.setMinOrderAmount(request.getMinOrderAmount());

        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(UUID companyId, UUID couponId, UUID ownerId, UpdateCouponRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        Coupon coupon = couponRepository.findByIdAndCompanyId(couponId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + couponId));

        if (request.getName() != null) coupon.setName(request.getName());

        DiscountType effectiveType = coupon.getType();
        BigDecimal effectiveValue = coupon.getValue();

        if (request.getType() != null) {
            effectiveType = parseType(request.getType());
            coupon.setType(effectiveType);
        }
        if (request.getValue() != null) {
            effectiveValue = request.getValue();
            coupon.setValue(effectiveValue);
        }
        if (request.getType() != null || request.getValue() != null) {
            validateValue(effectiveType, effectiveValue);
        }

        if (request.getStatus() != null) {
            if ("EXPIRED".equalsIgnoreCase(request.getStatus())) {
                throw new BadRequestException("Status 'EXPIRED' cannot be set directly — it is computed from endDate");
            }
            if ("ACTIVE".equalsIgnoreCase(request.getStatus())) {
                coupon.setStatus(DiscountStatus.ACTIVE);
            } else if ("DISABLED".equalsIgnoreCase(request.getStatus())) {
                coupon.setStatus(DiscountStatus.DISABLED);
            } else {
                throw new BadRequestException("Invalid status '" + request.getStatus() + "'. Must be ACTIVE or DISABLED");
            }
        }

        if (request.getStartDate() != null || request.getEndDate() != null) {
            Instant newStart = request.getStartDate() != null ? request.getStartDate() : coupon.getStartDate();
            Instant newEnd   = request.getEndDate()   != null ? request.getEndDate()   : coupon.getEndDate();
            validateDates(newStart, newEnd);
            if (request.getStartDate() != null) coupon.setStartDate(request.getStartDate());
            if (request.getEndDate()   != null) coupon.setEndDate(request.getEndDate());
        }

        if (request.getMaxUses()        != null) coupon.setMaxUses(request.getMaxUses());
        if (request.getMaxUsesPerUser() != null) coupon.setMaxUsesPerUser(request.getMaxUsesPerUser());
        if (request.getMinOrderAmount() != null) coupon.setMinOrderAmount(request.getMinOrderAmount());

        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public void deleteCoupon(UUID companyId, UUID couponId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        Coupon coupon = couponRepository.findByIdAndCompanyId(couponId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + couponId));

        couponRepository.delete(coupon);
    }

    // --- Private helpers ---

    private CouponResponse toResponse(Coupon c) {
        Instant now = Instant.now();
        DiscountStatus effectiveStatus = (c.getEndDate() != null && c.getEndDate().isBefore(now))
                ? DiscountStatus.EXPIRED
                : c.getStatus();

        return new CouponResponse(
                c.getId(),
                c.getCompany().getId(),
                c.getCode(),
                c.getName(),
                c.getType(),
                c.getValue(),
                effectiveStatus,
                c.getStartDate(),
                c.getEndDate(),
                c.getMaxUses(),
                c.getUsedCount(),
                c.getMaxUsesPerUser(),
                c.getMinOrderAmount(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private DiscountType parseType(String raw) {
        try {
            return DiscountType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid type '" + raw + "'. Must be PERCENTAGE or FIXED_AMOUNT");
        }
    }

    private void validateValue(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Percentage coupon value cannot exceed 100");
        }
    }

    private void validateDates(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BadRequestException("endDate must be strictly after startDate");
        }
    }
}
