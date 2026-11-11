package com.line1thon.team6.chat.controller;

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

/** DELETE /api/chats/{chatId}: 대화 삭제 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatDeleteTest {

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
    void 생각과_분석_작업까지_함께_지우고_204() throws Exception {
        Long chatId = newChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        mockMvc.perform(post("/api/chats/{chatId}/thoughts", chatId)
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요."}
                                """))
                .andExpect(status().isAccepted());
        assertThat(count("thought", chatId)).isEqualTo(1);
        assertThat(count("analysis_job", chatId)).isEqualTo(1);

        mockMvc.perform(delete("/api/chats/{chatId}", chatId).header("X-Guest-Token", token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(chatRepository.existsById(chatId)).isFalse();
        assertThat(count("thought", chatId)).isZero();
        assertThat(count("analysis_job", chatId)).isZero();
        mockMvc.perform(get("/api/chats/{chatId}", chatId).header("X-Guest-Token", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_대화는_남긴다() throws Exception {
        Long target = newChat(session);
        Long other = newChat(session);

        mockMvc.perform(delete("/api/chats/{chatId}", target).header("X-Guest-Token", token))
                .andExpect(status().isNoContent());

        assertThat(chatRepository.existsById(other)).isTrue();
    }

    @Test
    void 남의_대화는_지우지_않고_404() throws Exception {
        Long othersChat = newChat(guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString())));

        mockMvc.perform(delete("/api/chats/{chatId}", othersChat).header("X-Guest-Token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));

        assertThat(chatRepository.existsById(othersChat)).isTrue();
    }

    @Test
    void 없는_대화나_이미_지운_대화는_404() throws Exception {
        Long chatId = newChat(session);
        mockMvc.perform(delete("/api/chats/{chatId}", chatId).header("X-Guest-Token", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/chats/{chatId}", chatId).header("X-Guest-Token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        Long chatId = newChat(session);

        mockMvc.perform(delete("/api/chats/{chatId}", chatId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        assertThat(chatRepository.existsById(chatId)).isTrue();
    }
}
