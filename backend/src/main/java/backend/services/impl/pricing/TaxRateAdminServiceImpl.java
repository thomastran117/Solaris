package backend.services.impl.pricing;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.tax.CreateTaxRateRequest;
import backend.dtos.requests.tax.UpdateTaxRateRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.tax.TaxRateResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.TaxRate;
import backend.repositories.TaxRateRepository;
import backend.services.intf.pricing.TaxRateAdminService;
import backend.services.pricing.TaxJurisdiction;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TaxRateAdminServiceImpl implements TaxRateAdminService {

    private final TaxRateRepository taxRateRepository;

    public TaxRateAdminServiceImpl(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TaxRateResponse> listRates(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "country", "state", "postalCode"));
        return new PagedResponse<>(taxRateRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TaxRateResponse getRate(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public TaxRateResponse createRate(CreateTaxRateRequest request) {
        String country = TaxJurisdiction.iso2(request.getCountry());
        String state = TaxJurisdiction.iso2(request.getState());
        String postalCode = TaxJurisdiction.postal(request.getPostalCode());
        validateRate(request.getRate());

        if (country.isEmpty()) {
            throw new BadRequestException("country is required");
        }
        // Belt-and-braces over the DB unique constraint (also gives a clean 409 instead of a 500).
        taxRateRepository.findByCountryAndStateAndPostalCode(country, state, postalCode).ifPresent(existing -> {
            throw new ConflictException("A tax rate already exists for that jurisdiction");
        });

        TaxRate rate = new TaxRate();
        rate.setCountry(country);
        rate.setState(state);
        rate.setPostalCode(postalCode);
        rate.setRate(request.getRate());
        rate.setShippingTaxable(request.isShippingTaxable());
        rate.setActive(request.getActive() == null ? true : request.getActive());
        rate.setDescription(request.getDescription());
        return toResponse(taxRateRepository.save(rate));
    }

    @Override
    @Transactional
    public TaxRateResponse updateRate(UUID id, UpdateTaxRateRequest request) {
        TaxRate rate = findOrThrow(id);
        if (request.getRate() != null) {
            validateRate(request.getRate());
            rate.setRate(request.getRate());
        }
        if (request.getShippingTaxable() != null) {
            rate.setShippingTaxable(request.getShippingTaxable());
        }
        if (request.getActive() != null) {
            rate.setActive(request.getActive());
        }
        if (request.getDescription() != null) {
            rate.setDescription(request.getDescription());
        }
        return toResponse(taxRateRepository.save(rate));
    }

    @Override
    @Transactional
    public void deleteRate(UUID id) {
        // Soft delete: deactivate rather than remove. Orders snapshot taxRateId, so a hard delete would
        // orphan that reference for historical audit joins. The resolver already ignores inactive rows.
        TaxRate rate = findOrThrow(id);
        rate.setActive(false);
        taxRateRepository.save(rate);
    }

    private TaxRate findOrThrow(UUID id) {
        return taxRateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tax rate not found with id: " + id));
    }

    private void validateRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BadRequestException("rate must be a fraction in [0, 1) — e.g. 0.08875 for 8.875%");
        }
    }

    private TaxRateResponse toResponse(TaxRate t) {
        return new TaxRateResponse(
                t.getId(), t.getCountry(), t.getState(), t.getPostalCode(), t.getRate(),
                t.isShippingTaxable(), t.isActive(), t.getDescription(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
