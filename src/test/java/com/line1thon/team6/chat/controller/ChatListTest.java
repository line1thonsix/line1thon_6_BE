package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/chats: 사이드바 최근 대화, 학습 기록 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatListTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long newChat(GuestSession owner, String question) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, question, null, false)).getId();
    }

    /** 생성 시각과 잠금 상태를 직접 바꾼다. 시각은 Hibernate로 넣어야 UTC 서버(CI)에서도 한국 시간 그대로 저장된다 */
    private void set(Long chatId, ChatStatus status, LocalDateTime createdAt) {
        transactionTemplate.executeWithoutResult(tx -> entityManager
                .createQuery("update Chat c set c.status = :status, c.createdAt = :createdAt where c.id = :id")
                .setParameter("status", status)
                .setParameter("createdAt", createdAt)
                .setParameter("id", chatId)
                .executeUpdate());
    }

    @Test
    void 내_대화만_최신순으로_돌려주고_category를_잠금_상태로_계산한다() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 11, 5, 14, 32, 0);
        Long locked = newChat(session, "잠긴 질문");
        Long compared = newChat(session, "비교 끝난 질문");
        Long basic = newChat(session, "기초 학습 질문");
        set(locked, ChatStatus.LOCKED, base.plusMinutes(2));
        set(compared, ChatStatus.UNLOCKED_SELF, base);
        set(basic, ChatStatus.UNLOCKED_ASSISTED, base.plusMinutes(1));
        newChat(guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString())), "남의 질문");

        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].chatId").value(locked))
                .andExpect(jsonPath("$.items[0].status").value("LOCKED"))
                .andExpect(jsonPath("$.items[0].category").isEmpty())
                .andExpect(jsonPath("$.items[1].chatId").value(basic))
                .andExpect(jsonPath("$.items[1].category").value("BASIC"))
                .andExpect(jsonPath("$.items[2].chatId").value(compared))
                .andExpect(jsonPath("$.items[2].type").value("DEBUGGING"))
                .andExpect(jsonPath("$.items[2].questionPreview").value("비교 끝난 질문"))
                .andExpect(jsonPath("$.items[2].status").value("UNLOCKED_SELF"))
                .andExpect(jsonPath("$.items[2].category").value("COMPARED"))
                .andExpect(jsonPath("$.items[2].reflection").isEmpty())
                .andExpect(jsonPath("$.items[2].createdAt").value("2026-11-05T14:32:00+09:00"));
    }

    @Test
    void 대화가_없으면_빈_배열() throws Exception {
        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 같은_시각이면_나중에_만든_대화가_먼저() throws Exception {
        LocalDateTime same = KoreaTime.now().withNano(0);
        Long first = newChat(session, "먼저");
        Long second = newChat(session, "나중");
        set(first, ChatStatus.LOCKED, same);
        set(second, ChatStatus.LOCKED, same);

        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(jsonPath("$.items[0].chatId").value(second))
                .andExpect(jsonPath("$.items[1].chatId").value(first));
    }

    @Test
    void 질문은_한_줄로_바꿔_앞_40자만_보낸다() throws Exception {
        newChat(session, "  첫 줄\n\n둘째 줄   셋째\t" + "가".repeat(50));

        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(jsonPath("$.items[0].questionPreview")
                        .value(("첫 줄 둘째 줄 셋째 " + "가".repeat(50)).substring(0, 40)));
    }

    @Test
    void limit을_안_보내면_20개() throws Exception {
        for (int i = 0; i < 21; i++) {
            newChat(session, "질문 " + i);
        }

        mockMvc.perform(get("/api/chats").header("X-Guest-Token", token))
                .andExpect(jsonPath("$.items", hasSize(20)));
    }

    @Test
    void limit만큼만_돌려준다() throws Exception {
        for (int i = 0; i < 3; i++) {
            newChat(session, "질문 " + i);
        }

        mockMvc.perform(get("/api/chats").param("limit", "2").header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void limit이_50을_넘으면_50개까지만() throws Exception {
        for (int i = 0; i < 51; i++) {
            newChat(session, "질문 " + i);
        }

        mockMvc.perform(get("/api/chats").param("limit", "100").header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(50)));
    }

    @Test
    void limit이_1보다_작으면_400() throws Exception {
        mockMvc.perform(get("/api/chats").param("limit", "0").header("X-Guest-Token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void limit이_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/chats").param("limit", "abc").header("X-Guest-Token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
