package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.SellerStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.CategoryNotFoundException;
import com.bajar.saman.exception.DuplicateSkuException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.repository.CategoryRepository;
import com.bajar.saman.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductAuthorizationPolicy authorizationPolicy;

    @InjectMocks
    private ProductService productService;

    private Category testCategory;
    private User admin;

    @BeforeEach
    void setUpActor() {
        admin = new User("admin@saman.test", "hash");
        lenient().when(authorizationPolicy.isAdmin(admin)).thenReturn(true);
    }

    @Test
    void createProduct_withValidData_savesAndReturnsProduct() {
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        UUID categoryId = UUID.randomUUID();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productRepository.findBySlug("iphone-17")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(admin,
                categoryId, "iPhone 17", "Latest phone",
                new BigDecimal("999.99"), "SKU-001", 50);

        assertThat(result.getName()).isEqualTo("iPhone 17");
        assertThat(result.getSlug()).isEqualTo("iphone-17");
        assertThat(result.getSku()).isEqualTo("SKU-001");
        assertThat(result.getCategory()).isEqualTo(testCategory);
    }

    @Test
    void createProduct_withZeroPrice_throwsInvalidProductDataException_beforeAnyDbCall() {
        assertThatThrownBy(() ->
                productService.createProduct(admin,
                        UUID.randomUUID(), "Free Item", "desc",
                        BigDecimal.ZERO, "SKU-002", 10)
        ).isInstanceOf(InvalidProductDataException.class);

        // Validates BEFORE touching category/product repositories at all — same
        // fail-fast-and-cheap pattern established throughout this project
        // (existsByEmail-before-hashing in UserRegistrationService, etc.).
        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(productRepository);
    }

    @Test
    void createProduct_withNegativePrice_throwsInvalidProductDataException() {
        assertThatThrownBy(() ->
                productService.createProduct(admin,
                        UUID.randomUUID(), "Item", "desc",
                        new BigDecimal("-5.00"), "SKU-003", 10)
        ).isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void createProduct_withNegativeStock_throwsInvalidProductDataException() {
        assertThatThrownBy(() ->
                productService.createProduct(admin,
                        UUID.randomUUID(), "Item", "desc",
                        new BigDecimal("10.00"), "SKU-004", -1)
        ).isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void createProduct_withNonExistentCategory_throwsCategoryNotFoundException() {
        UUID fakeCategoryId = UUID.randomUUID();
        when(categoryRepository.findById(fakeCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.createProduct(admin,
                        fakeCategoryId, "Item", "desc",
                        new BigDecimal("10.00"), "SKU-005", 10)
        ).isInstanceOf(CategoryNotFoundException.class);

        // SKU uniqueness should never even be checked if the category doesn't exist.
        verify(productRepository, never()).existsBySku(any());
    }

    @Test
    void createProduct_withDuplicateSku_throwsDuplicateSkuException() {
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        UUID categoryId = UUID.randomUUID();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku("DUPLICATE-SKU")).thenReturn(true);

        assertThatThrownBy(() ->
                productService.createProduct(admin,
                        categoryId, "Item", "desc",
                        new BigDecimal("10.00"), "DUPLICATE-SKU", 10)
        ).isInstanceOf(DuplicateSkuException.class);

        // Confirms slug generation/save never happens once SKU is known to conflict.
        verify(productRepository, never()).save(any());
    }

    @Test
    void updatePrice_withValidPrice_updatesEntityViaDirtyChecking() {
        UUID productId = UUID.randomUUID();
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        Product product = new Product(
                testCategory, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product result = productService.updatePrice(admin, productId, new BigDecimal("899.99"));

        assertThat(result.getPrice()).isEqualByComparingTo("899.99");

        // Critical assertion: save() is intentionally NEVER called here — this
        // directly tests the dirty-checking behavior explained when updatePrice()
        // was built. If a future refactor accidentally added an explicit save()
        // call (harmless but redundant) or, worse, broke dirty-checking somehow
        // (e.g. by loading the entity in a different transaction), this exact
        // assertion is what would catch a REGRESSION in that specific mechanism.
        verify(productRepository, never()).save(any());
    }

    @Test
    void updatePrice_withInvalidPrice_throwsBeforeLookingUpProduct() {
        assertThatThrownBy(() ->
                productService.updatePrice(admin, UUID.randomUUID(), BigDecimal.ZERO)
        ).isInstanceOf(InvalidProductDataException.class);

        verify(productRepository, never()).findById(any());
    }

    @Test
    void updatePrice_withNonExistentProduct_throwsProductNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        when(productRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.updatePrice(admin, fakeId, new BigDecimal("10.00"))
        ).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void adjustStock_withPositiveDelta_increasesStock() {
        UUID productId = UUID.randomUUID();
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        Product product = new Product(
                testCategory, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product result = productService.adjustStock(productId, 10);

        assertThat(result.getStockQuantity()).isEqualTo(60);
    }

    @Test
    void adjustStock_withDeltaCausingNegativeStock_throwsInvalidProductDataException() {
        UUID productId = UUID.randomUUID();
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        // Only 5 in stock — a delta of -10 would push it to -5, which must be rejected.
        Product product = new Product(
                testCategory, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 5);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adjustStock(productId, -10))
                .isInstanceOf(InvalidProductDataException.class);

        // Confirms the entity was NOT mutated when validation fails partway through
        // — stock should still read its original value, not some intermediate
        // (invalid) computed value.
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void approvedSellerCreatesProductOwnedBySelf() {
        User seller = seller(SellerStatus.APPROVED);
        UUID categoryId = UUID.randomUUID();
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        when(authorizationPolicy.isAdmin(seller)).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku("SELLER-SKU")).thenReturn(false);
        when(productRepository.findBySlug("seller-phone")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product product = productService.createProduct(seller, categoryId,
                "Seller Phone", "Owned listing", new BigDecimal("500.00"),
                "SELLER-SKU", 4);

        assertThat(product.getSeller()).isSameAs(seller);
    }

    @Test
    void pendingSellerCannotCreateProduct() {
        User seller = seller(SellerStatus.PENDING_APPROVAL);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorizationPolicy).requireCanCreate(seller);

        assertThatThrownBy(() -> productService.createProduct(seller, UUID.randomUUID(),
                "Blocked", "Pending seller", BigDecimal.TEN, "BLOCKED-SKU", 1))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(productRepository);
    }

    @Test
    void approvedSellerCanUpdateOwnProductPrice() {
        User seller = seller(SellerStatus.APPROVED);
        Product product = new Product(new Category("Phones", "phones", null),
                "Own Phone", "own-phone", new BigDecimal("100.00"), "OWN-SKU", 2);
        product.setSeller(seller);
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product updated = productService.updatePrice(seller, productId, new BigDecimal("90.00"));

        assertThat(updated.getPrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void approvedSellerCannotUpdateAnotherSellersProduct() {
        User owner = seller(SellerStatus.APPROVED);
        User otherSeller = seller(SellerStatus.APPROVED);
        Product product = new Product(new Category("Phones", "phones", null),
                "Other Phone", "other-phone", new BigDecimal("100.00"), "OTHER-SKU", 2);
        product.setSeller(owner);
        UUID productId = UUID.randomUUID();
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorizationPolicy).requireCanManage(otherSeller, product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updatePrice(
                otherSeller, productId, new BigDecimal("1.00")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(product.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void listOwnedProductsUsesAuthenticatedSellerId() {
        User seller = seller(SellerStatus.APPROVED);
        PageRequest pageable = PageRequest.of(0, 20);
        when(productRepository.findBySellerId(seller.getId(), pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        productService.listOwnedProducts(seller, pageable);

        verify(authorizationPolicy).requireApprovedSeller(seller);
        verify(productRepository).findBySellerId(seller.getId(), pageable);
    }

    @Test
    void ownerCanFullyUpdateProductWhileSkuRemainsStable() {
        User seller = seller(SellerStatus.APPROVED);
        Category oldCategory = new Category("Old", "old", null);
        Category newCategory = new Category("New", "new", null);
        Product product = new Product(oldCategory, "Old Name", "old-name",
                new BigDecimal("100.00"), "STABLE-SKU", 2);
        product.setSeller(seller);
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(newCategory));
        when(productRepository.findBySlug("new-name")).thenReturn(Optional.empty());

        Product updated = productService.updateProduct(seller, productId, categoryId,
                "New Name", "New description", new BigDecimal("80.00"), 9);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getSlug()).isEqualTo("new-name");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getPrice()).isEqualByComparingTo("80.00");
        assertThat(updated.getStockQuantity()).isEqualTo(9);
        assertThat(updated.getCategory()).isSameAs(newCategory);
        assertThat(updated.getSku()).isEqualTo("STABLE-SKU");
        verify(authorizationPolicy).requireCanManage(seller, product);
    }

    @Test
    void deactivateProductPerformsSoftDelete() {
        User seller = seller(SellerStatus.APPROVED);
        Product product = new Product(new Category("Phones", "phones", null),
                "Phone", "phone", BigDecimal.TEN, "SOFT-DELETE-SKU", 1);
        product.setSeller(seller);
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.deactivateProduct(seller, productId);

        assertThat(product.isActive()).isFalse();
        verify(authorizationPolicy).requireCanManage(seller, product);
        verify(productRepository, never()).delete(any());
    }

    private User seller(SellerStatus status) {
        User seller = new User(UUID.randomUUID() + "@saman.test", "hash");
        ReflectionTestUtils.setField(seller, "id", UUID.randomUUID());
        seller.setSellerStatus(status);
        return seller;
    }
}
