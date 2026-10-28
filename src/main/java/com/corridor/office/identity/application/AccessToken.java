package com.corridor.office.identity.application;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {}
