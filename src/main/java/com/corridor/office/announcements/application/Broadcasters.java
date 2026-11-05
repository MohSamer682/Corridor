package com.corridor.office.announcements.application;

import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;

/** Who may speak to the whole company: managers, HR and admins. */
final class Broadcasters {

    private Broadcasters() {}

    static void check(CurrentUser person) {
        if (person.role() == Role.EMPLOYEE) {
            throw new ForbiddenException("announcements.notAllowed");
        }
    }
}
