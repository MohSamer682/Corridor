package com.corridor.office.attendance.application;

import java.security.SecureRandom;
import java.util.UUID;

/** Time-ordered ids for rows written with plain SQL, like the ones Hibernate makes elsewhere (ADR-002). */
final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    static UUID next() {
        var millis = System.currentTimeMillis();
        var high = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        var low = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }
}
