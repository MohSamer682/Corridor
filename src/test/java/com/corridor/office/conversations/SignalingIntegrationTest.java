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
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.application.ConversationService;
import com.corridor.office.conversations.application.ConversationService.GroupView;
import com.corridor.office.conversations.application.SignalRelay;
import com.corridor.office.conversations.application.SignalRelay.SignalView;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.presence.events.FloorTicked;
import com.corridor.office.presence.events.FloorTicked.Standing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
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
class SignalingIntegrationTest {

    private static final UUID ENGINEERING = UUID.fromString("7fa3558d-0411-44bf-8513-edb880329899");

    @MockitoBean
    SimpMessagingTemplate sockets;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    ConversationService conversations;

    @Autowired
    SignalRelay relay;

    @Autowired
    UserRepository users;

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
        stand();
        clearInvocations(sockets);
    }

    private void stand(Standing... everyone) {
        events.publishEvent(new FloorTicked(organizationId, ENGINEERING, List.of(everyone)));
    }

    @Test
    void shouldPassAnOfferBetweenTwoPeopleInTheSameGroup() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));

        relay.relay(nour, omar.userId(), "offer", "{\"sdp\":\"v=0\"}");

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(sockets).convertAndSendToUser(eq(omar.userId().toString()), eq("/queue/signals"), sent.capture());
        assertThat(((SignalView) sent.getValue()).from()).isEqualTo(nour.userId());
    }

    @Test
    void shouldDropSignalsBetweenPeopleInDifferentGroups() {
        stand(
                new Standing(nour.userId(), 40, 18),
                new Standing(omar.userId(), 41, 18),
                new Standing(nadia.userId(), 5, 18));

        relay.relay(nadia, omar.userId(), "offer", "{}");
        relay.relay(nour, nadia.userId(), "candidate", "{}");

        verify(sockets, never()).convertAndSendToUser(any(), eq("/queue/signals"), any());
    }

    @Test
    void shouldDropSignalsOnceSomeoneHasWalkedAway() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 50, 30));

        relay.relay(nour, omar.userId(), "answer", "{}");

        verify(sockets, never()).convertAndSendToUser(any(), eq("/queue/signals"), any());
    }

    @Test
    void shouldRefuseUnknownKindsAndPayloadsOver64Kilobytes() {
        stand(new Standing(nour.userId(), 40, 18), new Standing(omar.userId(), 41, 18));

        assertThatThrownBy(() -> relay.relay(nour, omar.userId(), "hello", "{}"))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> relay.relay(nour, omar.userId(), "offer", "x".repeat(64 * 1024 + 1)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void shouldTalkPeerToPeerUpToFourAndNotForFive() {
        var five = new ArrayList<Standing>();
        var people = List.of(nour, omar, nadia);
        for (int i = 0; i < people.size(); i++) {
            five.add(new Standing(people.get(i).userId(), 40 + i, 18));
        }
        stand(five.toArray(Standing[]::new));
        assertThat(lastGroupSentTo(nour).route()).isEqualTo("PEER_TO_PEER");

        five.add(new Standing(UUID.randomUUID(), 43, 18));
        five.add(new Standing(UUID.randomUUID(), 44, 18));
        stand(five.toArray(Standing[]::new));

        assertThat(lastGroupSentTo(nour).route()).isEqualTo("TOO_BIG");
        relay.relay(nour, omar.userId(), "offer", "{}");
        verify(sockets, never()).convertAndSendToUser(any(), eq("/queue/signals"), any());
    }

    private GroupView lastGroupSentTo(CurrentUser person) {
        var sent = ArgumentCaptor.forClass(Object.class);
        verify(sockets, atLeastOnce())
                .convertAndSendToUser(eq(person.userId().toString()), eq("/queue/conversation"), sent.capture());
        return (GroupView) sent.getValue();
    }
}
