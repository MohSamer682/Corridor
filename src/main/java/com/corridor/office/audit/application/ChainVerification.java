package com.corridor.office.audit.application;

import java.time.Instant;
import java.util.UUID;

/** {@code checked} counts entries up to and including the first altered one. */
public record ChainVerification(boolean intact, long checked, UUID firstAlteredId, Instant firstAlteredAt) {}
