package com.drivingschool.backend.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** "local" or "gcs". */
    private String provider = "local";

    private long maxFileSizeMb = 50;

    private String allowedMimeTypes = "application/pdf";

    private final Local local = new Local();

    private final Gcs gcs = new Gcs();

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
