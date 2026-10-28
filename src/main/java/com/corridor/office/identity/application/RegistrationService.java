package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final LoginAttemptLimiter limiter;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository users,
            OrganizationRepository organizations,
            LoginAttemptLimiter limiter,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.organizations = organizations;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
    }

    // An existing email gets the same answer as a new one, so sign-up can't be used to find accounts.
    @Transactional
    public void requestAccess(String displayName, String email, String password, String clientIp) {
        var normalizedEmail = User.normalizeEmail(email);
        limiter.check(clientIp, normalizedEmail);
        if (users.existsByEmail(normalizedEmail)) {
            return;
        }
        // shortcut: one company per deployment in v1; pick the company from the request once there are several.
        var organization = organizations.findFirstByOrderByCreatedAtAsc().orElseThrow();
        users.save(User.requestAccess(organization, email, displayName.strip(), passwordEncoder.encode(password)));
    }
}
