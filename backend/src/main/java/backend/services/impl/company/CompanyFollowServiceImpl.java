package backend.services.impl.company;

import backend.dtos.responses.company.CompanyFollowResponse;
import backend.dtos.responses.company.FollowStatusResponse;
import backend.dtos.responses.company.FollowedCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyFollow;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyFollowRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyFollowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CompanyFollowServiceImpl implements CompanyFollowService {

    private final CompanyFollowRepository followRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyFollowServiceImpl(
            CompanyFollowRepository followRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.followRepository = followRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CompanyFollowResponse follow(UUID userId, UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BadRequestException("Cannot follow an inactive company");
        }
        if (followRepository.existsByUserIdAndCompanyId(userId, companyId)) {
            throw new ConflictException("Already following this company");
        }

        backend.models.core.User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CompanyFollow follow = new CompanyFollow();
        follow.setUser(user);
        follow.setCompany(company);
        follow.setFollowedAt(Instant.now());
        follow.setNotificationsEnabled(true);

        CompanyFollow saved = followRepository.save(follow);
        return toFollowResponse(saved);
    }

    @Override
    @Transactional
    public void unfollow(UUID userId, UUID companyId) {
        followRepository.findByUserIdAndCompanyId(userId, companyId)
                .ifPresent(followRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusResponse getStatus(UUID userId, UUID companyId) {
        long followerCount = followRepository.countByCompanyId(companyId);
        if (userId == null) {
            return new FollowStatusResponse(false, false, followerCount);
        }
        return followRepository.findByUserIdAndCompanyId(userId, companyId)
                .map(f -> new FollowStatusResponse(true, f.isNotificationsEnabled(), followerCount))
                .orElse(new FollowStatusResponse(false, false, followerCount));
    }

    @Override
    @Transactional
    public CompanyFollowResponse setNotifications(UUID userId, UUID companyId, boolean enabled) {
        CompanyFollow follow = followRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Not following this company"));
        follow.setNotificationsEnabled(enabled);
        return toFollowResponse(followRepository.save(follow));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FollowedCompanyResponse> listFollowing(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("followedAt").descending());
        Page<CompanyFollow> result = followRepository.findAllByUserIdWithCompany(userId, pageable);
        Page<FollowedCompanyResponse> mapped = result.map(f -> {
            Company c = f.getCompany();
            return new FollowedCompanyResponse(
                    f.getId(), c.getId(), c.getName(), c.getLogoUrl(),
                    c.getIndustry(), f.isNotificationsEnabled(), f.getFollowedAt());
        });
        return new PagedResponse<>(mapped);
    }

    private CompanyFollowResponse toFollowResponse(CompanyFollow f) {
        return new CompanyFollowResponse(
                f.getId(), f.getCompany().getId(), f.getFollowedAt(), f.isNotificationsEnabled());
    }
}
