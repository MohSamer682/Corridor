package com.corridor.office.audit.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class AuditChain {

    private AuditChain() {}

    static byte[] hash(AuditEvent event) {
        var canonical = new ByteArrayOutputStream();
        write(canonical, event.organizationId());
        write(canonical, event.sequence());
        write(canonical, event.actorId());
        write(canonical, event.action());
        write(canonical, event.targetType());
        write(canonical, event.targetId());
        write(canonical, event.createdAt().toString());
        new TreeMap<>(event.details()).forEach((key, value) -> {
            write(canonical, key);
            write(canonical, value);
        });
        var digest = sha256();
        if (event.previousHash() != null) {
            digest.update(event.previousHash());
        }
        return digest.digest(canonical.toByteArray());
    }

    /** Walks events in sequence order and returns the first one whose link, position or content doesn't match. */
    public static ChainCheck verify(Stream<AuditEvent> events) {
        long checked = 0;
        byte[] expectedPrevious = null;
        long expectedSequence = 1;
        for (var event : (Iterable<AuditEvent>) events::iterator) {
            checked++;
            if (event.sequence() != expectedSequence
                    || !Arrays.equals(event.previousHash(), expectedPrevious)
                    || !Arrays.equals(event.hash(), hash(event))) {
                return new ChainCheck(checked, Optional.of(event));
            }
            expectedPrevious = event.hash();
            expectedSequence++;
        }
        return new ChainCheck(checked, Optional.empty());
    }

    // Length-prefixed, so "a"+"bc" and "ab"+"c" never hash the same.
    private static void write(ByteArrayOutputStream out, Object value) {
        var bytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
        out.writeBytes((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
