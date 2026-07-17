package com.drivingschool.backend.storage;

import com.drivingschool.backend.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePropertiesTest {

    @Test
    void validate_withLocalProviderInProdProfile_throwsBadRequestException() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("local");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("app.storage.provider=local is not allowed");
    }

    @Test
    void validate_withGcsProviderAndBucketNameInProdProfile_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("gcs");
        properties.getGcs().setBucketName("my-bucket");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_withLocalProviderOutsideProdProfile_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("local");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_withGcsProviderAndNoBucketName_throwsRegardlessOfHowProviderWasSet() {
        // Simulates the gap this check closes: an explicitly-set OR
        // YAML-default-resolved "gcs" provider with no bucket name configured -
        // this method only ever sees the final resolved value either way.
        MockEnvironment environment = new MockEnvironment();
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("gcs");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GCS_BUCKET_NAME");
    }

    @Test
    void validate_withCloudinaryProviderAndNoUrl_throws() {
        MockEnvironment environment = new MockEnvironment();
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("cloudinary");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CLOUDINARY_URL");
    }

    @Test
    void validate_withCloudinaryProviderAndUrlSet_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("CLOUDINARY_URL", "cloudinary://key:secret@cloud-name");
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("cloudinary");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
