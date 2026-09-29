package com.drivingschool.backend.auth.mapper;

import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.config.JwtProperties;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class AuthMapper {

    private final JwtProperties jwtProperties;

    public AuthMapper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .roles(user.getRoles().stream()
                                .map(role -> role.getName().name())
                                .collect(Collectors.toSet()))
                        .build())
                .build();
    }

    /**
     * For an account created BY someone else (an admin or instructor): just the new
     * user's identity, deliberately without tokens - handing the creator a working
     * session for the account they just made let them act as that user for the
     * refresh token's whole 7-day life. The new user logs in themselves.
     */
    public AuthResponse toRegisteredUserResponse(User user) {
        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .roles(user.getRoles().stream()
                                .map(role -> role.getName().name())
                                .collect(Collectors.toSet()))
                        .build())
                .build();
    }

    public AuthResponse toAuthResponse(UserPrincipal principal, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(principal.getId())
                        .email(principal.getEmail())
                        .roles(principal.getAuthorities().stream()
                                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                                .collect(Collectors.toSet()))
                        .build())
                .build();
    }
}
