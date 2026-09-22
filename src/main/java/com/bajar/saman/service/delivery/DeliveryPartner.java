package com.bajar.saman.service.delivery;

import com.bajar.saman.entity.DeliveryPartnerType;
import com.bajar.saman.entity.Order;

/**
 * Roadmap Addendum v2 §2.3: mirrors PaymentGateway's Strategy pattern
 * exactly. HONEST LIMITATION (same standard as PaymentGateway's
 * implementations): no real courier API exists or is planned within this
 * project's current resources — initiateShipment() SIMULATES a partner
 * accepting a shipment (generates a plausible tracking number) rather than
 * making a real API call. The exact point a real integration would replace
 * this is marked in each implementation.
 */
public interface DeliveryPartner {

    DeliveryPartnerType getType();

    /**
     * Called when an order is dispatched from the warehouse. Returns a
     * tracking number the customer can reference — in a real integration,
     * this would come from the courier's actual booking API response.
     */
    String initiateShipment(Order order);
}