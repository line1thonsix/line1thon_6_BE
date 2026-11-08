package com.line1thon.team6.chat.service;

import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.dto.ChatCreateResponse;
import com.line1thon.team6.chat.dto.PrepareRetryResponse;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
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

    /** 내 대화를 찾는다. 없거나 남의 대화면 404 CHAT_NOT_FOUND (둘을 구분하지 않음) */
    @Transactional(readOnly = true)
    public Chat getOwnedChat(GuestSession session, Long chatId) {
        return chatRepository.findByIdAndGuestSession(chatId, session)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND));
    }

    /**
     * 선생성 재시도 (전이 T2). FAILED일 때만 PREPARING으로 바꾸고 이벤트를 발행한다.
     * FAILED가 아니면(이미 완료, 진행 중, 중복 클릭) 409 PREPARE_NOT_FAILED.
     */
    @Transactional
    public PrepareRetryResponse retryPrepare(GuestSession session, Long chatId) {
        Chat chat = getOwnedChat(session, chatId);
        if (chatRepository.restartPrepareIfFailed(chat.getId(), KoreaTime.now()) == 0) {
            throw new BusinessException(ChatErrorCode.PREPARE_NOT_FAILED);
        }
        eventPublisher.publishEvent(new PrepareRequested(chat.getId()));
        return new PrepareRetryResponse(chat.getId(), chat.getStatus(), PrepareStatus.PREPARING);
    }

    /**
     * 선생성 성공 결과 저장 → DONE. B의 선생성 리스너가 호출한다.
     * PREPARING이 아니면(예: 1분이 지나 이미 FAILED) 늦게 온 결과라서 버린다.
     * 리스너는 백그라운드라 예외를 받아도 처리할 곳이 없어서, 이 경우는 예외 대신 로그만 남긴다.
     */
    @Transactional
    public void completePrepare(Long chatId, List<String> choices, List<String> hints, String referenceAnswer) {
        chatRepository.findById(chatId).ifPresentOrElse(chat -> {
            if (!chat.completePrepare(choices, hints, referenceAnswer)) {
                log.warn("선생성 결과를 버림: PREPARING이 아님 chatId={} prepareStatus={}", chatId, chat.getPrepareStatus());
            }
        }, () -> log.warn("선생성 결과를 버림: 없는 채팅 chatId={}", chatId));
    }

    /** 선생성 실패 → FAILED. B의 선생성 리스너가 호출한다. PREPARING이 아니면 무시 */
    @Transactional
    public void failPrepare(Long chatId) {
        chatRepository.findById(chatId).ifPresentOrElse(chat -> {
            if (!chat.failPrepare()) {
                log.warn("선생성 실패를 무시함: PREPARING이 아님 chatId={} prepareStatus={}", chatId, chat.getPrepareStatus());
            }
        }, () -> log.warn("선생성 실패를 무시함: 없는 채팅 chatId={}", chatId));
    }

    /** created가 false면 같은 전송 ID로 이미 있던 대화 (200) */
    public record CreateResult(ChatCreateResponse response, boolean created) {
    }
}
