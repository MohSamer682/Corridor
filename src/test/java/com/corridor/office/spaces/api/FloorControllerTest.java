package com.corridor.office.spaces.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.config.SecurityConfig;
import com.corridor.office.spaces.application.BuildingView;
import com.corridor.office.spaces.application.CampusQuery;
import com.corridor.office.spaces.application.CampusView;
import com.corridor.office.spaces.application.FloorLayoutView;
import com.corridor.office.spaces.application.FloorSummary;
import java.math.BigDecimal;
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

@WebMvcTest({CampusController.class, FloorController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class FloorControllerTest {

    static final UUID FLOOR_ID = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CampusQuery campus;

    @MockitoBean
    JwtDecoder jwtDecoder;

    static RequestPostProcessor employee() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                .claim("org", UUID.randomUUID().toString())
                .claim("role", "EMPLOYEE"));
    }

    private final FloorLayoutView layout = new FloorLayoutView(
            FLOOR_ID,
            1,
            3,
            1,
            new FloorLayoutView.Position(1, 0),
            "C.C",
            Map.of("items", List.of()),
            List.of(),
            List.of());

    @Test
    void shouldServeTheLayoutWithItsVersionAsTheETag() throws Exception {
        given(campus.layoutVersion(any(), eq(FLOOR_ID))).willReturn(1);
        given(campus.layout(any(), eq(FLOOR_ID))).willReturn(layout);

        mvc.perform(get("/api/v1/floors/{id}/layout", FLOOR_ID).with(employee()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + FLOOR_ID + ":1\""))
                .andExpect(jsonPath("$.tiles").value("C.C"))
                .andExpect(jsonPath("$.arrival.x").value(1));
    }

    @Test
    void shouldAnswer304WhenTheLayoutIsUnchanged() throws Exception {
        given(campus.layoutVersion(any(), eq(FLOOR_ID))).willReturn(1);

        mvc.perform(get("/api/v1/floors/{id}/layout", FLOOR_ID)
                        .header("If-None-Match", "\"" + FLOOR_ID + ":1\"")
                        .with(employee()))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
        then(campus).should(never()).layout(any(), any());
    }

    @Test
    void shouldAnswer404ForAFloorThatIsNotOpen() throws Exception {
        given(campus.layoutVersion(any(), eq(FLOOR_ID))).willThrow(new NotFoundException("spaces.floorNotFound"));

        mvc.perform(get("/api/v1/floors/{id}/layout", FLOOR_ID).with(employee()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("This floor isn't open yet."));
    }

    @Test
    void shouldServeTheCampus() throws Exception {
        var floor = new FloorSummary(FLOOR_ID, 0, "G", "Lobby & Commons", null);
        given(campus.campus(any()))
                .willReturn(new CampusView(List.of(new BuildingView(
                        UUID.randomUUID(),
                        "Corridor HQ",
                        "Head office",
                        new BigDecimal("-30.00"),
                        new BigDecimal("-12.00"),
                        List.of(floor)))));

        mvc.perform(get("/api/v1/campus").with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildings[0].name").value("Corridor HQ"))
                .andExpect(jsonPath("$.buildings[0].floors[0].code").value("G"));
    }

    @Test
    void shouldRefuseAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/campus")).andExpect(status().isUnauthorized());
    }
}
