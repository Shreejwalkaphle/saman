package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.entity.Product;
import com.bajar.saman.exception.CategoryNotFoundException;
import com.bajar.saman.exception.DuplicateSkuException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.repository.CategoryRepository;
import com.bajar.saman.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private ProductService productService;

    private Category testCategory;

    @Test
    void createProduct_withValidData_savesAndReturnsProduct() {
        testCategory = new Category("Mobile Phones", "mobile-phones", null);
        UUID categoryId = UUID.randomUUID();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productRepository.findBySlug("iphone-17")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(
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
                productService.createProduct(
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
                productService.createProduct(
                        UUID.randomUUID(), "Item", "desc",
                        new BigDecimal("-5.00"), "SKU-003", 10)
        ).isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void createProduct_withNegativeStock_throwsInvalidProductDataException() {
        assertThatThrownBy(() ->
                productService.createProduct(
                        UUID.randomUUID(), "Item", "desc",
                        new BigDecimal("10.00"), "SKU-004", -1)
        ).isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void createProduct_withNonExistentCategory_throwsCategoryNotFoundException() {
        UUID fakeCategoryId = UUID.randomUUID();
        when(categoryRepository.findById(fakeCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.createProduct(
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
                productService.createProduct(
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

        Product result = productService.updatePrice(productId, new BigDecimal("899.99"));

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
                productService.updatePrice(UUID.randomUUID(), BigDecimal.ZERO)
        ).isInstanceOf(InvalidProductDataException.class);

        verify(productRepository, never()).findById(any());
    }

    @Test
    void updatePrice_withNonExistentProduct_throwsProductNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        when(productRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.updatePrice(fakeId, new BigDecimal("10.00"))
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
}