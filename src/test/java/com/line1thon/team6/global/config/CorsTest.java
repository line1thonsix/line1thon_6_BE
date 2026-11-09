package com.line1thon.team6.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 프론트(다른 주소)에서 API를 부를 수 있는지. 허용 주소는 application.properties 기본값(localhost:5173) */
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

    private static final String FRONT = "http://localhost:5173";

    @Autowired
    MockMvc mockMvc;

    @Test
    void 허용한_주소의_preflight는_토큰_없이_통과한다() throws Exception {
        mockMvc.perform(options("/api/chats/1")
                        .header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Guest-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("X-Guest-Token")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void 허용하지_않은_주소의_preflight는_403() throws Exception {
        mockMvc.perform(options("/api/chats/1")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 실제_응답에서_X_Request_Id를_읽을_수_있게_노출한다() throws Exception {
        mockMvc.perform(post("/api/sessions").header(HttpHeaders.ORIGIN, FRONT))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsStringIgnoringCase("X-Request-Id")));
    }
}
