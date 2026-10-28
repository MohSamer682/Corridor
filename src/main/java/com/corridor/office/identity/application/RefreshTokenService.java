package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.RefreshToken;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.JwtProperties;
import com.corridor.office.identity.infrastructure.RefreshTokenRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final TokenService tokenService;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository tokens, TokenService tokenService, JwtProperties properties, Clock clock) {
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    public RefreshTokenValue issueFor(User user) {
        var value = newValue(clock.instant());
        tokens.save(
                RefreshToken.issue(user, UUID.randomUUID(), hash(value.raw()).orElseThrow(), value.expiresAt()));
        return value;
    }

    public SignedInSession rotate(String raw) {
        var token = hash(raw).flatMap(tokens::findForRotationByTokenHash).orElseThrow(SessionEndedException::new);
        var now = clock.instant();
        var user = token.user();
        if (!token.isUsable(now) || !user.isActive()) {
            tokens.revokeFamily(token.familyId(), now);
            throw new SessionEndedException();
        }
        var value = newValue(now);
        token.rotateTo(hash(value.raw()).orElseThrow(), now, value.expiresAt());
        return new SignedInSession(tokenService.issueAccessToken(user), value, user);
    }

    public void revokeAllFor(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    public void revokeFamilyOf(String raw) {
        hash(raw)
                .flatMap(tokens::findForRotationByTokenHash)
                .ifPresent(token -> tokens.revokeFamily(token.familyId(), clock.instant()));
    }

    private RefreshTokenValue newValue(Instant now) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RefreshTokenValue(raw, now.plus(properties.refreshTokenTtl()));
    }

    private static Optional<byte[]> hash(String raw) {
        try {
            var bytes = Base64.getUrlDecoder().decode(raw);
            return Optional.of(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
