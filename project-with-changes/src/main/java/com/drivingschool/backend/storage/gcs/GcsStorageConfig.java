package com.drivingschool.backend.storage.gcs;

import com.drivingschool.backend.storage.StorageProperties;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the GCS client using Application Default Credentials - no key file in
 * the codebase or config. On Cloud Run/GKE this resolves to the workload's
 * service account automatically; locally, run `gcloud auth application-default login`.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "gcs")
public class GcsStorageConfig {

    @Bean
    public Storage gcsClient(StorageProperties properties) {
        StorageOptions.Builder options = StorageOptions.newBuilder();
        if (properties.getGcs().getProjectId() != null) {
            options.setProjectId(properties.getGcs().getProjectId());
        }
        return options.build().getService();
    }
}
