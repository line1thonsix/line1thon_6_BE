package com.line1thon.team6.session.controller;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE /api/sessions/me: 전체 데이터 삭제 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionDeleteTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long newChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", null, false)).getId();
    }

    private int count(String table, Long chatId) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where chat_id = ?", Integer.class, chatId);
    }

    @Test
    void 세션과_모든_채팅_생각_분석_작업을_지우고_204() throws Exception {
        Long withThought = newChat(session);
        Long plain = newChat(session);
        chatService.completePrepare(withThought, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        mockMvc.perform(post("/api/chats/{chatId}/thoughts", withThought)
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요."}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/sessions/me").header("X-Guest-Token", token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(guestSessionRepository.findByToken(token)).isEmpty();
        assertThat(chatRepository.existsById(withThought)).isFalse();
        assertThat(chatRepository.existsById(plain)).isFalse();
        assertThat(count("thought", withThought)).isZero();
        assertThat(count("analysis_job", withThought)).isZero();
    }

    @Test
    void 지운_뒤_같은_토큰으로_요청하면_401() throws Exception {
        mockMvc.perform(delete("/api/sessions/me").header("X-Guest-Token", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(delete("/api/sessions/me").header("X-Guest-Token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 다른_세션의_데이터는_남긴다() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChat = newChat(other);

        mockMvc.perform(delete("/api/sessions/me").header("X-Guest-Token", token))
                .andExpect(status().isNoContent());

        assertThat(guestSessionRepository.findById(other.getId())).isPresent();
        assertThat(chatRepository.existsById(othersChat)).isTrue();
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(delete("/api/sessions/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
