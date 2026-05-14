package backend.services.impl.promotions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.pricing.CreatePromotionRuleRequest;
import backend.dtos.requests.pricing.UpdatePromotionRuleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.pricing.PromotionRuleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CustomerSegment;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.DiscountStatus;
import backend.models.enums.PromotionRuleType;
import backend.repositories.BundleRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.CustomerSegmentRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.promotions.PromotionRuleService;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.models.enums.CompanyCapability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PromotionRuleServiceImpl implements PromotionRuleService {

    private final PromotionRuleRepository ruleRepository;
    private final CompanyRepository companyRepository;
    private final CompanyAccessService companyAccessService;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final PromotionConfigValidator configValidator;
    private final ObjectMapper objectMapper;

    public PromotionRuleServiceImpl(
            PromotionRuleRepository ruleRepository,
            CompanyRepository companyRepository,
            CompanyAccessService companyAccessService,
            ProductRepository productRepository,
            BundleRepository bundleRepository,
            CustomerSegmentRepository segmentRepository,
            PromotionConfigValidator configValidator,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.companyRepository = companyRepository;
        this.companyAccessService = companyAccessService;
        this.productRepository = productRepository;
        this.bundleRepository = bundleRepository;
        this.segmentRepository = segmentRepository;
        this.configValidator = configValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public PagedResponse<PromotionRuleResponse> listRules(long companyId, long ownerId, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedResponse<>(
                ruleRepository.findAllByCompanyId(companyId, pageable).map(this::toResponse));
    }

    @Override
    public PromotionRuleResponse getRule(long companyId, long ruleId, long ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        PromotionRule rule = ruleRepository.findByIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion rule not found with id: " + ruleId));
        return toResponse(rule);
    }

    @Override
    @Transactional
    public PromotionRuleResponse createRule(long companyId, long ownerId, CreatePromotionRuleRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        PromotionRuleType type = parseType(request.getRuleType());
        String configJson = configValidator.validateAndSerialise(type, request.getConfig());
        validateDates(request.getStartDate(), request.getEndDate());

        PromotionRule rule = new PromotionRule();
        rule.setCompany(companyRepository.getReferenceById(companyId));
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(type);
        rule.setConfigJson(configJson);
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        rule.setStackable(Boolean.TRUE.equals(request.getStackable()));
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setMinCartAmount(request.getMinCartAmount());
        rule.setMaxUses(request.getMaxUses());
        rule.setMaxUsesPerUser(request.getMaxUsesPerUser());
        rule.setFundedByCompany(resolveFunder(request.getFundedByCompanyId()));
        rule.setTargetProducts(resolveProducts(request.getTargetProductIds(), companyId));
        rule.setTargetBundles(resolveBundles(request.getTargetBundleIds(), companyId));
        rule.setTargetSegments(resolveSegments(request.getTargetSegmentIds()));

        return toResponse(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public PromotionRuleResponse updateRule(
            long companyId, long ruleId, long ownerId, UpdatePromotionRuleRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        PromotionRule rule = ruleRepository.findByIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion rule not found with id: " + ruleId));

        if (request.getName() != null) rule.setName(request.getName());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());

        if (request.getConfig() != null) {
            rule.setConfigJson(configValidator.validateAndSerialise(rule.getRuleType(), request.getConfig()));
        }

        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getStackable() != null) rule.setStackable(request.getStackable());

        if (request.getStatus() != null) {
            if ("EXPIRED".equalsIgnoreCase(request.getStatus())) {
                throw new BadRequestException("Status 'EXPIRED' cannot be set directly — it is computed from endDate");
            } else if ("ACTIVE".equalsIgnoreCase(request.getStatus())) {
                rule.setStatus(DiscountStatus.ACTIVE);
            } else if ("DISABLED".equalsIgnoreCase(request.getStatus())) {
                rule.setStatus(DiscountStatus.DISABLED);
            } else {
                throw new BadRequestException("Invalid status '" + request.getStatus() + "'. Must be ACTIVE or DISABLED");
            }
        }

        if (request.getStartDate() != null || request.getEndDate() != null) {
            Instant newStart = request.getStartDate() != null ? request.getStartDate() : rule.getStartDate();
            Instant newEnd   = request.getEndDate()   != null ? request.getEndDate()   : rule.getEndDate();
            validateDates(newStart, newEnd);
            if (request.getStartDate() != null) rule.setStartDate(request.getStartDate());
            if (request.getEndDate()   != null) rule.setEndDate(request.getEndDate());
        }

        if (request.getMinCartAmount() != null) rule.setMinCartAmount(request.getMinCartAmount());
        if (request.getMaxUses()        != null) rule.setMaxUses(request.getMaxUses());
        if (request.getMaxUsesPerUser() != null) rule.setMaxUsesPerUser(request.getMaxUsesPerUser());

        if (request.getFundedByCompanyId() != null) {
            rule.setFundedByCompany(resolveFunder(request.getFundedByCompanyId()));
        }

        if (request.getTargetProductIds() != null) {
            rule.setTargetProducts(resolveProducts(request.getTargetProductIds(), companyId));
        }
        if (request.getTargetBundleIds() != null) {
            rule.setTargetBundles(resolveBundles(request.getTargetBundleIds(), companyId));
        }
        if (request.getTargetSegmentIds() != null) {
            rule.setTargetSegments(resolveSegments(request.getTargetSegmentIds()));
        }

        return toResponse(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public void deleteRule(long companyId, long ruleId, long ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        PromotionRule rule = ruleRepository.findByIdAndCompanyId(ruleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion rule not found with id: " + ruleId));

        rule.getTargetProducts().clear();
        rule.getTargetBundles().clear();
        rule.getTargetSegments().clear();
        ruleRepository.delete(rule);
    }

    // --- helpers ---

    private PromotionRuleType parseType(String raw) {
        try {
            return PromotionRuleType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid ruleType '" + raw
                    + "'. Must be one of PERCENTAGE_OFF, FIXED_OFF, BOGO, TIERED_PRICE, FREE_SHIPPING");
        }
    }

    private void validateDates(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BadRequestException("endDate must be strictly after startDate");
        }
    }

    private Company resolveFunder(Long funderId) {
        if (funderId == null) return null;
        return companyRepository.findById(funderId)
                .orElseThrow(() -> new BadRequestException("fundedByCompanyId does not reference an existing company"));
    }

    private Set<Product> resolveProducts(List<Long> ids, long companyId) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        List<Long> deduped = new ArrayList<>(new HashSet<>(ids));
        List<Product> found = productRepository.findAllByIdInAndCompanyId(deduped, companyId);
        if (found.size() != deduped.size()) {
            throw new BadRequestException("One or more product IDs are invalid or do not belong to this company");
        }
        return new HashSet<>(found);
    }

    private Set<ProductBundle> resolveBundles(List<Long> ids, long companyId) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        List<Long> deduped = new ArrayList<>(new HashSet<>(ids));
        List<ProductBundle> found = bundleRepository.findAllByIdInAndCompanyId(deduped, companyId);
        if (found.size() != deduped.size()) {
            throw new BadRequestException("One or more bundle IDs are invalid or do not belong to this company");
        }
        return new HashSet<>(found);
    }

    private Set<CustomerSegment> resolveSegments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        List<Long> deduped = new ArrayList<>(new HashSet<>(ids));
        List<CustomerSegment> found = segmentRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            throw new BadRequestException("One or more segment IDs are invalid");
        }
        return new HashSet<>(found);
    }

    private PromotionRuleResponse toResponse(PromotionRule r) {
        Instant now = Instant.now();
        DiscountStatus effectiveStatus = (r.getEndDate() != null && r.getEndDate().isBefore(now))
                ? DiscountStatus.EXPIRED
                : r.getStatus();

        JsonNode configNode;
        try {
            configNode = objectMapper.readTree(r.getConfigJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt config JSON for rule " + r.getId(), e);
        }

        List<Long> productIds = r.getTargetProducts().stream().map(Product::getId).sorted().toList();
        List<Long> bundleIds  = r.getTargetBundles().stream().map(ProductBundle::getId).sorted().toList();
        List<Long> segmentIds = r.getTargetSegments().stream().map(CustomerSegment::getId).sorted().toList();
        Long fundedById = r.getFundedByCompany() != null ? r.getFundedByCompany().getId() : null;

        return new PromotionRuleResponse(
                r.getId(),
                r.getCompany().getId(),
                r.getName(),
                r.getDescription(),
                r.getRuleType(),
                configNode,
                effectiveStatus,
                r.getPriority(),
                r.isStackable(),
                r.getStartDate(),
                r.getEndDate(),
                r.getMinCartAmount(),
                r.getMaxUses(),
                r.getUsedCount(),
                r.getMaxUsesPerUser(),
                fundedById,
                productIds,
                bundleIds,
                segmentIds,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
