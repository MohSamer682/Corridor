package com.corridor.office.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigTest.ProbeController.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void shouldReturnProblemJson401WithoutToken() throws Exception {
        mvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldAllowRequestWithValidToken() throws Exception {
        mvc.perform(get("/api/v1/probe").with(jwt().jwt(j -> j.claim("role", "EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnProblemJson403WhenRoleIsMissing() throws Exception {
        mvc.perform(get("/api/v1/probe/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldMapRoleClaimToRoleAuthority() {
        var jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("u1")
                .claim("role", "ADMIN")
                .issuedAt(Instant.now())
                .build();

        var authentication = SecurityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN")
                .noneMatch(authority -> authority.startsWith("SCOPE_"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "{}";
        }

        @GetMapping("/api/v1/probe/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "{}";
        }
    }
}
