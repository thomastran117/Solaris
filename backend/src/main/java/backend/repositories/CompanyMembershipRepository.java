package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {

    Optional<CompanyMembership> findByCompanyIdAndUserIdAndStatus(
            long companyId, long userId, CompanyMembershipStatus status);

    boolean existsByCompanyIdAndUserIdAndStatus(
            long companyId, long userId, CompanyMembershipStatus status);

    Optional<CompanyMembership> findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(
            long companyId, String inviteEmail, CompanyMembershipStatus status);

    Optional<CompanyMembership> findByInviteToken(String inviteToken);

    List<CompanyMembership> findAllByCompanyIdAndStatusIn(
            long companyId, List<CompanyMembershipStatus> statuses);

    @Query("""
            select m.company from CompanyMembership m
            where m.user.id = :userId and m.status = :status
            order by m.company.name asc
            """)
    List<Company> findCompaniesAccessibleByUser(
            @Param("userId") long userId,
            @Param("status") CompanyMembershipStatus status);

    Optional<CompanyMembership> findByCompanyIdAndUserIdAndRoleAndStatus(
            long companyId, long userId, CompanyRole role, CompanyMembershipStatus status);
}
