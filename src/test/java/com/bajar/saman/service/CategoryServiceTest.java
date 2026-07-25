package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.exception.CategoryNotFoundException;
import com.bajar.saman.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategory_withNoParent_createsRootCategoryWithGeneratedSlug() {
        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // echoes back
        // whatever Category was passed to save() — a common Mockito pattern
        // for repositories, since a real save() would return the
        // (now-persisted) entity, and our test needs that returned value to
        // make assertions against.

        Category result = categoryService.createCategory("Electronics", "Devices", null);

        assertThat(result.getName()).isEqualTo("Electronics");
        assertThat(result.getSlug()).isEqualTo("electronics");
        assertThat(result.getParent()).isNull();

        // Confirms the parent lookup was never even attempted — parentId was null,
        // so there's no ID to look up.
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void createCategory_withValidParent_linksToParentCorrectly() {
        UUID parentId = UUID.randomUUID();
        Category parentCategory = new Category("Electronics", "electronics", null);

        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.findBySlug("mobile-phones")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category result = categoryService.createCategory("Mobile Phones", "Phones", parentId);

        assertThat(result.getParent()).isEqualTo(parentCategory);
    }

    @Test
    void createCategory_withNonExistentParentId_throwsCategoryNotFoundException() {
        UUID fakeParentId = UUID.randomUUID();
        when(categoryRepository.findById(fakeParentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                categoryService.createCategory("Mobile Phones", "Phones", fakeParentId)
        ).isInstanceOf(CategoryNotFoundException.class);

        // Fails BEFORE any slug generation or save attempt — confirms the
        // fail-fast ordering (validate parent exists first) actually holds.
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_withDuplicateName_appendsNumericSuffixToSlug() {
        // Simulates "electronics" already being taken, but "electronics-2" free —
        // directly tests the generateUniqueSlug loop discussed when CategoryService
        // was built.
        when(categoryRepository.findBySlug("electronics")).thenReturn(
                Optional.of(new Category("Electronics", "electronics", null)));
        when(categoryRepository.findBySlug("electronics-2")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category result = categoryService.createCategory("Electronics", "Devices again", null);

        assertThat(result.getSlug()).isEqualTo("electronics-2");
    }

    @Test
    void createCategory_withTwoExistingDuplicates_incrementsToNextFreeSuffix() {
        // Both "electronics" AND "electronics-2" taken -> must land on "electronics-3".
        // Confirms the loop keeps advancing past a SINGLE collision, not just one.
        when(categoryRepository.findBySlug("electronics")).thenReturn(
                Optional.of(new Category("Electronics", "electronics", null)));
        when(categoryRepository.findBySlug("electronics-2")).thenReturn(
                Optional.of(new Category("Electronics", "electronics-2", null)));
        when(categoryRepository.findBySlug("electronics-3")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category result = categoryService.createCategory("Electronics", "Third one", null);

        assertThat(result.getSlug()).isEqualTo("electronics-3");
    }

    @Test
    void getCategoryBySlug_withExistingSlug_returnsCategory() {
        Category category = new Category("Electronics", "electronics", null);
        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryBySlug("electronics");

        assertThat(result.getName()).isEqualTo("Electronics");
    }

    @Test
    void getCategoryBySlug_withNonExistentSlug_throwsCategoryNotFoundException() {
        when(categoryRepository.findBySlug("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryBySlug("does-not-exist"))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void getSubcategories_delegatesToRepositoryWithCorrectParentId() {
        UUID parentId = UUID.randomUUID();
        Category child = new Category("Mobile Phones", "mobile-phones", null);
        when(categoryRepository.findByParentId(parentId)).thenReturn(List.of(child));

        List<Category> result = categoryService.getSubcategories(parentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Mobile Phones");
    }
}