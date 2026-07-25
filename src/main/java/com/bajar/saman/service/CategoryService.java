package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.exception.CategoryNotFoundException;
import com.bajar.saman.repository.CategoryRepository;
import com.bajar.saman.util.SlugGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category createCategory(String name, String description, UUID parentId) {

        // Resolve the parent FIRST, before generating anything — if the caller
        // passed a parentId that doesn't actually exist, we want to fail fast and
        // clearly, rather than partway through category creation.
        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new CategoryNotFoundException(parentId.toString()));
        }

        String uniqueSlug = generateUniqueSlug(name);

        Category category = new Category(name, uniqueSlug, parent);
        category.setDescription(description);

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
    }

    @Transactional(readOnly = true)
    public List<Category> getRootCategories() {
        return categoryRepository.findByParentIsNull();
    }

    @Transactional(readOnly = true)
    public List<Category> getSubcategories(UUID parentId) {
        return categoryRepository.findByParentId(parentId);
    }

    /**
     * Generates a slug from the given name, and if that exact slug is already
     * taken, appends "-2", "-3", etc. until a free one is found. Kept private —
     * this is an internal implementation detail of "how createCategory avoids
     * UNIQUE constraint violations," not something any other class needs to call
     * directly.
     */
    private String generateUniqueSlug(String name) {
        String baseSlug = SlugGenerator.generate(name);
        String candidateSlug = baseSlug;
        int suffix = 2;

        // Loop instead of a single check-then-insert: handles the case where
        // MULTIPLE prior collisions already exist (e.g. "sale", "sale-2", "sale-3"
        // all taken — this correctly lands on "sale-4"), not just a single
        // collision. Bounded by the fact that each iteration checks a DIFFERENT
        // candidate string, so it always terminates once a free slug is found.
        while (categoryRepository.findBySlug(candidateSlug).isPresent()) {
            candidateSlug = baseSlug + "-" + suffix;
            suffix++;
        }

        return candidateSlug;
    }
}