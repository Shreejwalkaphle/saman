package com.bajar.saman.service.payment;

import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EsewaPaymentGatewayTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private EsewaPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        gateway = new EsewaPaymentGateway(
                restClientBuilder,
                "https://example.test/esewa/form",
                "https://example.test/esewa/status",
                "EPAYTEST",
                "8gBm/:&EnhH.1/q",
                "http://localhost:4200/payment/esewa/success",
                "http://localhost:4200/payment/esewa/failure");
    }

    @Test
    void signatureMatchesOfficialEsewaUatFormDemo() {
        // eSewa's live documentation contains one inconsistent 100/11-201-13
        // sample. Its actual UAT form demo below that sample is reproducible
        // with the published secret, so this guards the working contract.
        String message = EsewaPaymentGateway.signingMessage("110", "241028", "EPAYTEST");

        assertThat(EsewaPaymentGateway.hmacSha256Base64(message, "8gBm/:&EnhH.1/q"))
                .isEqualTo("i94zsd3oXF6ZsSr/kGqT4sSzYQzjj1W/waxjWyRwaME=");
    }

    @Test
    void initiationProducesSignedPostFormWithoutExposingSecret() {
        UUID key = UUID.randomUUID();
        Order order = new Order(new User("customer@saman.test", "hash"),
                new BigDecimal("100.00"), UUID.randomUUID());

        PaymentGateway.PaymentInitiationResult result = gateway.initiate(order, key);

        assertThat(result.redirectUrl()).isEqualTo("https://example.test/esewa/form");
        assertThat(result.redirectMethod()).isEqualTo("POST");
        assertThat(result.gatewayReference()).isEqualTo(key.toString());
        assertThat(result.redirectFields())
                .containsEntry("total_amount", "100")
                .containsEntry("transaction_uuid", key.toString())
                .containsEntry("product_code", "EPAYTEST")
                .containsKey("signature")
                .doesNotContainKey("secret_key");
    }

    @Test
    void completeStatusIsTerminalSuccessWithGatewayAmount() {
        String reference = "d0f32c39-8547-4e2c-b8f9-c23c081284ef";
        server.expect(once(), requestTo(
                        "https://example.test/esewa/status?product_code=EPAYTEST&total_amount=100&transaction_uuid="
                                + reference))
                .andRespond(withSuccess("""
                        {"product_code":"EPAYTEST","transaction_uuid":"%s",
                         "total_amount":100.0,"status":"COMPLETE","ref_id":"0001TS9"}
                        """.formatted(reference), MediaType.APPLICATION_JSON));

        PaymentGateway.PaymentVerificationResult result =
                gateway.verify(reference, new BigDecimal("100.00"));

        assertThat(result.terminal()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.amountReceived()).isEqualByComparingTo("100.0");
        server.verify();
    }

    @Test
    void pendingStatusRemainsNonTerminal() {
        String reference = "pending-reference";
        server.expect(once(), requestTo(
                        "https://example.test/esewa/status?product_code=EPAYTEST&total_amount=100&transaction_uuid="
                                + reference))
                .andRespond(withSuccess("""
                        {"product_code":"EPAYTEST","transaction_uuid":"%s",
                         "total_amount":100.0,"status":"PENDING","ref_id":null}
                        """.formatted(reference), MediaType.APPLICATION_JSON));

        PaymentGateway.PaymentVerificationResult result =
                gateway.verify(reference, new BigDecimal("100.00"));

        assertThat(result.terminal()).isFalse();
        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void callbackRequiresValidSignatureAndReturnsSignedValues() {
        String signedFields = "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names";
        String message = "transaction_code=0001TS9,status=COMPLETE,total_amount=100.0,"
                + "transaction_uuid=callback-ref,product_code=EPAYTEST,signed_field_names="
                + signedFields;
        String signature = EsewaPaymentGateway.hmacSha256Base64(
                message, "8gBm/:&EnhH.1/q");
        String json = """
                {"transaction_code":"0001TS9","status":"COMPLETE","total_amount":"100.0",
                 "transaction_uuid":"callback-ref","product_code":"EPAYTEST",
                 "signed_field_names":"%s","signature":"%s"}
                """.formatted(signedFields, signature);
        String encoded = Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8));

        EsewaPaymentGateway.VerifiedCallback callback = gateway.verifyCallback(encoded);

        assertThat(callback.transactionUuid()).isEqualTo("callback-ref");
        assertThat(callback.totalAmount()).isEqualTo("100.0");
        assertThat(callback.status()).isEqualTo("COMPLETE");
    }

    @Test
    void callbackRejectsTamperedAmount() {
        String signedFields = "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names";
        String json = """
                {"transaction_code":"0001TS9","status":"COMPLETE","total_amount":"999.0",
                 "transaction_uuid":"callback-ref","product_code":"EPAYTEST",
                 "signed_field_names":"%s","signature":"invalid"}
                """.formatted(signedFields);
        String encoded = Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gateway.verifyCallback(encoded))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
