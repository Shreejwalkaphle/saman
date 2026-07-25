package com.bajar.saman.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores uploaded images on the local filesystem, under the directory configured
 * by app.upload.dir. Files are served back out via WebConfig's static resource
 * mapping (see that class) — this class's only job is writing the file to disk and
 * handing back a URL path; it has no idea HOW that path later gets served, keeping
 * this class focused on a single responsibility.
 */
@Service
public class LocalImageStorageService implements ImageStorageService {

    // Only these extensions are accepted — deliberately a strict allow-list, not a
    // deny-list of "bad" extensions. An allow-list is the safer default: it's
    // impossible to accidentally permit something dangerous (e.g. a ".jsp" or
    // ".php" file disguised as an image) because anything not explicitly listed is
    // rejected, rather than trying to enumerate every dangerous extension that
    // might exist.
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of("jpg", "jpeg", "png", "webp");

    // 5 MB — an arbitrary but reasonable ceiling for a product photo. Without a
    // limit, a malicious or accidental huge upload could fill disk space or tie up
    // server resources — this is a basic denial-of-service safeguard.
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final Path uploadDirectory;

    public LocalImageStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDirectory = Path.of(uploadDir);
        try {
            // Ensures the upload directory actually exists on disk before this
            // service ever tries to write into it — createDirectories() is
            // idempotent (safe to call even if the directory already exists,
            // unlike a plain mkdir which would throw).
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            // Fail fast at STARTUP, not on the first upload attempt — if the
            // upload directory can't be created (permissions issue, invalid path,
            // etc.), we want the application to refuse to start rather than run
            // for hours and only discover the problem when the first customer/
            // admin tries to upload an image.
            throw new IllegalStateException("Could not create upload directory: " + uploadDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        validateFile(file);

        String extension = extractExtension(file.getOriginalFilename());

        // Generate a fresh random filename rather than trusting the client-provided
        // one. This closes TWO real risks at once: (1) path traversal — a
        // maliciously crafted filename like "../../etc/passwd.jpg" could otherwise
        // write outside the intended upload directory; a fresh UUID has no path
        // separators or ".." sequences, so this is structurally impossible; (2)
        // filename collisions — two different admins uploading a file both named
        // "photo.jpg" would otherwise silently overwrite one another.
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadDirectory.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }

        // Returned path matches the URL pattern WebConfig maps to this physical
        // directory (see that class) — a frontend can use this value directly as
        // an <img src="..."> without any further transformation.
        return "/uploads/products/" + storedFilename;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 5MB");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: " + ALLOWED_EXTENSIONS);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("Uploaded file has no discernible extension");
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }
}