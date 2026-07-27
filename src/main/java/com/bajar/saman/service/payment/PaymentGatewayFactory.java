package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.repository.PaymentGatewayConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * THE Factory pattern roadmap doc calls for: "PaymentGatewayFactory selects the
 * implementation at runtime based on config — controller/service code never
 * hardcodes which gateway is active." PaymentService (next) asks THIS class for
 * a gateway by type; it never instantiates or references EsewaPaymentGateway/
 * KhaltiPaymentGateway/StripePaymentGateway directly.
 */
@Component
public class PaymentGatewayFactory {

    private final Map<GatewayType, PaymentGateway> gatewaysByType;
    private final PaymentGatewayConfigRepository configRepository;

    /**
     * Spring automatically collects EVERY bean implementing PaymentGateway into
     * this List when this constructor runs (constructor injection of a List<Interface>
     * is a standard Spring idiom for exactly this "gather all implementations"
     * pattern) — adding a new gateway class anywhere in the codebase means it
     * appears here automatically, with zero changes to this factory itself.
     */
    public PaymentGatewayFactory(List<PaymentGateway> gateways, PaymentGatewayConfigRepository configRepository) {
        this.gatewaysByType = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::getType, Function.identity()));
        this.configRepository = configRepository;
    }

    /**
     * Returns the gateway implementation for the given type — but ONLY if that
     * gateway is currently enabled per the database config (payment_gateways
     * table). This is the actual enforcement point for roadmap doc's "gated by a
     * DB-backed is_enabled flag" requirement — StripePaymentGateway fully exists
     * as working code and a registered bean, yet remains completely unreachable
     * through this factory while its DB row says is_enabled = false.
     */
    public PaymentGateway getGateway(GatewayType type) {
        boolean enabled = configRepository.findByName(type)
                .map(config -> config.isEnabled())
                .orElse(false); // fail closed: an unrecognized/missing config row
        // is treated as disabled, never as enabled —
        // a missing row should never accidentally open up
        // a payment path that was never explicitly turned on.

        if (!enabled) {
            throw new InvalidProductDataException(
                    "Payment gateway '" + type + "' is not currently available");
        }

        PaymentGateway gateway = gatewaysByType.get(type);
        if (gateway == null) {
            // A gateway is enabled in the DB config but has no corresponding
            // PaymentGateway bean registered — a genuine configuration/deployment
            // bug (someone enabled a gateway without deploying its code), not a
            // client-facing input error, hence IllegalStateException rather than
            // the business-exception types used above.
            throw new IllegalStateException(
                    "Gateway '" + type + "' is enabled in config but has no registered implementation");
        }

        return gateway;
    }
}