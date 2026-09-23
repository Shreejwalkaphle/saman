package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.PaymentGatewayConfig;
import com.bajar.saman.repository.PaymentGatewayConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayFactoryTest {

    @Mock
    private PaymentGatewayConfigRepository configRepository;

    private PaymentGatewayConfig buildConfig(GatewayType type, boolean enabled) {
        // PaymentGatewayConfig has no public constructor (deliberately — see its
        // class comment, only ever created via migration seed data in real usage).
        // Reflection is used here purely for test setup, same accepted pattern as
        // the User-ID-setting helper in earlier test classes.
        PaymentGatewayConfig config = new PaymentGatewayConfig() {};
        try {
            var nameField = PaymentGatewayConfig.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(config, type);
            var enabledField = PaymentGatewayConfig.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(config, enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return config;
    }

    @Test
    void getGateway_forEnabledGateway_returnsCorrectImplementation() {
        when(configRepository.findByName(GatewayType.ESEWA))
                .thenReturn(Optional.of(buildConfig(GatewayType.ESEWA, true)));

        PaymentGatewayFactory factory = new PaymentGatewayFactory(
                List.of(new EsewaPaymentGateway(
                                RestClient.builder(),
                                "https://example.test/esewa/form",
                                "https://example.test/esewa/status",
                                "EPAYTEST", "test-secret",
                                "http://localhost/success", "http://localhost/failure"),
                        new KhaltiPaymentGateway()), configRepository);

        PaymentGateway result = factory.getGateway(GatewayType.ESEWA);

        assertThat(result.getType()).isEqualTo(GatewayType.ESEWA);
        assertThat(result).isInstanceOf(EsewaPaymentGateway.class);
    }

    @Test
    void getGateway_forDisabledGateway_throwsInvalidProductDataException() {
        // THE test that directly proves roadmap doc's dormant-Stripe requirement
        // actually works — a fully-registered, working StripePaymentGateway bean
        // exists and is passed into the factory below, yet remains completely
        // unreachable while its config says disabled.
        when(configRepository.findByName(GatewayType.STRIPE))
                .thenReturn(Optional.of(buildConfig(GatewayType.STRIPE, false)));

        PaymentGatewayFactory factory = new PaymentGatewayFactory(
                List.of(new StripePaymentGateway()), configRepository);

        assertThatThrownBy(() -> factory.getGateway(GatewayType.STRIPE))
                .isInstanceOf(com.bajar.saman.exception.InvalidProductDataException.class)
                .hasMessageContaining("STRIPE");
    }

    @Test
    void getGateway_withNoConfigRowAtAll_failsClosedNotOpen() {
        // THE fail-closed test — directly verifies the security-critical default
        // discussed when PaymentGatewayFactory was built: a MISSING config row
        // must never be silently treated as enabled.
        when(configRepository.findByName(GatewayType.KHALTI)).thenReturn(Optional.empty());

        PaymentGatewayFactory factory = new PaymentGatewayFactory(
                List.of(new KhaltiPaymentGateway()), configRepository);

        assertThatThrownBy(() -> factory.getGateway(GatewayType.KHALTI))
                .isInstanceOf(com.bajar.saman.exception.InvalidProductDataException.class);
    }

    @Test
    void getGateway_enabledInConfigButNoImplementationRegistered_throwsIllegalStateException() {
        // Simulates a genuine deployment misconfiguration: ESEWA is marked
        // enabled in the DB, but NO EsewaPaymentGateway bean was passed to the
        // factory (as if the code was never deployed). This must be
        // distinguishable from a normal "gateway not available" business
        // rejection — it's a configuration bug, not a client input problem.
        when(configRepository.findByName(GatewayType.ESEWA))
                .thenReturn(Optional.of(buildConfig(GatewayType.ESEWA, true)));

        PaymentGatewayFactory factory = new PaymentGatewayFactory(
                List.of(), configRepository); // no implementations registered at all

        assertThatThrownBy(() -> factory.getGateway(GatewayType.ESEWA))
                .isInstanceOf(IllegalStateException.class);
    }
}
