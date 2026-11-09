package com.line1thon.team6.chat.service;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.chat.dto.ThoughtSubmitRequest;
import com.line1thon.team6.chat.dto.ThoughtSubmitResponse;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생각 제출 (전이 T4). 검증 → 비교 분석 작업 시작 → 최초 작성본 저장을 한 트랜잭션에서 한다.
 * 채팅 status는 LOCKED 그대로이고, 분석이 끝나면 B가 LockService.completeJob을 불러 UNLOCKED_SELF로 바뀐다.
 */
@Service
@RequiredArgsConstructor
public class ThoughtService {

    private final ThoughtValidator thoughtValidator;
    private final LockService lockService;
    private final ThoughtRepository thoughtRepository;

    /**
     * 새로 제출하면 created = true (202). 이미 생각을 제출한 대화면 기존 작업을 돌려주고
     * 처음 쓴 생각은 그대로 둔다 (created = false, 200. 중복 클릭).
     */
    @Transactional
    public SubmitResult submit(GuestSession session, Long chatId, ThoughtSubmitRequest request) {
        thoughtValidator.validate(request.content());

        LockService.StartResult started = lockService.startJob(session, chatId, AnalysisMode.COMPARE);
        Chat chat = started.chat();
        Thought thought = started.created()
                ? thoughtRepository.save(Thought.create(chat, request.content(), request.selectedChoice()))
                : thoughtRepository.findByChat(chat).orElseThrow(() -> new IllegalStateException(
                        "비교 분석 작업은 있는데 생각이 없습니다: chatId=" + chat.getId()));

        AnalysisJob job = started.job();
        ThoughtSubmitResponse response = new ThoughtSubmitResponse(
                job.getId(), job.getMode(), job.getStatus(), chat.getStatus(), thought.isHintUsed());
        return new SubmitResult(response, started.created());
    }

    /** created가 false면 이미 제출한 대화 (200) */
    public record SubmitResult(ThoughtSubmitResponse response, boolean created) {
    }
}
