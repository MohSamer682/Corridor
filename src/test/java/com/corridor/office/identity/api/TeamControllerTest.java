package com.corridor.office.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.config.SecurityConfig;
import com.corridor.office.identity.application.TeamService;
import com.corridor.office.identity.application.TeamView;
import java.util.List;
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

@WebMvcTest(TeamController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class TeamControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TeamService teams;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final TeamView design = new TeamView(UUID.randomUUID(), "Design", "#23395D", 2, 0);

    static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                .claim("org", UUID.randomUUID().toString())
                .claim("role", role));
    }

    @Test
    void shouldLetEveryoneListTeams() throws Exception {
        given(teams.list(any(), any())).willReturn(new PageResponse<>(List.of(design), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/teams").with(as("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Design"))
                .andExpect(jsonPath("$.items[0].memberCount").value(2));
    }

    @Test
    void shouldReturn201WithLocationWhenCreating() throws Exception {
        given(teams.create(any(), any(), any())).willReturn(design);

        mvc.perform(post("/api/v1/teams")
                        .with(as("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Design\",\"color\":\"#23395d\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/teams/" + design.id()));
    }

    @Test
    void shouldRejectAColorThatIsNotHex() throws Exception {
        mvc.perform(post("/api/v1/teams")
                        .with(as("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Design\",\"color\":\"navy\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("color"));
    }

    @Test
    void shouldExplainWhyATeamWithMembersCannotBeDeleted() throws Exception {
        willThrow(new ConflictException("teams.notEmpty")).given(teams).delete(any(), any());

        mvc.perform(delete("/api/v1/teams/" + design.id()).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Move the team's members before deleting it."));
    }
}
