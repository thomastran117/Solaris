package backend.services.impl.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateTeamMemberRoleRequest;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.configurations.environment.EnvironmentSetting;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.company.CompanyMembershipService;
import backend.services.intf.support.EmailService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CompanyMembershipServiceImpl implements CompanyMembershipService {

    private static final Logger log = LoggerFactory.getLogger(CompanyMembershipServiceImpl.class);
    private static final long INVITE_TTL_DAYS = 7;

    private final CompanyMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CompanyAccessService companyAccessService;
    private final EmailService emailService;
    private final EnvironmentSetting environmentSetting;

    public CompanyMembershipServiceImpl(
            CompanyMembershipRepository membershipRepository,
            UserRepository userRepository,
            CompanyAccessService companyAccessService,
            EmailService emailService,
            EnvironmentSetting environmentSetting) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.companyAccessService = companyAccessService;
        this.emailService = emailService;
        this.environmentSetting = environmentSetting;
    }

    @Override
    @Transactional
    public TeamMembershipResponse inviteMember(UUID companyId, UUID inviterUserId, InviteTeamMemberRequest request) {
        Company company = companyAccessService.require(companyId, inviterUserId, CompanyCapability.MANAGE_COMPANY);

        CompanyRole role = request.getRole();
        if (role == CompanyRole.OWNER) {
            throw new BadRequestException("Cannot invite a user as OWNER. Use the transfer-ownership flow instead.");
        }
        if (role == null) {
            throw new BadRequestException("Role is required");
        }

        String normalizedEmail = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BadRequestException("Email is required");
        }

        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            boolean alreadyMember = membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                    companyId, existingUser.get().getId(), CompanyMembershipStatus.ACTIVE);
            if (alreadyMember) {
                throw new ConflictException("This user is already an active member of the company");
            }
        }

        membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(
                companyId, normalizedEmail, CompanyMembershipStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ConflictException("An invite for this email is already pending");
                });

        User inviter = userRepository.getReferenceById(inviterUserId);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setInviteEmail(normalizedEmail);
        membership.setRole(role);
        membership.setStatus(CompanyMembershipStatus.PENDING);
        membership.setInviteToken(UUID.randomUUID().toString());
        membership.setInviteExpiresAt(Instant.now().plus(INVITE_TTL_DAYS, ChronoUnit.DAYS));
        membership.setInvitedBy(inviter);

        CompanyMembership saved = membershipRepository.save(membership);

        String acceptUrl = environmentSetting.getEmail().getVerificationBaseUrl()
                + "/invite/accept/" + saved.getInviteToken();
        String inviterName = displayName(inviter);
        String roleLabel = role == CompanyRole.MANAGER ? "Manager" : "Employee";
        try {
            emailService.sendTeamInviteEmail(normalizedEmail, company.getName(), roleLabel, inviterName, acceptUrl);
        } catch (Exception e) {
            log.warn("Failed to publish team invite email for company={} membershipId={}", companyId, saved.getId(), e);
        }
        log.info("Team invite created for company={} membershipId={} role={}", companyId, saved.getId(), role);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> listMembers(UUID companyId, UUID requestingUserId) {
        companyAccessService.require(companyId, requestingUserId, CompanyCapability.MANAGE_COMPANY);
        return membershipRepository
                .findAllByCompanyIdAndStatusIn(companyId,
                        List.of(CompanyMembershipStatus.ACTIVE, CompanyMembershipStatus.PENDING))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public TeamMembershipResponse updateMemberRole(
            UUID companyId, UUID membershipId, UUID requestingUserId, UpdateTeamMemberRoleRequest request) {
        companyAccessService.require(companyId, requestingUserId, CompanyCapability.MANAGE_COMPANY);

        CompanyMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found with id: " + membershipId));

        if (membership.getCompany().getId() != companyId) {
            throw new ResourceNotFoundException("Team member not found with id: " + membershipId);
        }
        if (membership.getRole() == CompanyRole.OWNER) {
            throw new ConflictException("Cannot change the role of the company owner");
        }
        if (request.getRole() == null || request.getRole() == CompanyRole.OWNER) {
            throw new BadRequestException("New role must be MANAGER or EMPLOYEE");
        }

        membership.setRole(request.getRole());
        CompanyMembership saved = membershipRepository.save(membership);

        if (saved.getUser() != null) {
            companyAccessService.invalidate(companyId, saved.getUser().getId());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void revokeMember(UUID companyId, UUID membershipId, UUID requestingUserId) {
        companyAccessService.require(companyId, requestingUserId, CompanyCapability.MANAGE_COMPANY);

        CompanyMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found with id: " + membershipId));

        if (membership.getCompany().getId() != companyId) {
            throw new ResourceNotFoundException("Team member not found with id: " + membershipId);
        }
        if (membership.getRole() == CompanyRole.OWNER) {
            throw new ConflictException("Cannot revoke the company owner");
        }

        membership.setStatus(CompanyMembershipStatus.REVOKED);
        membership.setInviteToken(null);
        membershipRepository.save(membership);

        if (membership.getUser() != null) {
            companyAccessService.invalidate(companyId, membership.getUser().getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TeamInvitePreviewResponse previewInvite(String token) {
        CompanyMembership invite = loadValidPendingInvite(token);
        String inviterName = invite.getInvitedBy() == null ? null : displayName(invite.getInvitedBy());
        return new TeamInvitePreviewResponse(
                invite.getCompany().getName(),
                invite.getInviteEmail(),
                invite.getRole(),
                inviterName,
                invite.getInviteExpiresAt()
        );
    }

    @Override
    @Transactional
    public TeamMembershipResponse acceptInvite(String token, UUID acceptingUserId) {
        CompanyMembership invite = loadValidPendingInvite(token);

        User acceptingUser = userRepository.findById(acceptingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String userEmail = acceptingUser.getEmail() == null ? "" : acceptingUser.getEmail().trim().toLowerCase();
        if (!userEmail.equals(invite.getInviteEmail())) {
            throw new ForbiddenException("This invite was sent to a different email address");
        }

        boolean alreadyActive = membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                invite.getCompany().getId(), acceptingUserId, CompanyMembershipStatus.ACTIVE);
        if (alreadyActive) {
            throw new ConflictException("You are already an active member of this company");
        }

        invite.setUser(acceptingUser);
        invite.setStatus(CompanyMembershipStatus.ACTIVE);
        invite.setAcceptedAt(Instant.now());
        invite.setInviteToken(null);
        CompanyMembership saved = membershipRepository.save(invite);

        companyAccessService.invalidate(saved.getCompany().getId(), acceptingUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyRoleResponse describeMyAccess(UUID companyId, UUID userId) {
        CompanyRole role = companyAccessService.resolveRole(companyId, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this company"));
        Set<CompanyCapability> capabilities = CompanyCapability.capabilitiesFor(role);
        return new CompanyRoleResponse(role, capabilities);
    }

    private CompanyMembership loadValidPendingInvite(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Invite not found");
        }
        CompanyMembership invite = membershipRepository.findByInviteToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));
        if (invite.getStatus() != CompanyMembershipStatus.PENDING) {
            throw new ResourceNotFoundException("Invite is no longer valid");
        }
        if (invite.getInviteExpiresAt() != null && invite.getInviteExpiresAt().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Invite has expired");
        }
        return invite;
    }

    private TeamMembershipResponse toResponse(CompanyMembership m) {
        User u = m.getUser();
        UUID userId = u == null ? null : u.getId();
        String displayName = u == null ? null : displayName(u);
        String email = u != null && u.getEmail() != null ? u.getEmail() : m.getInviteEmail();
        return new TeamMembershipResponse(
                m.getId(),
                userId,
                displayName,
                email,
                m.getRole(),
                m.getStatus(),
                m.getCreatedAt(),
                m.getAcceptedAt()
        );
    }

    private static String displayName(User u) {
        String first = u.getFirstName();
        String last = u.getLastName();
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) {
            return u.getEmail();
        }
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
}
