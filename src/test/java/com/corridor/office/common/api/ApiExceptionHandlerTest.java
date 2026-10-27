package com.corridor.office.common.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.common.domain.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.ProbeController.class)
@Import(ApiExceptionHandlerTest.ProbeController.class)
@ActiveProfiles("test")
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void shouldReturnProblemDetailsForUnknownRoute() throws Exception {
        mvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldMapDomainExceptionToItsStatusAndMessage() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Probe 7 not found."))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn422WithFieldErrorsWhenValidationFails() throws Exception {
        mvc.perform(post("/probe/echo").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void shouldHideInternalErrorsBehindAGenericMessage() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong. Try again."));
    }

    record EchoRequest(@NotBlank String name) {}

    @RestController
    static class ProbeController {

        @GetMapping("/probe/missing")
        String missing() {
            throw new NotFoundException("probe.missing", 7);
        }

        @PostMapping("/probe/echo")
        EchoRequest echo(@Valid @RequestBody EchoRequest request) {
            return request;
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("probe failure");
        }
    }
}
