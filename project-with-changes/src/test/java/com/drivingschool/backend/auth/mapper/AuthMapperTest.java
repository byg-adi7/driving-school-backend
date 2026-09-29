package com.drivingschool.backend.auth.mapper;

import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.config.JwtProperties;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthMapperTest {

    private final JwtProperties jwtProperties = mock(JwtProperties.class);
    private final AuthMapper mapper = new AuthMapper(jwtProperties);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User student() {
        User user = User.builder().email("new.student@example.com").password("x").enabled(true).emailVerified(false).build();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.addRole(Role.builder().name(RoleName.STUDENT).build());
        return user;
    }

    @Test
    void toRegisteredUserResponse_carriesIdentityButNoTokenFieldsAtAll() throws Exception {
        JsonNode json = objectMapper.valueToTree(mapper.toRegisteredUserResponse(student()));

        assertThat(json.path("user").path("id").asLong()).isEqualTo(42L);
        assertThat(json.path("user").path("email").asText()).isEqualTo("new.student@example.com");
        assertThat(json.path("user").path("roles").get(0).asText()).isEqualTo("STUDENT");
        assertThat(json.has("accessToken")).isFalse();
        assertThat(json.has("refreshToken")).isFalse();
        assertThat(json.has("tokenType")).isFalse();
        assertThat(json.has("expiresIn")).isFalse();
    }

    @Test
    void toAuthResponse_loginShapeIsUnchanged() {
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponse response = mapper.toAuthResponse(student(), "access", "refresh");
        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("accessToken").asText()).isEqualTo("access");
        assertThat(json.path("refreshToken").asText()).isEqualTo("refresh");
        assertThat(json.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.path("expiresIn").asLong()).isEqualTo(900L);
    }
}
