package com.corridor.office.identity.application;

import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import java.util.UUID;

public record UserView(
        UUID id,
        String email,
        String displayName,
        String jobTitle,
        Role role,
        String organizationName,
        UUID teamId,
        String teamName,
        UserStatus status,
        long version) {

    public static UserView of(User user) {
        var team = user.team();
        return new UserView(
                user.id(),
                user.email(),
                user.displayName(),
                user.jobTitle(),
                user.role(),
                user.organization().name(),
                team == null ? null : team.id(),
                team == null ? null : team.name(),
                user.status(),
                user.version());
    }
}
