package com.bajar.saman.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Maps the URL path "/uploads/products/**" to the actual local disk folder where
 * LocalImageStorageService writes uploaded files — without this, a stored image's
 * returned URL (e.g. "/uploads/products/abc123.jpg") would 404 when a browser
 * actually tries to load it, since Spring has no reason to know that URL path
 * corresponds to a real folder on disk.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /**
     * Global pagination size cap — closes gap #1 tracked in PROGRESS.md
     * (§6, HIGH priority). Without this, a client could request
     * ?size=999999 on ANY paginated endpoint (ProductController's listAll/
     * listByCategory) and force the database to materialize the entire
     * table in one query — a real, trivially triggerable DoS vector.
     *
     * PageableHandlerMethodArgumentResolverCustomizer is Spring's built-in
     * hook for this — it intercepts EVERY Pageable-typed controller
     * parameter globally, so this is enforced once here rather than
     * needing a manual check duplicated in every paginated endpoint.
     */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer
    pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(100);
        // 100 chosen as a reasonable ceiling for a product-catalog listing —
        // far more than any real UI would render on one page (the frontend
        // currently defaults to 20 via @PageableDefault), but low enough
        // that even a malicious max-size request can't force a
        // multi-thousand-row query.
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "file:" prefix + trailing slash matter here — Spring's resource handler
        // needs an explicit "file:" URI scheme to know this is a filesystem path
        // (not a classpath resource), and the trailing slash tells it to treat this
        // as a DIRECTORY to serve from, not a single file.
        String resourceLocation = "file:" + uploadDir + "/";

        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations(resourceLocation);
    }
}