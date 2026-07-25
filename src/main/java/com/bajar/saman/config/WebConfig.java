package com.bajar.saman.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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