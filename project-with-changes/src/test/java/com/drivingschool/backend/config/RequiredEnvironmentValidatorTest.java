package com.drivingschool.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEnvironmentValidatorTest {

    private final RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator();

    private void fire(MockEnvironment environment) {
        validator.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment));
    }

    @Test
    void missingDbPasswordAndJwtSecret_throwsWithBothNamed() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> fire(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void allCoreVariablesSet_localStorage_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_PASSWORD", "x");
        environment.setProperty("JWT_SECRET", "x");

        assertThatCode(() -> fire(environment)).doesNotThrowAnyException();
    }

    @Test
    void gcsProviderWithoutBucketName_throws() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_PASSWORD", "x");
        environment.setProperty("JWT_SECRET", "x");
        environment.setProperty("STORAGE_PROVIDER", "gcs");

        assertThatThrownBy(() -> fire(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GCS_BUCKET_NAME");
    }

    @Test
    void gcsProviderWithBucketName_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_PASSWORD", "x");
        environment.setProperty("JWT_SECRET", "x");
        environment.setProperty("STORAGE_PROVIDER", "gcs");
        environment.setProperty("GCS_BUCKET_NAME", "my-bucket");

        assertThatCode(() -> fire(environment)).doesNotThrowAnyException();
    }

    @Test
    void localProvider_neverRequiresGcsBucketName() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DB_PASSWORD", "x");
        environment.setProperty("JWT_SECRET", "x");
        environment.setProperty("STORAGE_PROVIDER", "local");

        assertThatCode(() -> fire(environment)).doesNotThrowAnyException();
    }
}
