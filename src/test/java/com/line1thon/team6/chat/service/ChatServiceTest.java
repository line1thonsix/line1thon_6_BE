package com.line1thon.team6.chat.service;

import com.line1thon.team6.analysis.service.AnalysisJobService;
import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.session.entity.GuestSession;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 같은 요청 두 개가 동시에 들어와 UNIQUE 제약에 걸린 경우 (통합 테스트로는 재현하기 어려워 목으로 확인) */
class ChatServiceTest {

    private final ChatRepository chatRepository = mock(ChatRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ChatService chatService = new ChatService(chatRepository, new SecretMasker(), eventPublisher,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), mock(AnalysisJobService.class));

    private final GuestSession session = new GuestSession(UUID.randomUUID().toString());
    private final UUID clientRequestId = UUID.randomUUID();
    private final ChatCreateRequest request =
            new ChatCreateRequest(clientRequestId, QuestionType.CONCEPT, "질문", null, null);

    @Test
    void 동시에_먼저_저장된_대화가_있으면_그_대화를_돌려준다() {
        Chat existing = Chat.create(session, clientRequestId.toString(), QuestionType.CONCEPT, null, "질문", null, false);
        given(chatRepository.findByGuestSessionAndClientRequestId(eq(session), eq(clientRequestId.toString())))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existing));
        given(chatRepository.save(any(Chat.class))).willThrow(new DataIntegrityViolationException("duplicate"));

        ChatService.CreateResult result = chatService.create(session, request);

        assertThat(result.created()).isFalse();
        assertThat(result.response().type()).isEqualTo(QuestionType.CONCEPT);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 다른_이유의_제약_위반이면_예외를_그대로_던진다() {
        given(chatRepository.findByGuestSessionAndClientRequestId(any(), any())).willReturn(Optional.empty());
        given(chatRepository.save(any(Chat.class))).willThrow(new DataIntegrityViolationException("other"));

        assertThatThrownBy(() -> chatService.create(session, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
