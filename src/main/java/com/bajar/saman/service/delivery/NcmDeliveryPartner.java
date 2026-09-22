package com.bajar.saman.service.delivery;

import com.bajar.saman.entity.DeliveryPartnerType;
import com.bajar.saman.entity.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NcmDeliveryPartner implements DeliveryPartner {

    @Override
    public DeliveryPartnerType getType() {
        return DeliveryPartnerType.NCM;
    }

    @Override
    public String initiateShipment(Order order) {
        // REAL IMPLEMENTATION: NCM (Nepal Can Move)'s booking API — same
        // honest simulation limitation as PathaoDeliveryPartner above.
        return "NCM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}