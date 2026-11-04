package com.corridor.office.attendance.events;

import java.time.Instant;
import java.util.UUID;

/** Someone punched in; published inside the transaction that recorded it. */
public record PunchedIn(UUID organizationId, UUID userId, Instant at) {}
