package com.corridor.office.identity.application;

import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileQuery {

    private final UserRepository users;

    public ProfileQuery(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        return users.findWithOrganizationById(userId).map(UserView::of).orElseThrow(SessionEndedException::new);
    }
}
