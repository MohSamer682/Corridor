package com.corridor.office.meetings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.meetings.application.MeetingClock;
import com.corridor.office.meetings.application.MeetingMedia;
import com.corridor.office.meetings.domain.MeetingStatus;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true",
            "app.livekit.url=ws://livekit.test:7880",
            "app.livekit.api-key=testkey",
            "app.livekit.api-secret=a-test-secret-that-is-at-least-32-bytes-long"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MeetingMediaIntegrationTest {

    // Meeting rooms on L1 Engineering; each meeting in a test gets its own, so the bookings never overlap.
    private static final List<UUID> ROOMS = List.of(
            UUID.fromString("2dabfd95-c0fa-4311-adca-1f73cf1af37c"),
            UUID.fromString("b9ccbd69-9cae-4d10-a385-38b2e7b6a93b"),
            UUID.fromString("596d30d5-ee4c-424e-b5a5-620a5643630c"));

    private int nextRoom;

    @Autowired
    MeetingMedia media;

    @Autowired
    MeetingClock meetingClock;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser nour;
    private CurrentUser omar;
    private CurrentUser salma;

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    @BeforeEach
    void clearTheCalendar() {
        jdbc.sql("DELETE FROM meeting_attendees").update();
        jdbc.sql("DELETE FROM meetings").update();
        nour = person("nour@corridor.local");
        omar = person("omar@corridor.local");
        salma = person("manager@corridor.local");
    }

    // Written straight to the table so it can start in the past or right now.
    private UUID meeting(Instant start, Instant end, String status) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO meetings (id, organization_id, room_id, organizer_id, title, starts_at, ends_at, status,
                                              created_at, updated_at)
                        VALUES (:id, :org, :room, :organizer, 'Release planning', :start, :end, :status, now(), now())""")
                .paramSource(Map.of(
                        "id", id,
                        "org", nour.organizationId(),
                        "room", ROOMS.get(nextRoom++ % ROOMS.size()),
                        "organizer", nour.userId(),
                        "start", Timestamp.from(start),
                        "end", Timestamp.from(end),
                        "status", status))
                .update();
        jdbc.sql("INSERT INTO meeting_attendees (meeting_id, user_id) VALUES (:id, :omar)")
                .param("id", id)
                .param("omar", omar.userId())
                .update();
        return id;
    }

    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM meetings WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    @Test
    void shouldGiveInviteesATokenForTheMeetingRoomUntilItEnds() throws Exception {
        var end = Instant.now().plus(25, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        var id = meeting(Instant.now().minus(5, ChronoUnit.MINUTES), end, "LIVE");

        var join = media.join(omar, id);

        assertThat(join.url()).isEqualTo("ws://livekit.test:7880");
        var jwt = SignedJWT.parse(join.token());
        assertThat(jwt.verify(new MACVerifier("a-test-secret-that-is-at-least-32-bytes-long")))
                .isTrue();
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("testkey");
        assertThat(claims.getSubject()).isEqualTo(omar.userId().toString());
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(end);
        @SuppressWarnings("unchecked")
        var video = (Map<String, Object>) claims.getClaim("video");
        assertThat(video).containsEntry("room", "meeting-" + id).containsEntry("roomJoin", true);
    }

    @Test
    void shouldRefuseSomeoneWhoIsNotInvited() {
        var id = meeting(Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES), "LIVE");

        assertThatThrownBy(() -> media.join(salma, id)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldOpenTenMinutesBeforeTheStartAndCloseWhenCancelled() {
        var early = meeting(
                Instant.now().plus(11, ChronoUnit.MINUTES), Instant.now().plus(41, ChronoUnit.MINUTES), "SCHEDULED");
        var soon = meeting(
                Instant.now().plus(70, ChronoUnit.MINUTES).minus(61, ChronoUnit.MINUTES),
                Instant.now().plus(90, ChronoUnit.MINUTES),
                "SCHEDULED");
        var cancelled = meeting(Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES), "CANCELLED");

        assertThatThrownBy(() -> media.join(nour, early))
                .isInstanceOf(ConflictException.class)
                .hasMessage("meetings.notYet");
        assertThat(media.join(nour, soon).token()).isNotBlank();
        assertThatThrownBy(() -> media.join(nour, cancelled))
                .isInstanceOf(ConflictException.class)
                .hasMessage("meetings.closed");
    }

    @Test
    void shouldGoLiveAndEndOnTime() {
        var now = meeting(
                Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES), "SCHEDULED");
        var over =
                meeting(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.MINUTES), "LIVE");

        meetingClock.tick();

        assertThat(status(now)).isEqualTo(MeetingStatus.LIVE.name());
        assertThat(status(over)).isEqualTo(MeetingStatus.ENDED.name());
    }
}
