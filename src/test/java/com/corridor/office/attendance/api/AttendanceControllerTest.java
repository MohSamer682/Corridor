package com.corridor.office.attendance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.attendance.application.AttendanceQuery;
import com.corridor.office.attendance.application.AttendanceService;
import com.corridor.office.attendance.application.AttendanceService.PunchView;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.config.SecurityConfig;
import java.time.Instant;
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

@WebMvcTest(AttendanceController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AttendanceControllerTest {

    static final UUID NOUR = UUID.randomUUID();
    static final UUID ORG = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AttendanceService attendance;

    @MockitoBean
    AttendanceQuery days;

    @MockitoBean
    JwtDecoder jwtDecoder;

    static RequestPostProcessor nour() {
        return jwt().jwt(j ->
                j.subject(NOUR.toString()).claim("org", ORG.toString()).claim("role", "EMPLOYEE"));
    }

    @Test
    void shouldNeedAnIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/me/punch-in").with(nour())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/punch-in").with(nour()).header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPunchInAndExplainADoublePunch() throws Exception {
        var key = UUID.randomUUID();
        given(attendance.punchIn(any(), eq(key)))
                .willReturn(new PunchView(true, Instant.parse("2026-11-04T07:25:00Z"), null, 0));

        mvc.perform(post("/api/v1/me/punch-in").with(nour()).header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punchedIn").value(true));

        given(attendance.punchIn(any(), eq(key))).willThrow(new ConflictException("attendance.alreadyIn"));
        mvc.perform(post("/api/v1/me/punch-in").with(nour()).header("Idempotency-Key", key.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You're already punched in."));
    }
}
