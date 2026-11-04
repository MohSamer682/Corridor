package com.corridor.office.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.application.ConversationService;
import com.corridor.office.conversations.application.ConversationService.GroupView;
import com.corridor.office.conversations.application.MessageRetentionJob;
import com.corridor.office.conversations.application.MessageService;
import com.corridor.office.conversations.application.MessageService.MessageView;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.presence.events.FloorTicked;
import com.corridor.office.presence.events.FloorTicked.Standing;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConversationFlowIntegrationTest {

    // Corridor HQ, L1 Engineering, from the local seed. The Backend Lead office spans x 18-25, y 0-9.
    private static final UUID ENGINEERING = UUID.fromString("7fa3558d-0411-44bf-8513-edb880329899");

    @MockitoBean
    SimpMessagingTemplate sockets;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    ConversationService conversations;

    @Autowired
    MessageService messages;

    @Autowired
    MessageRetentionJob retention;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private UUID organizationId;
    private CurrentUser nour;
    private CurrentUser omar;
    private CurrentUser nadia;

    @BeforeEach
    void meetTheTeam() {
        var n = users.findByEmail("nour@corridor.local").orElseThrow();
        organizationId = n.organization().id();
        nour = new CurrentUser(n.id(), organizationId, n.role());
        var o = users.findByEmail("omar@corridor.local").orElseThrow();
        omar = new CurrentUser(o.id(), organizationId, o.role());
        var m = users.findByEmail("manager@corridor.local").orElseThrow();
        nadia = new CurrentUser(m.id(), organizationId, m.role());
        conversations.focus(nadia, false);
        stand(); // an empty floor clears groups left by other tests
        clearInvocations(sockets);
    }

    private void stand(Standing... everyone) {
        events.publishEvent(new FloorTicked(organizationId, ENGINEERING, List.of(everyone)));
    }

    private GroupView lastGroupSentTo(CurrentUser person) {
        var sent = ArgumentCaptor.forClass(Object.class);
        verify(sockets, atLeastOnce())
                .convertAndSendToUser(eq(person.userId().toString()), eq("/queue/conversation"), sent.capture());
        return (GroupView) sent.getValue();
    }

    @Test
    void shouldPutTwoPeopleStandingCloseInOneGroup() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 42, 19));

        assertThat(lastGroupSentTo(nour).members()).containsExactlyInAnyOrder(nour.userId(), omar.userId());
        assertThat(conversations.groupOf(omar.userId())).isPresent();
    }

    @Test
    void shouldDropSomeoneFromTheGroupWhenTheyTurnOnFocus() {
        stand(
                new Standing(nour.userId(), 40, 18),
                new Standing(omar.userId(), 42, 19),
                new Standing(nadia.userId(), 41, 20));
        assertThat(lastGroupSentTo(nour).members()).hasSize(3);

        conversations.focus(nadia, true);

        assertThat(lastGroupSentTo(nour).members()).containsExactlyInAnyOrder(nour.userId(), omar.userId());
        assertThat(lastGroupSentTo(nadia).groupId()).isNull();
    }

    @Test
    void shouldEndFocusModeWhenSomeoneLeavesTheOffice() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(nadia.userId(), 41, 18));
        conversations.focus(nadia, true);

        stand(new Standing(nour.userId(), 40, 18));
        stand(new Standing(nour.userId(), 40, 18), new Standing(nadia.userId(), 41, 18));

        assertThat(conversations.inFocus(nadia.userId())).isFalse();
        assertThat(conversations.groupOf(nadia.userId())).isPresent();
    }

    @Test
    void shouldNeverDeliverToSomeoneInFocus() {
        stand(
                new Standing(nour.userId(), 40, 18),
                new Standing(omar.userId(), 42, 19),
                new Standing(nadia.userId(), 41, 20));
        conversations.focus(nadia, true);
        clearInvocations(sockets);

        messages.send(nour, "Got a minute for the release checklist?");

        verify(sockets).convertAndSendToUser(eq(omar.userId().toString()), eq("/queue/messages"), any());
        verify(sockets).convertAndSendToUser(eq(nour.userId().toString()), eq("/queue/messages"), any());
        verify(sockets, never()).convertAndSendToUser(eq(nadia.userId().toString()), eq("/queue/messages"), any());
        assertThat(messages.history(nadia, ENGINEERING, null).items()).isEmpty();
        assertThat(messages.history(omar, ENGINEERING, null).items()).isNotEmpty();
    }

    @Test
    void shouldKeepPeopleOutsideAPrivateRoomOut() {
        stand(
                new Standing(nour.userId(), 20, 7),
                new Standing(omar.userId(), 23, 2),
                new Standing(nadia.userId(), 20, 10));

        assertThat(lastGroupSentTo(nour).members()).containsExactlyInAnyOrder(nour.userId(), omar.userId());
        assertThat(conversations.groupOf(nadia.userId())).isEmpty();
    }

    @Test
    void shouldRefuseToSendWithNobodyNearOrABadBody() {
        stand(new Standing(nour.userId(), 40, 18));

        assertThatThrownBy(() -> messages.send(nour, "Hello?")).isInstanceOf(ConflictException.class);

        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));
        assertThatThrownBy(() -> messages.send(nour, "   ")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> messages.send(nour, "x".repeat(2_001))).isInstanceOf(InvalidInputException.class);
        messages.send(nour, "x".repeat(2_000));
    }

    @Test
    void shouldPageHistoryNewestFirst() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));
        jdbc.sql("DELETE FROM messages").update();
        for (int i = 1; i <= 3; i++) {
            messages.send(nour, "Message " + i);
        }

        var first = messages.history(omar, ENGINEERING, null);
        assertThat(first.items()).extracting(MessageView::body).containsExactly("Message 3", "Message 2", "Message 1");
        var after =
                messages.history(omar, ENGINEERING, first.items().get(1).id().toString());
        assertThat(after.items()).extracting(MessageView::body).containsExactly("Message 1");
    }

    @Test
    void shouldDeleteMessagesOlderThanNinetyDays() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));
        messages.send(nour, "Old news");
        jdbc.sql("UPDATE messages SET created_at = :then")
                .param("then", Timestamp.from(Instant.now().minus(Duration.ofDays(91))))
                .update();

        retention.deleteOldMessages();

        assertThat(jdbc.sql("SELECT count(*) FROM messages").query(Long.class).single())
                .isZero();
    }
}
