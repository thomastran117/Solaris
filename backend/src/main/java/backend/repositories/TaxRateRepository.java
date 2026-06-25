package backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.TaxRate;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, java.util.UUID> {

    /**
     * Most-specific-match lookup, bounded by the caller's {@link Pageable} (pass a size-1 page).
     * Candidates are limited to three jurisdiction granularities and ordered exact ZIP → state →
     * country default, so the first row is the rate to apply. Inactive rows are excluded.
     *
     * <p>Wildcards are the empty string (not NULL) so these are plain equality matches.
     * Inputs must already be normalised to uppercase by the caller.
     */
    @Query("""
            SELECT t FROM TaxRate t
            WHERE t.active = true
              AND t.country = :country
              AND ( (t.state = :state AND t.postalCode = :postalCode)
                 OR (t.state = :state AND t.postalCode = '')
                 OR (t.state = ''     AND t.postalCode = '') )
            ORDER BY CASE
                WHEN t.state = :state AND t.postalCode = :postalCode THEN 1
                WHEN t.state = :state AND t.postalCode = ''          THEN 2
                ELSE 3 END
            """)
    List<TaxRate> findBestMatch(@Param("country") String country,
                                @Param("state") String state,
                                @Param("postalCode") String postalCode,
                                Pageable pageable);

    /** Duplicate-jurisdiction guard for admin create. */
    Optional<TaxRate> findByCountryAndStateAndPostalCode(String country, String state, String postalCode);

    /** Duplicate-jurisdiction guard for admin update (excludes the row being edited). */
    boolean existsByCountryAndStateAndPostalCodeAndIdNot(String country, String state,
                                                         String postalCode, java.util.UUID id);
}
