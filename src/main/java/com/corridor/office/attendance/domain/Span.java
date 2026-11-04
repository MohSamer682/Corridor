package com.corridor.office.attendance.domain;

import java.time.Instant;

/** One punch from in to out; {@code to} is null while it is still open. */
public record Span(Instant from, Instant to) {}
