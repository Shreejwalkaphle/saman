package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import tools.jackson.databind.json.JsonMapper;

/** Official eSewa ePay v2 UAT/production adapter. */
@Component
public class EsewaPaymentGateway implements PaymentGateway {

    private static final String SIGNED_FIELDS =
            "total_amount,transaction_uuid,product_code";
    private static final String CALLBACK_SIGNED_FIELDS =
            "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names";
    private static final Set<String> TERMINAL_FAILURES =
            Set.of("NOT_FOUND", "CANCELED");

    private final RestClient restClient;
    private final String formUrl;
    private final String statusUrl;
    private final String productCode;
    private final String secretKey;
    private final String successUrl;
    private final String failureUrl;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public EsewaPaymentGateway(
            RestClient.Builder restClientBuilder,
            @Value("${app.payment.esewa.form-url}") String formUrl,
            @Value("${app.payment.esewa.status-url}") String statusUrl,
            @Value("${app.payment.esewa.product-code}") String productCode,
            @Value("${app.payment.esewa.secret-key}") String secretKey,
            @Value("${app.payment.esewa.success-url}") String successUrl,
            @Value("${app.payment.esewa.failure-url}") String failureUrl) {
        this.restClient = restClientBuilder.build();
        this.formUrl = requireConfig(formUrl, "form-url");
        this.statusUrl = requireConfig(statusUrl, "status-url");
        this.productCode = requireConfig(productCode, "product-code");
        this.secretKey = requireConfig(secretKey, "secret-key");
        this.successUrl = requireConfig(successUrl, "success-url");
        this.failureUrl = requireConfig(failureUrl, "failure-url");
    }

    @Override
    public GatewayType getType() {
        return GatewayType.ESEWA;
    }

    @Override
    public PaymentInitiationResult initiate(Order order, UUID idempotencyKey) {
        String amount = formatAmount(order.getTotalAmount());
        String transactionUuid = idempotencyKey.toString();
        String message = signingMessage(amount, transactionUuid, productCode);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", amount);
        fields.put("tax_amount", "0");
        fields.put("total_amount", amount);
        fields.put("transaction_uuid", transactionUuid);
        fields.put("product_code", productCode);
        fields.put("product_service_charge", "0");
        fields.put("product_delivery_charge", "0");
        fields.put("success_url", successUrl);
        fields.put("failure_url", failureUrl);
        fields.put("signed_field_names", SIGNED_FIELDS);
        fields.put("signature", hmacSha256Base64(message, secretKey));

        return new PaymentInitiationResult(formUrl, "POST", fields, transactionUuid);
    }

    @Override
    public PaymentVerificationResult verify(String gatewayReference, BigDecimal expectedAmount) {
        EsewaStatusResponse response = restClient.get()
                .uri(statusUrl +
                                "?product_code={productCode}&total_amount={amount}&transaction_uuid={transactionUuid}",
                        productCode, formatAmount(expectedAmount), gatewayReference)
                .retrieve()
                .body(EsewaStatusResponse.class);

        if (response == null || response.status() == null) {
            throw new IllegalStateException("eSewa returned an empty status response");
        }
        if (!productCode.equals(response.productCode())
                || !gatewayReference.equals(response.transactionUuid())) {
            throw new IllegalStateException("eSewa status response identifiers did not match the payment");
        }

        String status = response.status().toUpperCase(Locale.ROOT);
        boolean success = "COMPLETE".equals(status);
        boolean terminal = success || TERMINAL_FAILURES.contains(status)
                || "FULL_REFUND".equals(status);

        return new PaymentVerificationResult(
                terminal, success, gatewayReference, status, response.totalAmount());
    }

    public VerifiedCallback verifyCallback(String encodedData) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedData);
            EsewaCallbackResponse callback = jsonMapper.readValue(decoded, EsewaCallbackResponse.class);

            if (!CALLBACK_SIGNED_FIELDS.equals(callback.signedFieldNames())
                    || !productCode.equals(callback.productCode())
                    || callback.signature() == null) {
                throw new IllegalArgumentException("Invalid eSewa callback fields");
            }

            String message = "transaction_code=" + callback.transactionCode()
                    + ",status=" + callback.status()
                    + ",total_amount=" + callback.totalAmount()
                    + ",transaction_uuid=" + callback.transactionUuid()
                    + ",product_code=" + callback.productCode()
                    + ",signed_field_names=" + callback.signedFieldNames();
            byte[] supplied = Base64.getDecoder().decode(callback.signature());
            byte[] expected = Base64.getDecoder().decode(hmacSha256Base64(message, secretKey));
            if (!MessageDigest.isEqual(supplied, expected)) {
                throw new IllegalArgumentException("Invalid eSewa callback signature");
            }

            return new VerifiedCallback(callback.transactionUuid(), callback.totalAmount(),
                    callback.status(), callback.transactionCode());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed eSewa callback", ex);
        }
    }

    static String signingMessage(String totalAmount, String transactionUuid,
                                 String productCode) {
        return "total_amount=" + totalAmount
                + ",transaction_uuid=" + transactionUuid
                + ",product_code=" + productCode;
    }

    static String hmacSha256Base64(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign eSewa payment request", ex);
        }
    }

    static String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String requireConfig(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing eSewa configuration: " + name);
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EsewaStatusResponse(
            @JsonAlias({"product_code", "scd"}) String productCode,
            @JsonAlias({"transaction_uuid", "pid"}) String transactionUuid,
            @JsonAlias({"total_amount", "totalAmount"}) BigDecimal totalAmount,
            String status,
            @JsonAlias({"ref_id", "refId"}) String referenceId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EsewaCallbackResponse(
            @JsonProperty("transaction_code") String transactionCode,
            String status,
            @JsonProperty("total_amount") String totalAmount,
            @JsonProperty("transaction_uuid") String transactionUuid,
            @JsonProperty("product_code") String productCode,
            @JsonProperty("signed_field_names") String signedFieldNames,
            String signature
    ) {
    }

    public record VerifiedCallback(
            String transactionUuid,
            String totalAmount,
            String status,
            String transactionCode
    ) {
    }
}
