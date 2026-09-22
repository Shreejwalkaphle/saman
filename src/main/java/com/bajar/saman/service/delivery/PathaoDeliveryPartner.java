package com.bajar.saman.service.delivery;

import com.bajar.saman.entity.DeliveryPartnerType;
import com.bajar.saman.entity.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PathaoDeliveryPartner implements DeliveryPartner {

    @Override
    public DeliveryPartnerType getType() {
        return DeliveryPartnerType.PATHAO;
    }

    @Override
    public String initiateShipment(Order order) {
        // REAL IMPLEMENTATION WOULD GO HERE: an HTTP call to Pathao's
        // shipment-booking API, passing order.getShippingAddress*() fields
        // and package details, returning Pathao's own consignment/tracking
        // ID. No real Pathao merchant credentials exist in this project —
        // same category of limitation as EsewaPaymentGateway/
        // KhaltiPaymentGateway, flagged with equal transparency here.
        return "PATHAO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}