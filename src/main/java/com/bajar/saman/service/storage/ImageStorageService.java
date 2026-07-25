package com.bajar.saman.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over "where uploaded files physically live." Exists so the rest of
 * the application (ProductImageService) depends on THIS interface, never on a
 * specific storage mechanism — Dependency Inversion Principle, same reasoning as
 * PasswordEncoder earlier in this project. Local disk storage is the only
 * implementation for now; swapping to S3/cloud storage later means writing ONE new
 * class implementing this interface and changing which bean gets injected —
 * ProductImageService itself would need zero changes.
 */
public interface ImageStorageService {

    /**
     * Stores the given file and returns a URL/path the frontend can use to display
     * it. The exact meaning of the returned String is implementation-specific
     * (a local implementation might return "/uploads/products/abc123.jpg", a cloud
     * implementation might return a full "https://cdn.example.com/..." URL) — callers
     * treat it as an opaque, directly-usable image URL either way.
     */
    String store(MultipartFile file);
}