package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.SellerStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAuthorizationPolicyTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Test
    void adminCanManageAnyProduct() {
        User admin = user(SellerStatus.REJECTED);
        when(userRoleRepository.findRoleNamesByUserId(admin.getId())).thenReturn(List.of("ADMIN"));
        ProductAuthorizationPolicy policy = new ProductAuthorizationPolicy(userRoleRepository);

        assertThatCode(() -> policy.requireCanManage(admin, productOwnedBy(user(SellerStatus.APPROVED))))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedOwnerCanManageOwnProduct() {
        User seller = user(SellerStatus.APPROVED);
        when(userRoleRepository.findRoleNamesByUserId(seller.getId())).thenReturn(List.of("SELLER"));
        ProductAuthorizationPolicy policy = new ProductAuthorizationPolicy(userRoleRepository);

        assertThatCode(() -> policy.requireCanManage(seller, productOwnedBy(seller)))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedSellerCannotManageForeignProduct() {
        User seller = user(SellerStatus.APPROVED);
        when(userRoleRepository.findRoleNamesByUserId(seller.getId())).thenReturn(List.of("SELLER"));
        ProductAuthorizationPolicy policy = new ProductAuthorizationPolicy(userRoleRepository);

        assertThatThrownBy(() -> policy.requireCanManage(
                seller, productOwnedBy(user(SellerStatus.APPROVED))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void pendingSellerCannotCreateOrListOwnedProducts() {
        User seller = user(SellerStatus.PENDING_APPROVAL);
        when(userRoleRepository.findRoleNamesByUserId(seller.getId())).thenReturn(List.of("SELLER"));
        ProductAuthorizationPolicy policy = new ProductAuthorizationPolicy(userRoleRepository);

        assertThatThrownBy(() -> policy.requireCanCreate(seller))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireApprovedSeller(seller))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private User user(SellerStatus status) {
        User user = new User(UUID.randomUUID() + "@saman.test", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setSellerStatus(status);
        return user;
    }

    private Product productOwnedBy(User seller) {
        Product product = new Product(new Category("Phones", "phones", null),
                "Phone", "phone", BigDecimal.TEN, UUID.randomUUID().toString(), 1);
        product.setSeller(seller);
        return product;
    }
}
