package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 선생성 재시도 API와, B의 리스너가 부를 completePrepare / failPrepare */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PrepareRetryTest {

    private static final List<String> CHOICES = List.of("칩1", "칩2", "칩3");
    private static final List<String> HINTS = List.of("힌트1", "힌트2");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    ApplicationEvents events;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    /** PREPARING 상태의 채팅 하나 */
    private Long newChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "질문", null, false)).getId();
    }

    private PrepareStatus prepareStatusOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getPrepareStatus();
    }

    private ResultActions retry(Long chatId) throws Exception {
        return mockMvc.perform(post("/api/chats/{chatId}/prepare/retry", chatId).header("X-Guest-Token", token));
    }

    // ===== 재시도 API =====

    @Test
    void FAILED면_202와_PREPARING으로_바꾸고_선생성_이벤트를_발행한다() throws Exception {
        Long chatId = newChat(session);
        chatService.failPrepare(chatId);

        retry(chatId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.chatId").value(chatId))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.prepareStatus").value("PREPARING"));

        assertThat(prepareStatusOf(chatId)).isEqualTo(PrepareStatus.PREPARING);
        assertThat(events.stream(PrepareRequested.class)).extracting(PrepareRequested::chatId).containsExactly(chatId);
    }

    @Test
    void 재시도하면_선생성_시작_시각을_새로_기록한다() throws Exception {
        Long chatId = newChat(session);
        chatService.failPrepare(chatId);
        var before = chatRepository.findById(chatId).orElseThrow().getPrepareStartedAt();

        retry(chatId).andExpect(status().isAccepted());

        assertThat(chatRepository.findById(chatId).orElseThrow().getPrepareStartedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void 두_번_연속_누르면_두_번째는_409() throws Exception {
        Long chatId = newChat(session);
        chatService.failPrepare(chatId);

        retry(chatId).andExpect(status().isAccepted());
        retry(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREPARE_NOT_FAILED"));

        assertThat(events.stream(PrepareRequested.class)).hasSize(1);
    }

    @Test
    void PREPARING이면_409() throws Exception {
        Long chatId = newChat(session);

        retry(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREPARE_NOT_FAILED"));
        assertThat(events.stream(PrepareRequested.class)).isEmpty();
    }

    @Test
    void DONE이면_409() throws Exception {
        Long chatId = newChat(session);
        chatService.completePrepare(chatId, CHOICES, HINTS, "기준 답변");

        retry(chatId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREPARE_NOT_FAILED"));
        assertThat(prepareStatusOf(chatId)).isEqualTo(PrepareStatus.DONE);
    }

    @Test
    void 남의_대화면_404이고_상태를_바꾸지_않는다() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChat = newChat(other);
        chatService.failPrepare(othersChat);

        retry(othersChat)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
        assertThat(prepareStatusOf(othersChat)).isEqualTo(PrepareStatus.FAILED);
    }

    @Test
    void 없는_대화면_404() throws Exception {
        retry(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void chatId가_숫자가_아니면_400() throws Exception {
        mockMvc.perform(post("/api/chats/abc/prepare/retry").header("X-Guest-Token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/prepare/retry", newChat(session)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    // ===== B가 부를 메서드 =====

    @Test
    void completePrepare는_결과를_저장하고_DONE으로_바꾼다() {
        Long chatId = newChat(session);

        chatService.completePrepare(chatId, CHOICES, HINTS, "기준 답변");

        Chat chat = chatRepository.findById(chatId).orElseThrow();
        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
        assertThat(chat.getChoices()).containsExactlyElementsOf(CHOICES);
        assertThat(chat.getHints()).containsExactlyElementsOf(HINTS);
        assertThat(chat.getReferenceAnswer()).isEqualTo("기준 답변");
    }

    @Test
    void failPrepare는_FAILED로_바꾼다() {
        Long chatId = newChat(session);

        chatService.failPrepare(chatId);

        assertThat(prepareStatusOf(chatId)).isEqualTo(PrepareStatus.FAILED);
    }

    @Test
    void 이미_FAILED인_채팅에_늦게_온_결과는_버린다() {
        Long chatId = newChat(session);
        chatService.failPrepare(chatId);

        chatService.completePrepare(chatId, CHOICES, HINTS, "늦게 온 기준 답변");

        Chat chat = chatRepository.findById(chatId).orElseThrow();
        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);
        assertThat(chat.getReferenceAnswer()).isNull();
    }

    @Test
    void 이미_DONE인_채팅의_실패_알림은_무시한다() {
        Long chatId = newChat(session);
        chatService.completePrepare(chatId, CHOICES, HINTS, "기준 답변");

        chatService.failPrepare(chatId);

        assertThat(prepareStatusOf(chatId)).isEqualTo(PrepareStatus.DONE);
    }

    @Test
    void 없는_채팅이면_예외_없이_무시한다() {
        chatService.completePrepare(Long.MAX_VALUE, CHOICES, HINTS, "기준 답변");
        chatService.failPrepare(Long.MAX_VALUE);
    }

    @Test
    void 칩이나_힌트_개수가_틀리거나_기준_답변이_비면_저장하지_않는다() {
        Long chatId = newChat(session);

        assertThatThrownBy(() -> chatService.completePrepare(chatId, List.of(), HINTS, "기준 답변"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chatService.completePrepare(chatId, List.of("1", "2", "3", "4"), HINTS, "기준 답변"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chatService.completePrepare(chatId, CHOICES, List.of("힌트1"), "기준 답변"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chatService.completePrepare(chatId, CHOICES, HINTS, " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(prepareStatusOf(chatId)).isEqualTo(PrepareStatus.PREPARING);
    }
}
