package backend.repositories;

import backend.models.core.VendorSLAMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorSLAMetricRepository extends JpaRepository<VendorSLAMetric, java.util.UUID> {

    Optional<VendorSLAMetric> findByVendorIdAndDate(UUID vendorId, LocalDate date);

    List<VendorSLAMetric> findByVendorIdOrderByDateDesc(UUID vendorId);

    Page<VendorSLAMetric> findByVendorId(UUID vendorId, Pageable pageable);

    List<VendorSLAMetric> findByVendorIdAndDateBetweenOrderByDateAsc(UUID vendorId, LocalDate from, LocalDate to);
}
