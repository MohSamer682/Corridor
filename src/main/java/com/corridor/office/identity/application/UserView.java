package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.Role;
import com.corridor.office.identity.domain.User;
import java.util.UUID;

public record UserView(UUID id, String email, String displayName, String jobTitle, Role role, String organizationName) {

    public static UserView of(User user) {
        return new UserView(
                user.id(),
                user.email(),
                user.displayName(),
                user.jobTitle(),
                user.role(),
                user.organization().name());
    }
}
