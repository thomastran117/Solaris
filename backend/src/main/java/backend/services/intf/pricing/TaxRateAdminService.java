package backend.services.intf.pricing;

import backend.dtos.requests.tax.CreateTaxRateRequest;
import backend.dtos.requests.tax.UpdateTaxRateRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.tax.TaxRateResponse;

import java.util.UUID;

/** Admin CRUD for the tax-rate jurisdiction table. */
public interface TaxRateAdminService {

    PagedResponse<TaxRateResponse> listRates(int page, int size);

    TaxRateResponse getRate(UUID id);

    TaxRateResponse createRate(CreateTaxRateRequest request);

    TaxRateResponse updateRate(UUID id, UpdateTaxRateRequest request);

    void deleteRate(UUID id);
}
