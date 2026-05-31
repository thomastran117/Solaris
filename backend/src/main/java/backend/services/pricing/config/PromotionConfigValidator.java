package backend.services.pricing.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import backend.exceptions.http.BadRequestException;
import backend.models.enums.PromotionRuleType;

import java.math.BigDecimal;

/**
 * Parses PromotionRule.configJson into a typed record and enforces per-type invariants.
 * Used on create/update to fail-fast with a clear message, and by PricingEngine evaluators
 * at runtime. The parsed record is the contract — evaluators assume it is already validated.
 */
@Component
public class PromotionConfigValidator {

    private static final BigDecimal ZERO  = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ObjectMapper mapper;

    public PromotionConfigValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Canonicalises the incoming JSON into the validated shape and returns it as a String
     * suitable for persisting in {@code PromotionRule.configJson}. Throws BadRequestException
     * with a user-safe message on any validation failure.
     */
    public String validateAndSerialise(PromotionRuleType type, JsonNode config) {
        if (config == null || config.isNull()) {
            throw new BadRequestException("config is required");
        }
        Object parsed = parse(type, config);
        try {
            return mapper.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to serialise config: " + e.getOriginalMessage());
        }
    }

    /** Parses a configJson string from the DB into a typed record. */
    public Object parseStored(PromotionRuleType type, String configJson) {
        try {
            JsonNode node = mapper.readTree(configJson);
            return parse(type, node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt promotion rule config for type " + type, e);
        }
    }

    private Object parse(PromotionRuleType type, JsonNode config) {
        try {
            return switch (type) {
                case PERCENTAGE_OFF -> validate(mapper.treeToValue(config, PercentageOffConfig.class));
                case FIXED_OFF      -> validate(mapper.treeToValue(config, FixedOffConfig.class));
                case BOGO           -> validate(mapper.treeToValue(config, BogoConfig.class));
                case TIERED_PRICE   -> validate(mapper.treeToValue(config, TieredPriceConfig.class));
                case FREE_SHIPPING  -> validate(mapper.treeToValue(config, FreeShippingConfig.class));
            };
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid config for " + type + ": " + e.getMessage());
        }
    }

    private PercentageOffConfig validate(PercentageOffConfig c) {
        if (c.percent() == null || c.percent().compareTo(ZERO) <= 0 || c.percent().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("percent must be > 0 and ≤ 100");
        }
        if (c.maxDiscount() != null && c.maxDiscount().compareTo(ZERO) <= 0) {
            throw new BadRequestException("maxDiscount must be > 0 when provided");
        }
        if (c.appliesTo() == null) {
            throw new BadRequestException("appliesTo must be LINE or ORDER");
        }
        return c;
    }

    private FixedOffConfig validate(FixedOffConfig c) {
        if (c.amount() == null || c.amount().compareTo(ZERO) <= 0) {
            throw new BadRequestException("amount must be > 0");
        }
        if (c.appliesTo() == null) {
            throw new BadRequestException("appliesTo must be LINE or ORDER");
        }
        return c;
    }

    private BogoConfig validate(BogoConfig c) {
        if (c.triggerQty() < 1) throw new BadRequestException("triggerQty must be ≥ 1");
        if (c.rewardQty()  < 1) throw new BadRequestException("rewardQty must be ≥ 1");
        if (c.maxApplicationsPerOrder() < 1) {
            throw new BadRequestException("maxApplicationsPerOrder must be ≥ 1");
        }
        if (c.rewardPercentOff() == null
                || c.rewardPercentOff().compareTo(ZERO) < 0
                || c.rewardPercentOff().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("rewardPercentOff must be between 0 and 100");
        }
        return c;
    }

    private TieredPriceConfig validate(TieredPriceConfig c) {
        if (c.breakpoints() == null || c.breakpoints().isEmpty()) {
            throw new BadRequestException("breakpoints must contain at least one entry");
        }
        int prevMin = Integer.MIN_VALUE;
        for (TieredPriceConfig.Breakpoint bp : c.breakpoints()) {
            if (bp.minQty() < 1) throw new BadRequestException("breakpoint.minQty must be ≥ 1");
            if (bp.unitPrice() == null || bp.unitPrice().compareTo(ZERO) < 0) {
                throw new BadRequestException("breakpoint.unitPrice must be ≥ 0");
            }
            if (bp.minQty() <= prevMin) {
                throw new BadRequestException("breakpoints must be strictly ascending by minQty");
            }
            prevMin = bp.minQty();
        }
        return c;
    }

    private FreeShippingConfig validate(FreeShippingConfig c) {
        if (c.maxShippingDiscount() != null && c.maxShippingDiscount().compareTo(ZERO) <= 0) {
            throw new BadRequestException("maxShippingDiscount must be > 0 when provided");
        }
        return c;
    }
}
