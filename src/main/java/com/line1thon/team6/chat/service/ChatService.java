package com.line1thon.team6.chat.service;

import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.dto.ChatCreateResponse;
import com.line1thon.team6.chat.dto.ChatDetailResponse;
import com.line1thon.team6.chat.dto.OpenedHint;
import com.line1thon.team6.chat.dto.PrepareRetryResponse;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.analysis.service.AnalysisJobService;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 이 시간이 넘도록 PREPARING이면 조회할 때 FAILED로 바꾼다 (API 명세서: 1분) */
    static final Duration PREPARE_TIMEOUT = Duration.ofMinutes(1);

    private final ChatRepository chatRepository;
    private final ThoughtRepository thoughtRepository;
    private final SecretMasker secretMasker;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisJobService analysisJobService;

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
     * 채팅 상세 조회 (새로고침 복구, 선생성 폴링). 없거나 남의 대화면 404 CHAT_NOT_FOUND.
     * 선생성 시작(재시도 포함) 후 1분이 넘도록 PREPARING이면 FAILED로 바꿔서 돌려준다 (멈춘 작업 복구).
     * B의 선생성은 최대 45초(15초 × 3번)라서 정상 작업은 1분 안에 끝난다.
     */
    @Transactional
    public ChatDetailResponse getDetail(GuestSession session, Long chatId) {
        Chat chat = getOwnedChat(session, chatId);
        LocalDateTime now = KoreaTime.now();
        if (chat.getPrepareStatus() == PrepareStatus.PREPARING
                && chatRepository.failPrepareIfTimedOut(chat.getId(), now.minus(PREPARE_TIMEOUT), now) > 0) {
            log.warn("선생성 시간 초과로 FAILED 처리: chatId={} prepareStartedAt={}", chatId, chat.getPrepareStartedAt());
            // 조건부 UPDATE가 영속성 컨텍스트를 비웠으니 바뀐 값을 다시 읽는다
            chat = getOwnedChat(session, chatId);
        }
        return ChatDetailResponse.from(chat,
                thoughtRepository.findByChat(chat).orElse(null),
                analysisJobService.findByChatId(chat.getId()).orElse(null));
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
     * PREPARING일 때만 바꾸는 조건부 UPDATE라서, 조회 API가 시간 초과로 FAILED로 바꾸는 것과 겹쳐도 덮어쓰지 않는다.
     * PREPARING이 아니면(예: 1분이 지나 이미 FAILED) 늦게 온 결과라서 버린다.
     * 리스너는 백그라운드라 예외를 받아도 처리할 곳이 없어서, 이 경우는 예외 대신 로그만 남긴다.
     */
    @Transactional
    public void completePrepare(Long chatId, List<String> choices, List<String> hints, String referenceAnswer) {
        Chat.validatePrepareResult(choices, hints, referenceAnswer);
        int updated = chatRepository.completePrepareIfPreparing(
                chatId, List.copyOf(choices), List.copyOf(hints), referenceAnswer, KoreaTime.now());
        if (updated == 0) {
            log.warn("선생성 결과를 버림: PREPARING이 아님 chatId={} prepareStatus={}", chatId, prepareStatusOf(chatId));
        }
    }

    /** 선생성 실패 → FAILED. B의 선생성 리스너가 호출한다. PREPARING이 아니면 무시 (조건부 UPDATE) */
    @Transactional
    public void failPrepare(Long chatId) {
        if (chatRepository.failPrepareIfPreparing(chatId, KoreaTime.now()) == 0) {
            log.warn("선생성 실패를 무시함: PREPARING이 아님 chatId={} prepareStatus={}", chatId, prepareStatusOf(chatId));
        }
    }

    /**
     * 힌트 한 단계 공개 (전이 T3). B의 POST /api/chats/{chatId}/hints가 호출한다.
     * 채팅 행을 잠근(SELECT ... FOR UPDATE) 상태에서 확인과 증가를 한 트랜잭션으로 한다.
     * 확인과 증가를 따로 하면 그 사이에 생각 제출로 작업이 생겨도 힌트가 열릴 수 있기 때문이다.
     * <ul>
     *   <li>없거나 남의 대화: 404 CHAT_NOT_FOUND</li>
     *   <li>선생성이 DONE이 아님: 409 PREPARE_NOT_DONE</li>
     *   <li>분석 작업이 있음 (생각 제출·기초 학습을 이미 함): 409 ALREADY_SUBMITTED</li>
     *   <li>이미 2단계: 바꾸지 않고 마지막 힌트를 다시 돌려줌 (200)</li>
     * </ul>
     * 잠금 상태(status)는 바꾸지 않는다.
     */
    @Transactional
    public OpenedHint openNextHint(GuestSession session, Long chatId) {
        Chat chat = chatRepository.findOwnedForUpdate(chatId, session)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND));
        if (chat.getPrepareStatus() != PrepareStatus.DONE) {
            throw new BusinessException(ChatErrorCode.PREPARE_NOT_DONE);
        }
        if (analysisJobService.findByChatId(chat.getId()).isPresent()) {
            throw new BusinessException(ChatErrorCode.ALREADY_SUBMITTED);
        }
        if (chat.getHintLevel() < Chat.MAX_HINT_LEVEL) {
            chat.openNextHint();
        }
        int hintLevel = chat.getHintLevel();
        return new OpenedHint(hintLevel, chat.currentHint(),
                hintLevel < Chat.MAX_HINT_LEVEL, hintLevel == Chat.MAX_HINT_LEVEL);
    }

    /** 로그용 현재 선생성 상태. 없는 채팅이면 "없음" */
    private Object prepareStatusOf(Long chatId) {
        return chatRepository.findById(chatId).<Object>map(Chat::getPrepareStatus).orElse("없음");
    }

    /** created가 false면 같은 전송 ID로 이미 있던 대화 (200) */
    public record CreateResult(ChatCreateResponse response, boolean created) {
    }
}
