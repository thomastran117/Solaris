package backend.controllers.impl.pricing;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.pricing.PayoutAttributionResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.pricing.PricingReportService;

import java.time.Instant;

@RestController
@RequestMapping("/admin/pricing")
public class PricingReportController {

    private final PricingReportService reportService;

    public PricingReportController(PricingReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/payout-attribution")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<PayoutAttributionResponse> getPayoutAttribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        try {
            return ResponseEntity.ok(reportService.getPayoutAttribution(from, to));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
