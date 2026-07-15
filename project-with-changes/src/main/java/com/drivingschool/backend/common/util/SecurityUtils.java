package com.drivingschool.backend.common.util;

import com.drivingschool.backend.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static Long getCurrentUserId() {
        return getCurrentUser()
                .map(UserPrincipal::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }
    /**
     * @return the current user's role name without the Spring Security "ROLE_" prefix
     *         (e.g. "INSTRUCTOR", not "ROLE_INSTRUCTOR") - callers compare this against
     *         bare role names.
     */
    public static String getCurrentUserRole() {
        return getCurrentUser()
                .flatMap(user -> user.getAuthorities()
                        .stream()
                        .findFirst())
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse(null);
    }

}
