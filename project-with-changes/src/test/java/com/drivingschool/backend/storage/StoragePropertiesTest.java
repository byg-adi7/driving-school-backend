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
    void validate_withGcsProviderInProdProfile_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("gcs");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_withLocalProviderOutsideProdProfile_doesNotThrow() {
        MockEnvironment environment = new MockEnvironment();
        StorageProperties properties = new StorageProperties(environment);
        properties.setProvider("local");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
