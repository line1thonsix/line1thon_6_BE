package com.line1thon.team6.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Swagger 문서와 화면이 토큰 없이 열리고, X-Guest-Token 인증 방식이 문서에 들어가는지 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void API_문서는_토큰_없이_열리고_X_Guest_Token_헤더를_인증으로_쓴다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Deveil API"))
                .andExpect(jsonPath("$.components.securitySchemes.guestToken.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.guestToken.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.guestToken.name").value("X-Guest-Token"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}/analysis'].get").exists());
    }

    @Test
    void Swagger_화면은_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
