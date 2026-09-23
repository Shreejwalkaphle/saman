package com.bajar.saman.controller;

import com.bajar.saman.config.AdminBootstrapConfig;
import com.bajar.saman.entity.Category;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.SellerStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.service.ProductService;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProxyManager<byte[]> rateLimitProxyManager;

    @MockitoBean
    private AdminBootstrapConfig adminBootstrapConfig;

    @Test
    void pendingSellerReceivesForbiddenWhenListingOwnProducts() throws Exception {
        User pendingSeller = user(SellerStatus.PENDING_APPROVAL);
        when(productService.listOwnedProducts(eq(pendingSeller), any()))
                .thenThrow(new AccessDeniedException("Seller approval is required"));

        mockMvc.perform(get("/api/products/mine")
                        .with(authentication(authToken(pendingSeller, "SELLER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to perform this action"));
    }

    @Test
    void approvedOwnerCanUpdateOwnProduct() throws Exception {
        User owner = user(SellerStatus.APPROVED);
        Product product = productOwnedBy(owner);
        UUID productId = product.getId();
        when(productService.updateProduct(eq(owner), eq(productId), any(), any(), any(), any(), anyInt()))
                .thenReturn(product);

        mockMvc.perform(put("/api/products/{id}", productId)
                        .with(authentication(authToken(owner, "SELLER")))
                        .contentType("application/json")
                        .content(updateBody(product.getCategory().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.sellerId").value(owner.getId().toString()));
    }

    @Test
    void foreignSellerReceivesForbiddenWhenDeactivatingProduct() throws Exception {
        User seller = user(SellerStatus.APPROVED);
        UUID productId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Only the owner can manage this product"))
                .when(productService).deactivateProduct(seller, productId);

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .with(authentication(authToken(seller, "SELLER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to perform this action"));
    }

    @Test
    void adminCanUpdateSellerOwnedProduct() throws Exception {
        User admin = user(null);
        Product product = productOwnedBy(user(SellerStatus.APPROVED));
        UUID productId = product.getId();
        when(productService.updateProduct(eq(admin), eq(productId), any(), any(), any(), any(), anyInt()))
                .thenReturn(product);

        mockMvc.perform(put("/api/products/{id}", productId)
                        .with(authentication(authToken(admin, "ADMIN")))
                        .contentType("application/json")
                        .content(updateBody(product.getCategory().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()));
    }

    @Test
    void inactiveProductSlugIsNotExposedByPublicEndpoint() throws Exception {
        when(productService.getProductBySlug("inactive-product"))
                .thenThrow(new ProductNotFoundException("inactive-product"));

        mockMvc.perform(get("/api/products/inactive-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: inactive-product"));
    }

    private UsernamePasswordAuthenticationToken authToken(User user, String role) {
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private User user(SellerStatus sellerStatus) {
        User user = new User(UUID.randomUUID() + "@saman.test", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setSellerStatus(sellerStatus);
        return user;
    }

    private Product productOwnedBy(User owner) {
        Category category = new Category("Phones", "phones", null);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        Product product = new Product(category, "Seller Phone", "seller-phone",
                new BigDecimal("499.00"), "SELLER-PHONE-001", 5);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setSeller(owner);
        return product;
    }

    private String updateBody(UUID categoryId) {
        return """
                {
                  "categoryId": "%s",
                  "name": "Seller Phone",
                  "description": "Updated listing",
                  "price": 499.00,
                  "stockQuantity": 5
                }
                """.formatted(categoryId);
    }
}
