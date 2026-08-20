package com.bajar.saman.service;

import com.bajar.saman.entity.SellerStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.UserNotFoundException;
import com.bajar.saman.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Roadmap Addendum v2 §1.4: the "checker" half of the seller maker-checker
 * workflow — an ADMIN reviews and approves/rejects pending seller
 * applications. Controller-level @PreAuthorize("hasRole('ADMIN')") is the
 * actual enforcement (same established pattern as Catalog mutation, Order
 * shipping) — this service assumes it's only ever called by an authorized
 * caller, consistent with how every other service in this project separates
 * "is this allowed" (controller) from "what does this business action do"
 * (service).
 */
@Service
public class RoleManagementService {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementService.class);

    private final UserRepository userRepository;

    public RoleManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> getPendingSellerApplications() {
        return userRepository.findBySellerStatus(SellerStatus.PENDING_APPROVAL);
    }

    @Transactional
    public User approveSeller(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (user.getSellerStatus() != SellerStatus.PENDING_APPROVAL) {
            throw new InvalidProductDataException(
                    "User is not currently pending seller approval (status: " + user.getSellerStatus() + ")");
        }

        user.setSellerStatus(SellerStatus.APPROVED);
        // Audit logging — same principle established for Payment (roadmap
        // doc Section 7's "audit logging for sensitive actions"), applied
        // here since granting seller capability is exactly that kind of
        // sensitive action.
        log.info("Seller application APPROVED for userId={}", userId);
        return user;
    }

    @Transactional
    public User rejectSeller(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (user.getSellerStatus() != SellerStatus.PENDING_APPROVAL) {
            throw new InvalidProductDataException(
                    "User is not currently pending seller approval (status: " + user.getSellerStatus() + ")");
        }

        user.setSellerStatus(SellerStatus.REJECTED);
        log.info("Seller application REJECTED for userId={}", userId);
        return user;
    }
}