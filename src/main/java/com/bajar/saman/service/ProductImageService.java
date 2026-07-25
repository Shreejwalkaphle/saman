package com.bajar.saman.service;

import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.ProductImage;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.repository.ProductImageRepository;
import com.bajar.saman.repository.ProductRepository;
import com.bajar.saman.service.storage.ImageStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ImageStorageService imageStorageService;

    public ProductImageService(
            ProductImageRepository productImageRepository,
            ProductRepository productRepository,
            ImageStorageService imageStorageService) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public ProductImage uploadImage(UUID productId, MultipartFile file, boolean setPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        // Deliberately store the file to disk BEFORE touching the database — if
        // the file write fails (disk full, permissions issue), we want that
        // exception to prevent any DB row from ever being created for it. Doing
        // it in this order means there's never a ProductImage row pointing at a
        // file that doesn't actually exist.
        String imageUrl = imageStorageService.store(file);

        if (setPrimary) {
            // If this new image is being set as primary, any PREVIOUSLY primary
            // image for this product must be un-set first — the "at most one
            // primary image per product" rule flagged as NOT database-enforced
            // when V6 migration was written (no partial unique index yet). This is
            // the application-layer enforcement of that rule, exactly as noted
            // back then would happen once the upload service was actually built.
            List<ProductImage> existingImages =
                    productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
            existingImages.stream()
                    .filter(ProductImage::isPrimary)
                    .forEach(img -> img.setPrimary(false)); // dirty-checking handles the
            // actual UPDATE, same pattern
            // as ProductService.updatePrice
        }

        int nextDisplayOrder = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(productId).size();

        ProductImage image = new ProductImage(product, imageUrl, setPrimary);
        image.setDisplayOrder(nextDisplayOrder);

        return productImageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> getImagesForProduct(UUID productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }
}