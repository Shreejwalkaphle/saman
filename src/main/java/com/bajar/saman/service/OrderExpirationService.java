package com.bajar.saman.service;

import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.OrderItem;
import com.bajar.saman.entity.OrderStatus;
import com.bajar.saman.entity.PaymentStatus;
import com.bajar.saman.entity.Product;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    public OrderExpirationService(OrderRepository orderRepository,
                                  PaymentRepository paymentRepository,
                                  ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
    }

    /**
     * Cancels one abandoned order and restores its reserved inventory atomically.
     * The order row and every product row are locked so payment initiation,
     * checkout and a duplicate expiry run cannot interleave with restoration.
     */
    @Transactional
    public boolean expireIfAbandoned(UUID orderId) {
        Order order = orderRepository.findByIdForPaymentOrExpiry(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        boolean hasNonFailedPayment = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .anyMatch(payment -> payment.getStatus() != PaymentStatus.FAILED);
        if (hasNonFailedPayment) {
            return false;
        }

        var items = new java.util.ArrayList<>(order.getItems());
        items.sort(java.util.Comparator.comparing(item -> item.getProduct().getId()));
        for (OrderItem item : items) {
            Product product = productRepository.findByIdForCheckout(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot restore stock for missing product " + item.getProduct().getId()));
            product.setStockQuantity(Math.addExact(product.getStockQuantity(), item.getQuantity()));
        }
        order.setStatus(OrderStatus.CANCELLED);
        return true;
    }
}
