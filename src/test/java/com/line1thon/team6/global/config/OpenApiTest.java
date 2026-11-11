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
    void 현재_세션_인자는_요청_파라미터로_나오지_않는다() throws Exception {
        // 채팅 상세의 파라미터는 경로의 chatId 하나뿐이어야 한다 (GuestSession 필드가 쿼리로 펼쳐지면 안 됨)
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].get.parameters.length()").value(1))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].get.parameters[0].name").value("chatId"))
                .andExpect(jsonPath("$.paths['/api/chats'].post.parameters").doesNotExist());
    }

    @Test
    void Swagger_화면은_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
