package com.bajar.saman.controller;

import com.bajar.saman.dto.CategoryResponse;
import com.bajar.saman.dto.CreateCategoryRequest;
import com.bajar.saman.entity.Category;
import com.bajar.saman.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.createCategory(
                request.name(), request.description(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        Category category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(toResponse(category));
    }

    @GetMapping("/root")
    public ResponseEntity<List<CategoryResponse>> getRootCategories() {
        List<CategoryResponse> response = categoryService.getRootCategories()
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<CategoryResponse>> getSubcategories(@PathVariable UUID parentId) {
        List<CategoryResponse> response = categoryService.getSubcategories(parentId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    // Manual entity -> DTO mapping, kept as a small private helper (same pattern as
    // GlobalExceptionHandler's buildResponse). Roadmap doc mentions MapStruct for
    // this eventually (avoiding hand-written mapping boilerplate at scale) — not
    // introduced yet since the mapping here is still simple enough (one line) that
    // a new library isn't earning its complexity cost yet. Worth revisiting once
    // there are several more entities with mapping needs.
    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() != null ? category.getParent().getId() : null
        );
    }
}