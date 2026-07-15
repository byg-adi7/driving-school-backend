package com.drivingschool.backend.storage;

import com.drivingschool.backend.common.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private final Environment environment;

    public StorageProperties(Environment environment) {
        this.environment = environment;
    }

    /** "local" or "gcs". */
    private String provider = "local";

    private long maxFileSizeMb = 50;

    private String allowedMimeTypes = "application/pdf";

    private final Local local = new Local();

    private final Gcs gcs = new Gcs();

    /**
     * Local-disk storage doesn't persist across restarts/redeploys and isn't
     * shared across instances, so it's a data-loss trap if it's ever active
     * in production - most likely by a simply-unset STORAGE_PROVIDER rather
     * than a deliberate choice. Fail fast at startup instead of silently
     * losing uploaded files later.
     */
    @PostConstruct
    public void validate() {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProduction && "local".equalsIgnoreCase(provider)) {
            throw new BadRequestException(
                    "app.storage.provider=local is not allowed when the 'prod' profile is active - "
                            + "local storage does not persist across redeploys or restarts and isn't shared "
                            + "across instances. Set STORAGE_PROVIDER=gcs (or another real backend) instead.");
        }
    }

    @Getter
    @Setter
    public static class Local {
        private String basePath = "./uploads";
    }

    @Getter
    @Setter
    public static class Gcs {
        private String bucketName;
        private String projectId;
    }
}
