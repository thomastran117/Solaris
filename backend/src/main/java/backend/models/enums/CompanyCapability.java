package backend.models.enums;

import java.util.EnumSet;
import java.util.Set;

public enum CompanyCapability {
    MANAGE_COMPANY,
    MANAGE_PRODUCTS,
    MANAGE_INVENTORY,
    MANAGE_PROMOTIONS,
    MANAGE_QUOTES,
    FULFILL_ORDERS,
    READ_PRODUCTS,
    READ_ANALYTICS;

    public static Set<CompanyCapability> capabilitiesFor(CompanyRole role) {
        return switch (role) {
            case OWNER -> EnumSet.allOf(CompanyCapability.class);
            case MANAGER -> EnumSet.of(
                    MANAGE_PRODUCTS,
                    MANAGE_INVENTORY,
                    MANAGE_PROMOTIONS,
                    MANAGE_QUOTES,
                    FULFILL_ORDERS,
                    READ_PRODUCTS,
                    READ_ANALYTICS
            );
            case EMPLOYEE -> EnumSet.of(
                    FULFILL_ORDERS,
                    READ_PRODUCTS
            );
        };
    }

    public boolean grantedTo(CompanyRole role) {
        return capabilitiesFor(role).contains(this);
    }
}
