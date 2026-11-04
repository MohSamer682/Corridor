package com.corridor.office.attendance.events;

import java.time.Instant;
import java.util.UUID;

/** Someone punched out, closing the interval that started at {@code from}. */
public record PunchedOut(UUID organizationId, UUID userId, Instant from, Instant to) {}
