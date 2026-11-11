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
                .andExpect(jsonPath("$.info.title").value("Stack Overgrow API"))
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
    void 카테고리와_API_이름은_Notion_명세의_한국어_이름이다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name").value(
                        org.hamcrest.Matchers.contains("세션", "채팅", "힌트·기초학습", "생각·잠금", "AI 분석")))
                .andExpect(jsonPath("$.paths['/api/sessions'].post.summary").value("게스트 세션 발급"))
                .andExpect(jsonPath("$.paths['/api/sessions'].post.tags[0]").value("세션"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].get.summary").value("채팅 상세 조회 (새로고침 복구)"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}/thoughts'].post.tags[0]").value("생각·잠금"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}/basic-mode'].post.tags[0]").value("힌트·기초학습"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}/analysis/retry'].post.summary").value("분석 재시도"));
    }

    @Test
    void 토큰_발급_API는_토큰_없이_호출한다고_문서에_표시된다() throws Exception {
        // security가 빈 배열이면 Swagger 화면에서 자물쇠가 없고 Authorize 토큰도 붙이지 않는다
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/sessions'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/chats'].post.security").doesNotExist());
    }

    @Test
    void 최근_대화_목록과_삭제_API도_문서에_나온다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/chats'].get.summary").value("최근 대화 목록"))
                .andExpect(jsonPath("$.paths['/api/chats'].get.tags[0]").value("채팅"))
                .andExpect(jsonPath("$.paths['/api/chats'].get.parameters.length()").value(1))
                .andExpect(jsonPath("$.paths['/api/chats'].get.parameters[0].name").value("limit"))
                .andExpect(jsonPath("$.paths['/api/chats'].get.parameters[0].required").value(false))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].delete.summary").value("대화 삭제"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].delete.tags[0]").value("채팅"))
                .andExpect(jsonPath("$.paths['/api/chats/{chatId}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/me'].delete.summary").value("전체 데이터 삭제"))
                .andExpect(jsonPath("$.paths['/api/sessions/me'].delete.tags[0]").value("세션"))
                .andExpect(jsonPath("$.paths['/api/sessions/me'].delete.parameters").doesNotExist())
                // 토큰이 필요한 API라서 토큰 발급처럼 security를 빈 배열로 덮어쓰지 않는다
                .andExpect(jsonPath("$.paths['/api/sessions/me'].delete.security").doesNotExist());
    }

    @Test
    void Swagger_화면은_토큰_없이_열린다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
