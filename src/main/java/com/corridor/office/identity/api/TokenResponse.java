package com.corridor.office.identity.api;

import com.corridor.office.identity.application.UserView;

record TokenResponse(String accessToken, long expiresIn, UserView user) {}
