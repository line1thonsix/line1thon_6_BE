package com.line1thon.team6.chat.service;

import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.dto.ChatCreateResponse;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final SecretMasker secretMasker;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * 질문 생성. 같은 전송 ID로 이미 만든 대화가 있으면 새로 만들지 않고 그 대화를 돌려준다.
     * 같은 요청 두 개가 동시에 들어와 UNIQUE 제약에 걸리면, 먼저 저장된 대화를 찾아 돌려준다.
     * 저장 트랜잭션이 실패한 뒤 다시 조회해야 해서 @Transactional 대신 TransactionTemplate을 쓴다.
     */
    public CreateResult create(GuestSession session, ChatCreateRequest request) {
        String clientRequestId = request.clientRequestId().toString();
        try {
            return transactionTemplate.execute(status -> chatRepository
                    .findByGuestSessionAndClientRequestId(session, clientRequestId)
                    .map(existing -> new CreateResult(ChatCreateResponse.from(existing), false))
                    .orElseGet(() -> new CreateResult(ChatCreateResponse.from(save(session, request)), true)));
        } catch (DataIntegrityViolationException e) {
            return chatRepository.findByGuestSessionAndClientRequestId(session, clientRequestId)
                    .map(existing -> new CreateResult(ChatCreateResponse.from(existing), false))
                    .orElseThrow(() -> e);
        }
    }

    /** 저장 후 같은 트랜잭션에서 선생성 이벤트 발행. AI는 이 API에서 호출하지 않는다 */
    private Chat save(GuestSession session, ChatCreateRequest request) {
        SecretMasker.Result masked = secretMasker.mask(request.code());
        Chat chat = chatRepository.save(Chat.create(session, request.clientRequestId().toString(), request.type(),
                request.exampleId(), request.question(), masked.code(), masked.masked()));
        eventPublisher.publishEvent(new PrepareRequested(chat.getId()));
        return chat;
    }

    /** created가 false면 같은 전송 ID로 이미 있던 대화 (200) */
    public record CreateResult(ChatCreateResponse response, boolean created) {
    }
}
