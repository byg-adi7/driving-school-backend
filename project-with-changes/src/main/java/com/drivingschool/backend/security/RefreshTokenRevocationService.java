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

    /**
     * Revokes every refresh token already issued to this user (e.g. after a password
     * reset, when a leaked token must stop working), for {@code ttlMs} - the refresh
     * token lifetime, after which every such token has expired on its own anyway.
     */
    void revokeAllForUser(Long userId, long ttlMs);

    /**
     * @param issuedAtEpochSeconds the token's {@code iat} claim, which JWT stores at
     *                             one-second precision
     */
    boolean isRevokedForUser(Long userId, long issuedAtEpochSeconds);
}
