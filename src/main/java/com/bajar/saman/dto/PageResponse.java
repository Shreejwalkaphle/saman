package com.bajar.saman.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable, explicit shape for paginated responses — deliberately NOT returning
 * Spring Data's Page<T> directly from a controller. Page is designed for internal
 * Spring use; serializing it straight to JSON exposes internal implementation
 * details (like a "pageable" object echoing back Spring-specific structure) that
 * are liable to change between Spring versions, and couples our public API
 * contract to a Spring Data internal type rather than something we control.
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}