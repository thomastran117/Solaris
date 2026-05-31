package backend.services.impl.company;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyAccessServiceImpl implements CompanyAccessService {

    private static final Logger log = LoggerFactory.getLogger(CompanyAccessServiceImpl.class);
    private static final long CACHE_TTL_SECONDS = 60L;
    private static final String CACHE_NS = "company:access:";

    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final CacheService cacheService;

    public CompanyAccessServiceImpl(
            CompanyRepository companyRepository,
            CompanyMembershipRepository membershipRepository,
            CacheService cacheService) {
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.cacheService = cacheService;
    }

    @Override
    @Transactional(readOnly = true)
    public Company require(UUID companyId, UUID userId, CompanyCapability capability) {
        CompanyRole role = resolveRole(companyId, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this company"));
        if (!roleHas(role, capability)) {
            throw new ForbiddenException("Your role does not allow this action");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Company requireAnyAccess(UUID companyId, UUID userId) {
        resolveRole(companyId, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this company"));
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyRole> resolveRole(UUID companyId, UUID userId) {
        String key = cacheKey(companyId, userId);
        try {
            String cached = cacheService.get(key);
            if (cached != null) {
                if (cached.isEmpty()) return Optional.empty();
                return Optional.of(CompanyRole.valueOf(cached));
            }
        } catch (Exception e) {
            log.warn("Redis read failed for company access {}; falling through to DB", key, e);
        }

        Optional<CompanyRole> role = membershipRepository
                .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMembershipStatus.ACTIVE)
                .map(CompanyMembership::getRole);

        try {
            cacheService.set(key, role.map(Enum::name).orElse(""), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Redis write failed for company access {}", key, e);
        }
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> listAccessibleCompanies(UUID userId) {
        return membershipRepository.findCompaniesAccessibleByUser(userId, CompanyMembershipStatus.ACTIVE);
    }

    @Override
    public boolean roleHas(CompanyRole role, CompanyCapability capability) {
        return capability.grantedTo(role);
    }

    @Override
    public void invalidate(UUID companyId, UUID userId) {
        try {
            cacheService.delete(cacheKey(companyId, userId));
        } catch (Exception e) {
            log.warn("Redis invalidate failed for company access {}:{}", companyId, userId, e);
        }
    }

    private String cacheKey(UUID companyId, UUID userId) {
        return CACHE_NS + companyId + ":" + userId;
    }
}
