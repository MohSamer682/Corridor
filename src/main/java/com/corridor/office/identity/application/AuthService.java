package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final TokenService tokenService;
    private final LoginAttemptLimiter limiter;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenService refreshTokens,
            TokenService tokenService,
            LoginAttemptLimiter limiter,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public SignedInSession signIn(String email, String password, String clientIp) {
        var normalizedEmail = User.normalizeEmail(email);
        limiter.check(clientIp, normalizedEmail);
        var user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            // Hash anyway so an unknown email takes as long as a wrong password.
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!user.passwordMatches(passwordEncoder, password)) {
            throw new InvalidCredentialsException();
        }
        if (user.isPending()) {
            throw new AccountPendingException();
        }
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }
        return new SignedInSession(tokenService.issueAccessToken(user), refreshTokens.issueFor(user), user);
    }

    // Reuse detection revokes the family and then fails; that revocation must survive the failure.
    @Transactional(noRollbackFor = SessionEndedException.class)
    public SignedInSession refresh(String rawRefreshToken) {
        return refreshTokens.rotate(rawRefreshToken);
    }

    @Transactional
    public void signOut(String rawRefreshToken) {
        refreshTokens.revokeFamilyOf(rawRefreshToken);
    }
}
