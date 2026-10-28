package com.corridor.office.audit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.audit.application.AuditEventView;
import com.corridor.office.audit.application.AuditFilter;
import com.corridor.office.audit.application.AuditQuery;
import com.corridor.office.audit.application.ChainVerification;
import com.corridor.office.audit.application.InvalidAuditQueryException;
import com.corridor.office.common.api.CursorResponse;
import com.corridor.office.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuditControllerTest {

    static final UUID ADMIN_ID = UUID.randomUUID();
    static final UUID ORG_ID = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditQuery query;

    @MockitoBean
    JwtDecoder jwtDecoder;

    static RequestPostProcessor admin() {
        return jwt().jwt(j ->
                j.subject(ADMIN_ID.toString()).claim("org", ORG_ID.toString()).claim("role", "ADMIN"));
    }

    @Test
    void shouldListEventsWithTheNextCursor() throws Exception {
        var event = new AuditEventView(
                UUID.randomUUID(),
                "user.suspended",
                Instant.parse("2026-10-28T12:32:00Z"),
                ADMIN_ID,
                "user",
                UUID.randomUUID(),
                Map.of("name", "Omar Adel"));
        given(query.list(any(), any(), any(), anyInt())).willReturn(new CursorResponse<>(List.of(event), "next"));

        mvc.perform(get("/api/v1/audit-events")
                        .param("action", "people")
                        .param("actorId", ADMIN_ID.toString())
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-29T00:00:00Z")
                        .param("size", "20")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("user.suspended"))
                .andExpect(jsonPath("$.items[0].details.name").value("Omar Adel"))
                .andExpect(jsonPath("$.nextCursor").value("next"));
        then(query)
                .should()
                .list(
                        any(),
                        eq(new AuditFilter(
                                "people",
                                ADMIN_ID,
                                Instant.parse("2026-10-01T00:00:00Z"),
                                Instant.parse("2026-10-29T00:00:00Z"))),
                        eq(null),
                        eq(20));
    }

    @Test
    void shouldAnswer400ForABrokenCursor() throws Exception {
        given(query.list(any(), any(), any(), anyInt())).willThrow(new InvalidAuditQueryException("audit.badCursor"));

        mvc.perform(get("/api/v1/audit-events").param("cursor", "nope").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void shouldReturnTheVerification() throws Exception {
        given(query.verify(any())).willReturn(new ChainVerification(false, 2, UUID.randomUUID(), Instant.now()));

        mvc.perform(get("/api/v1/audit-events/verification").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.checked").value(2));
    }

    @Test
    void shouldRefuseAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());
    }
}
