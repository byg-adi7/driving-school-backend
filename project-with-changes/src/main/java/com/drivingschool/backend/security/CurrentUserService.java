package com.drivingschool.backend.security;

import com.drivingschool.backend.common.exception.AuthenticationException;
import com.drivingschool.backend.role.enums.RoleName;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CurrentUserService {

    public UserPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationException("Not authenticated");
        }
        return principal;
    }

    public Long requireUserId() {
        return requirePrincipal().getId();
    }

    public boolean hasRole(RoleName roleName) {
        String authority = "ROLE_" + roleName.name();
        return requirePrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public Set<String> getRoles() {
        return requirePrincipal().getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());
    }
}
