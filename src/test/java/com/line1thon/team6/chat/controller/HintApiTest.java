package com.line1thon.team6.chat.controller;

import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/chats/{chatId}/hints: 힌트 한 단계 공개 (로직은 ChatService.openNextHint, 여기서는 HTTP 응답) */
@SpringBootTest
@AutoConfigureMockMvc
class HintApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long preparingChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", null, false)).getId();
    }

    private Long doneChat() {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        return chatId;
    }

    private ResultActions openHint(Long chatId) throws Exception {
        return mockMvc.perform(post("/api/chats/{chatId}/hints", chatId).header("X-Guest-Token", token));
    }

    private int hintLevelOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getHintLevel();
    }

    @Test
    void 처음_요청하면_1단계_힌트를_돌려준다() throws Exception {
        Long chatId = doneChat();

        openHint(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(1))
                .andExpect(jsonPath("$.hint").value("힌트1"))
                .andExpect(jsonPath("$.hasNextHint").value(true))
                .andExpect(jsonPath("$.basicModeAvailable").value(false));
        assertThat(hintLevelOf(chatId)).isEqualTo(1);
    }

    @Test
    void 두_번째_요청하면_2단계_힌트와_기초_학습_버튼을_돌려준다() throws Exception {
        Long chatId = doneChat();
        openHint(chatId);

        openHint(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(2))
                .andExpect(jsonPath("$.hint").value("힌트2"))
                .andExpect(jsonPath("$.hasNextHint").value(false))
                .andExpect(jsonPath("$.basicModeAvailable").value(true));
    }

    @Test
    void 이미_2단계면_200으로_마지막_힌트를_다시_돌려주고_단계는_그대로다() throws Exception {
        Long chatId = doneChat();
        openHint(chatId);
        openHint(chatId);

        openHint(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(2))
                .andExpect(jsonPath("$.hint").value("힌트2"));
        assertThat(hintLevelOf(chatId)).isEqualTo(2);
    }

    @Test
    void 힌트를_열면_채팅_상세에도_공개된_힌트가_보인다() throws Exception {
        Long chatId = doneChat();
        openHint(chatId);

        mockMvc.perform(get("/api/chats/{chatId}", chatId).header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(1))
                .andExpect(jsonPath("$.hints.length()").value(1))
                .andExpect(jsonPath("$.hints[0].text").value("힌트1"));
    }

    @Test
    void 선생성이_끝나지_않았으면_409_PREPARE_NOT_DONE() throws Exception {
        Long chatId = preparingChat(session);

        openHint(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREPARE_NOT_DONE"));
        assertThat(hintLevelOf(chatId)).isZero();
    }

    @Test
    void 생각을_제출한_뒤면_409_ALREADY_SUBMITTED() throws Exception {
        Long chatId = doneChat();
        lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        openHint(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
        assertThat(hintLevelOf(chatId)).isZero();
    }

    @Test
    void 없는_채팅이면_404_CHAT_NOT_FOUND() throws Exception {
        openHint(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void 남의_채팅이면_404_CHAT_NOT_FOUND이고_단계가_바뀌지_않는다() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long chatId = preparingChat(other);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");

        openHint(chatId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
        assertThat(hintLevelOf(chatId)).isZero();
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/hints", doneChat()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
