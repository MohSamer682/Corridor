package com.corridor.office.identity.application;

import java.time.Instant;

public record RefreshTokenValue(String raw, Instant expiresAt) {}
