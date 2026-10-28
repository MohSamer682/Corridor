package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;

public record SignedInSession(AccessToken accessToken, RefreshTokenValue refreshToken, User user) {}
