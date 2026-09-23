package com.bajar.saman.service;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.PaymentNotFoundException;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.service.payment.EsewaPaymentGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class EsewaPaymentCompletionService {

    private final EsewaPaymentGateway esewaGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public EsewaPaymentCompletionService(
            EsewaPaymentGateway esewaGateway,
            PaymentRepository paymentRepository,
            PaymentService paymentService) {
        this.esewaGateway = esewaGateway;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public Payment complete(User user, String encodedData) {
        EsewaPaymentGateway.VerifiedCallback callback;
        try {
            callback = esewaGateway.verifyCallback(encodedData);
        } catch (IllegalArgumentException ex) {
            throw new InvalidProductDataException(ex.getMessage());
        }

        Payment payment = paymentRepository
                .findByGatewayAndGatewayReference(GatewayType.ESEWA, callback.transactionUuid())
                .orElseThrow(() -> new PaymentNotFoundException(callback.transactionUuid()));

        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new PaymentNotFoundException(callback.transactionUuid());
        }
        if (!"COMPLETE".equalsIgnoreCase(callback.status())) {
            throw new InvalidProductDataException("eSewa callback is not complete");
        }

        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(callback.totalAmount().replace(",", ""));
        } catch (RuntimeException ex) {
            throw new InvalidProductDataException("Invalid eSewa callback amount");
        }
        if (callbackAmount.compareTo(payment.getAmount()) != 0) {
            throw new InvalidProductDataException("eSewa callback amount does not match payment");
        }

        // PaymentService performs the independent server-to-server status check.
        // The signed browser callback alone is never enough to mark an order PAID.
        return paymentService.confirmPayment(user, payment.getId());
    }
}
