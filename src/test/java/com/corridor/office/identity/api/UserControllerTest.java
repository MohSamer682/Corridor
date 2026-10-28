package com.corridor.office.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.Role;
import com.corridor.office.config.SecurityConfig;
import com.corridor.office.identity.application.UserAccessService;
import com.corridor.office.identity.application.UserService;
import com.corridor.office.identity.application.UserView;
import com.corridor.office.identity.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    static final UUID ADMIN_ID = UUID.randomUUID();
    static final UUID ORG_ID = UUID.randomUUID();
    static final String NEW_USER =
            "{\"email\":\"nour@corridor.local\",\"displayName\":\"Nour Hassan\",\"role\":\"EMPLOYEE\",\"password\":\"Welcome-pass-1\"}";
    static final String CHANGES = "{\"displayName\":\"Nour\",\"role\":\"EMPLOYEE\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService users;

    @MockitoBean
    UserAccessService access;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final UUID nourId = UUID.randomUUID();
    private final UserView nour = new UserView(
            nourId,
            "nour@corridor.local",
            "Nour Hassan",
            null,
            Role.EMPLOYEE,
            "Corridor Technologies",
            null,
            null,
            UserStatus.ACTIVE,
            3);

    static RequestPostProcessor admin() {
        return jwt().jwt(j ->
                j.subject(ADMIN_ID.toString()).claim("org", ORG_ID.toString()).claim("role", "ADMIN"));
    }

    @Test
    void shouldReturn201WithLocationWhenCreating() throws Exception {
        given(users.create(any(), any())).willReturn(nour);

        mvc.perform(post("/api/v1/users")
                        .with(admin())
                        .contentType(APPLICATION_JSON)
                        .content(NEW_USER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + nourId))
                .andExpect(jsonPath("$.email").value("nour@corridor.local"));
    }

    @Test
    void shouldReturn422ForAShortPassword() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(admin())
                        .contentType(APPLICATION_JSON)
                        .content(NEW_USER.replace("Welcome-pass-1", "short")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void shouldReturnETagWithTheVersion() throws Exception {
        given(users.get(any(), eq(nourId))).willReturn(nour);

        mvc.perform(get("/api/v1/users/" + nourId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void shouldReturn428WithoutIfMatch() throws Exception {
        mvc.perform(patch("/api/v1/users/" + nourId)
                        .with(admin())
                        .contentType(APPLICATION_JSON)
                        .content(CHANGES))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.detail").value("Reload the user before saving."));
    }

    @Test
    void shouldPassTheIfMatchVersionToTheService() throws Exception {
        given(users.update(any(), eq(nourId), eq(3L), any())).willReturn(nour);

        mvc.perform(patch("/api/v1/users/" + nourId)
                        .with(admin())
                        .header("If-Match", "\"3\"")
                        .contentType(APPLICATION_JSON)
                        .content(CHANGES))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void shouldRejectAStaleIfMatch() throws Exception {
        given(users.update(any(), eq(nourId), eq(2L), any())).willThrow(new ConflictException("users.stale"));

        mvc.perform(patch("/api/v1/users/" + nourId)
                        .with(admin())
                        .header("If-Match", "\"2\"")
                        .contentType(APPLICATION_JSON)
                        .content(CHANGES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Someone else changed this user. Reload and try again."));
    }

    @Test
    void shouldAnswerSuspensionWithAnApiResult() throws Exception {
        mvc.perform(post("/api/v1/users/" + nourId + "/suspension").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nourId.toString()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        then(access).should().suspend(any(), eq(nourId));
    }

    @Test
    void shouldCapPageSizeAt100() throws Exception {
        given(users.list(any(), any(), any(), any(), any())).willReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mvc.perform(get("/api/v1/users?size=500").with(admin())).andExpect(status().isOk());

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        then(users).should().list(any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
