package com.drivingschool.backend.storage;

import com.drivingschool.backend.common.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    /** "local", "gcs", or "cloudinary". */
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
     *
     * The provider-credential checks below are deliberately here, not (only) in
     * RequiredEnvironmentValidator: that validator runs before any YAML is
     * loaded, so it can only ever see an *explicitly* set STORAGE_PROVIDER env
     * var - it can't know that an unset one will actually resolve to
     * "cloudinary" via application-prod.yml's own default. This method runs
     * after full property binding, so `provider` here is always the true
     * effective value regardless of whether it came from an explicit env var
     * or a YAML default. That gap matters in practice: neither Cloudinary's
     * nor GCS's client construction fails when its credential is missing -
     * both silently build a client with empty/default config, so without this
     * check the app boots fine and only fails on the first real upload
     * attempt, with nothing in the startup logs pointing at the cause.
     */
    @PostConstruct
    public void validate() {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProduction && "local".equalsIgnoreCase(provider)) {
            throw new BadRequestException(
                    "app.storage.provider=local is not allowed when the 'prod' profile is active - "
                            + "local storage does not persist across redeploys or restarts and isn't shared "
                            + "across instances. Set STORAGE_PROVIDER=cloudinary (or gcs) instead.");
        }

        if ("cloudinary".equalsIgnoreCase(provider) && !StringUtils.hasText(environment.getProperty("CLOUDINARY_URL"))) {
            throw new BadRequestException(
                    "app.storage.provider=cloudinary (the effective value, whether set explicitly or via "
                            + "default) requires CLOUDINARY_URL to be set - without it, Cloudinary silently "
                            + "builds an unauthenticated client instead of failing, so every upload would fail "
                            + "later instead of failing clearly now.");
        }

        if ("gcs".equalsIgnoreCase(provider) && !StringUtils.hasText(gcs.getBucketName())) {
            throw new BadRequestException(
                    "app.storage.provider=gcs (the effective value, whether set explicitly or via default) "
                            + "requires GCS_BUCKET_NAME to be set.");
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
