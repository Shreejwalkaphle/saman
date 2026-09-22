package com.bajar.saman.service;

import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.SellerStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.repository.UserRoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Central ownership policy reused by every seller product mutation. */
@Component
public class ProductAuthorizationPolicy {

    private final UserRoleRepository userRoleRepository;

    public ProductAuthorizationPolicy(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    public boolean isAdmin(User actor) {
        return actor != null && userRoleRepository.findRoleNamesByUserId(actor.getId()).contains("ADMIN");
    }

    public void requireCanCreate(User actor) {
        if (!isAdmin(actor) && (actor == null || actor.getSellerStatus() != SellerStatus.APPROVED)) {
            throw denied();
        }
    }

    public void requireApprovedSeller(User actor) {
        if (actor == null || actor.getSellerStatus() != SellerStatus.APPROVED) {
            throw denied();
        }
    }

    public void requireCanManage(User actor, Product product) {
        if (isAdmin(actor)) {
            return;
        }
        boolean approvedOwner = actor != null
                && actor.getSellerStatus() == SellerStatus.APPROVED
                && product.getSeller() != null
                && product.getSeller().getId().equals(actor.getId());
        if (!approvedOwner) {
            throw denied();
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("You do not have permission to manage this product");
    }
}
