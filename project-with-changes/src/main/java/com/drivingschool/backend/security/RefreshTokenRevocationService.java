package com.drivingschool.backend.security;

public interface RefreshTokenRevocationService {

    /**
     * Revokes the refresh token identified by {@code jti} for the remainder of its
     * natural lifetime. A no-op if {@code remainingMs} is not positive - a token with
     * no time left needs no revocation entry, since it will already be rejected as
     * expired.
     */
    void revoke(String jti, long remainingMs);

    boolean isRevoked(String jti);
}
