package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.events.SignInFailed;
import com.corridor.office.identity.domain.events.SignInFailed.Reason;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Checks an email and password, and publishes every refusal so it reaches the audit log. */
@Component
class CredentialVerifier {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final String dummyHash;

    CredentialVerifier(
            UserRepository users,
            OrganizationRepository organizations,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events) {
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    User verify(String normalizedEmail, String password, String clientIp) {
        var user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            // Hash anyway so an unknown email takes as long as a wrong password.
            passwordEncoder.matches(password, dummyHash);
            // shortcut: one company per deployment, as in RegistrationService; key by email domain if that changes.
            var organization = organizations.findFirstByOrderByCreatedAtAsc().orElseThrow();
            events.publishEvent(
                    new SignInFailed(organization.id(), null, normalizedEmail, Reason.UNKNOWN_EMAIL, clientIp));
            throw new InvalidCredentialsException();
        }
        if (!user.passwordMatches(passwordEncoder, password)) {
            throw refuse(user, Reason.WRONG_PASSWORD, clientIp, new InvalidCredentialsException());
        }
        if (user.isPending()) {
            throw refuse(user, Reason.PENDING, clientIp, new AccountPendingException());
        }
        if (!user.isActive()) {
            throw refuse(user, Reason.SUSPENDED, clientIp, new AccountSuspendedException());
        }
        return user;
    }

    private RuntimeException refuse(User user, Reason reason, String clientIp, RuntimeException refusal) {
        events.publishEvent(new SignInFailed(user.organization().id(), user.id(), user.email(), reason, clientIp));
        return refusal;
    }
}
