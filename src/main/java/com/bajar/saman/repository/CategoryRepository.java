package com.bajar.saman.repository;

import com.bajar.saman.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    // Finds all DIRECT children of a given category — this is how the
    // unidirectional Category.parent relationship (decided when building the
    // entity) actually answers "what are this category's subcategories," instead
    // of navigating a bidirectional `children` field that was deliberately not
    // added to the entity.
    List<Category> findByParentId(UUID parentId);

    // Finds all ROOT-level categories (top of the tree — e.g. "Electronics",
    // "Clothing"). Spring Data JPA understands "Is<Property>Null" as a query-method
    // keyword — no need for a hand-written @Query for this simple a check. Used for
    // building the main navigation menu / category tree's starting point.
    List<Category> findByParentIsNull();
}