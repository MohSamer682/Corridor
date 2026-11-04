package com.corridor.office.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.application.AuthService;
import com.corridor.office.identity.application.UserAccessService;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.spaces.application.CampusQuery;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PresenceFlowIntegrationTest {

    private static final String PASSWORD = "Corridor-local-1";

    @LocalServerPort
    int port;

    @Autowired
    AuthService auth;

    @Autowired
    CampusQuery campus;

    @Autowired
    UserAccessService access;

    @Autowired
    UserRepository users;

    private UUID floorId;

    @BeforeEach
    void pickTheEngineeringFloor() {
        var nour = users.findByEmail("nour@corridor.local").orElseThrow();
        var viewer = new CurrentUser(nour.id(), nour.organization().id(), nour.role());
        floorId = campus.campus(viewer).buildings().getFirst().floors().get(1).id();
    }

    private record Client(
            StompSession session,
            BlockingQueue<Map<String, Object>> frames,
            BlockingQueue<Map<String, Object>> errors) {}

    private Client connect(String email) throws Exception {
        var token = auth.signIn(email, PASSWORD, "10.9.0." + email.length())
                .accessToken()
                .value();
        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        var session = stomp.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        headers,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        var client = new Client(session, new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        session.subscribe("/topic/floors/" + floorId + "/frames", collect(client.frames()));
        session.subscribe("/user/queue/errors", collect(client.errors()));
        session.subscribe("/user/queue/position", collect(client.errors()));
        return client;
    }

    private static StompFrameHandler collect(BlockingQueue<Map<String, Object>> into) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                into.add((Map<String, Object>) payload);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean movedBy(Map<String, Object> frame, UUID person) {
        return ((List<Map<String, Object>>) frame.get("moved"))
                .stream().anyMatch(state -> person.toString().equals(state.get("userId")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Number> whereSeen(Client watcher, UUID person) throws InterruptedException {
        for (var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos(); System.nanoTime() < deadline; ) {
            var frame = watcher.frames().poll(200, TimeUnit.MILLISECONDS);
            if (frame != null && movedBy(frame, person)) {
                return ((List<Map<String, Object>>) frame.get("moved"))
                        .stream()
                                .filter(state -> person.toString().equals(state.get("userId")))
                                .map(state -> Map.of("x", (Number) state.get("x"), "y", (Number) state.get("y")))
                                .findFirst()
                                .orElseThrow();
            }
        }
        return null;
    }

    private static boolean sawMove(Client watcher, UUID person) throws InterruptedException {
        for (var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos(); System.nanoTime() < deadline; ) {
            var frame = watcher.frames().poll(200, TimeUnit.MILLISECONDS);
            if (frame != null && movedBy(frame, person)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void shouldLetTwoPeopleSeeEachOtherWalk() throws Exception {
        var nour = connect("nour@corridor.local");
        var omar = connect("omar@corridor.local");
        var omarId = users.findByEmail("omar@corridor.local").orElseThrow().id();

        nour.session().send("/app/floors/" + floorId + "/enter", "");
        omar.session().send("/app/floors/" + floorId + "/enter", "");
        var arrived = whereSeen(nour, omarId);
        assertThat(arrived).isNotNull();

        // A small diagonal step north-west from wherever Omar arrived; the floor around the lift is open.
        omar.session()
                .send(
                        "/app/move",
                        Map.of(
                                "x",
                                arrived.get("x").doubleValue() - 0.2,
                                "y",
                                arrived.get("y").doubleValue() - 0.25,
                                "facing",
                                3.9,
                                "moving",
                                true));

        assertThat(sawMove(nour, omarId)).isTrue();
        nour.session().disconnect();
        omar.session().disconnect();
    }

    @Test
    void shouldPutPeopleBackWhenTheyReportAnImpossiblePosition() throws Exception {
        var nour = connect("nour@corridor.local");
        nour.session().send("/app/floors/" + floorId + "/enter", "");
        // STOMP messages from one client may be handled on different threads; wait until the entry lands.
        assertThat(sawMove(
                        nour,
                        users.findByEmail("nour@corridor.local").orElseThrow().id()))
                .isTrue();

        nour.session().send("/app/move", Map.of("x", 0.5, "y", 0.5, "facing", 0, "moving", true));

        var correction = nour.errors().poll(5, TimeUnit.SECONDS);
        assertThat(correction).isNotNull();
        assertThat(((Number) correction.get("x")).doubleValue()).isGreaterThan(20);
        nour.session().disconnect();
    }

    @Test
    void shouldRefuseASocketWithoutAToken() {
        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        var future = stomp.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {});

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCloseTheSocketOfSomeoneWhoIsSuspended() throws Exception {
        var omar = connect("omar@corridor.local");
        omar.session().send("/app/floors/" + floorId + "/enter", "");
        var omarUser = users.findByEmail("omar@corridor.local").orElseThrow();
        var admin = users.findByEmail("admin@corridor.local").orElseThrow();
        var actor = new CurrentUser(admin.id(), admin.organization().id(), admin.role());
        await().atMost(Duration.ofSeconds(3)).until(() -> omar.session().isConnected());

        access.suspend(actor, omarUser.id());

        try {
            await().atMost(Duration.ofSeconds(1)).until(() -> !omar.session().isConnected());
        } finally {
            access.reactivate(actor, omarUser.id());
        }
    }
}
