package com.bajar.saman.service.delivery;

import com.bajar.saman.entity.DeliveryPartnerType;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.repository.DeliveryPartnerConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mirrors PaymentGatewayFactory exactly — same collect-all-beans pattern,
 * same fail-closed default on missing/unknown config.
 */
@Component
public class DeliveryPartnerFactory {

    private final Map<DeliveryPartnerType, DeliveryPartner> partnersByType;
    private final DeliveryPartnerConfigRepository configRepository;

    public DeliveryPartnerFactory(List<DeliveryPartner> partners, DeliveryPartnerConfigRepository configRepository) {
        this.partnersByType = partners.stream()
                .collect(Collectors.toMap(DeliveryPartner::getType, Function.identity()));
        this.configRepository = configRepository;
    }

    public DeliveryPartner getPartner(DeliveryPartnerType type) {
        boolean enabled = configRepository.findByName(type)
                .map(config -> config.isEnabled())
                .orElse(false); // fail closed — same reasoning as PaymentGatewayFactory

        if (!enabled) {
            throw new InvalidProductDataException(
                    "Delivery partner '" + type + "' is not currently available");
        }

        DeliveryPartner partner = partnersByType.get(type);
        if (partner == null) {
            throw new IllegalStateException(
                    "Partner '" + type + "' is enabled in config but has no registered implementation");
        }

        return partner;
    }
}