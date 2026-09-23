package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.repository.ShopMemberRepository;
import com.bajar.saman.repository.UserRoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProductAuthorizationPolicy {
    private static final List<ShopMemberRole> PRODUCT_MANAGERS =
            List.of(ShopMemberRole.OWNER, ShopMemberRole.MANAGER);
    private final UserRoleRepository userRoleRepository;
    private final ShopMemberRepository memberRepository;

    public ProductAuthorizationPolicy(UserRoleRepository userRoleRepository,
                                      ShopMemberRepository memberRepository) {
        this.userRoleRepository = userRoleRepository;
        this.memberRepository = memberRepository;
    }
    public boolean isAdmin(User actor) {
        return actor != null && userRoleRepository.findRoleNamesByUserId(actor.getId()).contains("ADMIN");
    }
    public void requireCanCreate(User actor, Shop shop) {
        requireActiveShop(shop);
        if (!isAdmin(actor) && !canManage(actor, shop)) throw denied();
    }
    public void requireCanManage(User actor, Product product) {
        if (isAdmin(actor)) return;
        requireActiveShop(product.getShop());
        if (!canManage(actor, product.getShop())) throw denied();
    }
    public void requireActiveMember(User actor, Shop shop) {
        if (isAdmin(actor)) return;
        if (actor == null || memberRepository.findByShopIdAndUserIdAndActiveTrue(
                shop.getId(), actor.getId()).isEmpty()) throw denied();
    }
    private boolean canManage(User actor, Shop shop) {
        return actor != null && memberRepository.existsByShopIdAndUserIdAndRoleInAndActiveTrue(
                shop.getId(), actor.getId(), PRODUCT_MANAGERS);
    }
    private void requireActiveShop(Shop shop) {
        if (shop.getStatus() != ShopStatus.ACTIVE) throw new AccessDeniedException("Shop is not active");
    }
    private AccessDeniedException denied() {
        return new AccessDeniedException("You do not have permission to manage products for this shop");
    }
}
