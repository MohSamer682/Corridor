package com.corridor.office.identity.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.config.SecurityConfig;
import com.corridor.office.identity.application.ProfileQuery;
import com.corridor.office.identity.application.UserView;
import com.corridor.office.identity.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileQuery profiles;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void shouldReturnTheSignedInUser() throws Exception {
        var id = UUID.randomUUID();
        given(profiles.currentUser(id))
                .willReturn(new UserView(id, "nour@corridor.local", "Nour", null, Role.EMPLOYEE));

        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(j -> j.subject(id.toString()).claim("role", "EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("nour@corridor.local"));
    }

    @Test
    void shouldReturnProblemJson401WithoutToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
