package com.corridor.office.audit.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Every field is optional. {@code action} is one action name or a group ({@code people}, {@code teams},
 * {@code sign-in}); {@code from} is inclusive and {@code to} exclusive.
 */
public record AuditFilter(String action, UUID actorId, Instant from, Instant to) {}
