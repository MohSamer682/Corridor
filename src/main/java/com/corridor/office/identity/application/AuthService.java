package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CredentialVerifier credentials;
    private final RefreshTokenService refreshTokens;
    private final TokenService tokenService;
    private final LoginAttemptLimiter limiter;

    AuthService(
            CredentialVerifier credentials,
            RefreshTokenService refreshTokens,
            TokenService tokenService,
            LoginAttemptLimiter limiter) {
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.limiter = limiter;
    }

    // Refusals still commit, so the failed sign-in they record survives the exception.
    @Transactional(
            noRollbackFor = {
                InvalidCredentialsException.class,
                AccountPendingException.class,
                AccountSuspendedException.class
            })
    public SignedInSession signIn(String email, String password, String clientIp) {
        var normalizedEmail = User.normalizeEmail(email);
        limiter.check(clientIp, normalizedEmail);
        var user = credentials.verify(normalizedEmail, password, clientIp);
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
