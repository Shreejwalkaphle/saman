package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.repository.ShopMemberRepository;
import com.bajar.saman.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAuthorizationPolicyTest {
    @Mock UserRoleRepository roles;
    @Mock ShopMemberRepository members;

    @Test void activeOwnerCanCreateAndManage() {
        User owner = user(); Shop shop = activeShop(); Product product = product(shop);
        when(roles.findRoleNamesByUserId(owner.getId())).thenReturn(List.of("CUSTOMER"));
        when(members.existsByShopIdAndUserIdAndRoleInAndActiveTrue(
                eq(shop.getId()), eq(owner.getId()), any())).thenReturn(true);
        ProductAuthorizationPolicy policy = new ProductAuthorizationPolicy(roles, members);
        assertThatCode(() -> policy.requireCanCreate(owner, shop)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanManage(owner, product)).doesNotThrowAnyException();
    }

    @Test void pickerCannotManageProducts() {
        User picker = user(); Shop shop = activeShop();
        when(roles.findRoleNamesByUserId(picker.getId())).thenReturn(List.of("CUSTOMER"));
        when(members.existsByShopIdAndUserIdAndRoleInAndActiveTrue(any(), any(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> new ProductAuthorizationPolicy(roles, members)
                .requireCanCreate(picker, shop)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void suspendedShopIsRejectedBeforeActorAuthorization() {
        User admin = user(); Shop shop = shop();
        ReflectionTestUtils.setField(shop, "status", ShopStatus.SUSPENDED);
        assertThatThrownBy(() -> new ProductAuthorizationPolicy(roles, members)
                .requireCanCreate(admin, shop)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles, members);
    }

    private User user() { User u = new User(UUID.randomUUID()+"@test", "hash"); ReflectionTestUtils.setField(u,"id",UUID.randomUUID()); return u; }
    private Shop shop() { Shop s = new Shop("Gudri Shop","gudri-shop",UUID.randomUUID(),"9800000000","Gudri","Biratnagar","Morang",new BigDecimal("26.4525"),new BigDecimal("87.2718")); ReflectionTestUtils.setField(s,"id",UUID.randomUUID()); return s; }
    private Shop activeShop() { Shop s=shop(); s.approve(); return s; }
    private Product product(Shop s) { Product p=new Product(null,"Rice","rice",BigDecimal.TEN,"RICE-1",10); p.setShop(s); return p; }
}
