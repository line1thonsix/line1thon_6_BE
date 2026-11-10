package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.client.FakeLlmClient;
import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** API 키가 없으면 가짜 클라이언트로 선생성이 끝까지 진행된다 (로컬에서 키 없이 프론트 연동할 때) */
@SpringBootTest
class FakeClientPrepareTest {

    @Autowired
    LlmClient llmClient;

    @Autowired
    PrepareService prepareService;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Test
    void 키가_없으면_가짜_클라이언트를_쓰고_고정_결과로_DONE이_된다() {
        assertThat(llmClient).isInstanceOf(FakeLlmClient.class);
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "질문", null, false)).getId();

        prepareService.prepare(chatId);

        Chat chat = chatRepository.findById(chatId).orElseThrow();
        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
        assertThat(chat.getChoices()).hasSize(3);
        assertThat(chat.getHints()).hasSize(2);
    }

    @Test
    void PREPARING이_아니면_AI를_부르지_않고_건너뛴다() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Chat chat = Chat.create(session, UUID.randomUUID().toString(), QuestionType.CONCEPT, null, "질문", null, false);
        Long chatId = chatRepository.save(chat).getId();
        prepareService.prepare(chatId);

        prepareService.prepare(chatId);

        assertThat(chatRepository.findById(chatId).orElseThrow().getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
    }

    @Test
    void 없는_채팅이면_아무것도_하지_않는다() {
        prepareService.prepare(Long.MAX_VALUE);
    }
}
