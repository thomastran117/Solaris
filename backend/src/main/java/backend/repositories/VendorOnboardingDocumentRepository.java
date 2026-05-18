package backend.repositories;

import backend.models.core.VendorOnboardingDocument;
import backend.models.enums.VendorDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorOnboardingDocumentRepository extends JpaRepository<VendorOnboardingDocument, java.util.UUID> {

    List<VendorOnboardingDocument> findAllByMarketplaceVendorId(java.util.UUID marketplaceVendorId);

    Optional<VendorOnboardingDocument> findByMarketplaceVendorIdAndDocumentType(
            java.util.UUID marketplaceVendorId, VendorDocumentType documentType);
}
