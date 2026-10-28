package com.corridor.office.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditChainTest {

    private static final UUID ORGANIZATION = UUID.fromString("0190a6f0-0000-7000-8000-000000000001");
    private static final UUID ACTOR = UUID.fromString("0190a6f0-0000-7000-8000-000000000002");
    private static final Instant AT = Instant.parse("2026-10-28T12:00:00Z");

    private static AuditEntry suspension(Map<String, String> details) {
        return new AuditEntry(ORGANIZATION, ACTOR, "user.suspended", "user", UUID.randomUUID(), details);
    }

    private static AuditEvent first() {
        return AuditEvent.append(null, suspension(Map.of("name", "Omar Adel")), AT);
    }

    @Test
    void shouldStartTheChainWithoutAPreviousHash() {
        var event = first();

        assertThat(event.sequence()).isEqualTo(1);
        assertThat(event.previousHash()).isNull();
        assertThat(event.hash()).hasSize(32);
    }

    @Test
    void shouldLinkEachEventToThePreviousHash() {
        var first = first();
        var second = AuditEvent.append(first, suspension(Map.of("name", "Nour Hassan")), AT.plusSeconds(1));

        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.previousHash()).isEqualTo(first.hash());
    }

    @Test
    void shouldNotDependOnTheOrderOfDetails() {
        var ordered = new LinkedHashMap<String, String>();
        ordered.put("from", "MEMBER");
        ordered.put("to", "MANAGER");
        var reversed = new LinkedHashMap<String, String>();
        reversed.put("to", "MANAGER");
        reversed.put("from", "MEMBER");
        var target = UUID.randomUUID();

        var one = AuditEvent.append(
                null, new AuditEntry(ORGANIZATION, ACTOR, "user.role_changed", "user", target, ordered), AT);
        var two = AuditEvent.append(
                null, new AuditEntry(ORGANIZATION, ACTOR, "user.role_changed", "user", target, reversed), AT);

        assertThat(one.hash()).isEqualTo(two.hash());
    }

    @Test
    void shouldTellAnEmptyFieldFromAShiftedValue() {
        var target = UUID.randomUUID();
        var one = AuditEvent.append(null, new AuditEntry(ORGANIZATION, null, "a", "bc", target, Map.of()), AT);
        var two = AuditEvent.append(null, new AuditEntry(ORGANIZATION, null, "ab", "c", target, Map.of()), AT);

        assertThat(one.hash()).isNotEqualTo(two.hash());
    }

    @Test
    void shouldReportAnIntactChain() {
        var first = first();
        var second = AuditEvent.append(first, suspension(Map.of()), AT.plusSeconds(1));

        var check = AuditChain.verify(List.of(first, second).stream());

        assertThat(check.checked()).isEqualTo(2);
        assertThat(check.firstAltered()).isEmpty();
    }

    @Test
    void shouldReportTheFirstAlteredEvent() {
        var first = first();
        var second = AuditEvent.append(first, suspension(Map.of("name", "Nour Hassan")), AT.plusSeconds(1));
        var third = AuditEvent.append(second, suspension(Map.of()), AT.plusSeconds(2));
        ReflectionTestUtils.setField(second, "details", Map.of("name", "Someone else"));

        var check = AuditChain.verify(List.of(first, second, third).stream());

        assertThat(check.firstAltered()).containsSame(second);
    }

    @Test
    void shouldReportAMissingEvent() {
        var first = first();
        var second = AuditEvent.append(first, suspension(Map.of()), AT.plusSeconds(1));
        var third = AuditEvent.append(second, suspension(Map.of()), AT.plusSeconds(2));

        var check = AuditChain.verify(List.of(first, third).stream());

        assertThat(check.firstAltered()).containsSame(third);
    }
}
