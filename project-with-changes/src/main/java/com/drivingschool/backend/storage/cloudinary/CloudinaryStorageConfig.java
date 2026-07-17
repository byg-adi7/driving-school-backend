package com.drivingschool.backend.storage.cloudinary;

import com.cloudinary.Cloudinary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Cloudinary client from the {@code CLOUDINARY_URL} environment variable
 * (the convention shared across all of Cloudinary's official SDKs -
 * {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}) - no separate cloud_name/
 * api_key/api_secret properties needed, and nothing to commit to the codebase.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryStorageConfig {

    @Bean
    public Cloudinary cloudinaryClient() {
        return new Cloudinary();
    }
}
