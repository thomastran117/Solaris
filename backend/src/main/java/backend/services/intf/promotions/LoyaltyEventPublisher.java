package backend.services.intf.promotions;

import backend.events.loyalty.LoyaltyEvent;

public interface LoyaltyEventPublisher {
    void publish(LoyaltyEvent event);
}
