package com.line1thon.team6.analysis.service;

import com.line1thon.team6.analysis.dto.BasicModeResponse;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.exception.AnalysisErrorCode;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기초 학습 시작 (전이 T5, B). 힌트 2단계를 본 뒤에만 기초 설명(BASIC) 작업을 만든다.
 * 작업 생성, 중복 클릭, 다른 길 확인은 A의 LockService.startJob이 채팅 행을 잠그고 한다 (PR #10 합의).
 */
@Service
@RequiredArgsConstructor
public class BasicModeService {

    private final ChatService chatService;
    private final LockService lockService;

    /**
     * <ul>
     *   <li>없거나 남의 대화: 404 CHAT_NOT_FOUND</li>
     *   <li>hintLevel이 2가 아님: 400 BASIC_MODE_NOT_ALLOWED</li>
     *   <li>생각을 이미 제출함: 409 ALREADY_SUBMITTED (startJob)</li>
     *   <li>이미 기초 학습을 시작함: 기존 작업, created = false (200, 중복 클릭)</li>
     * </ul>
     * hintLevel은 올라가기만 하고 분석 작업이 생기면 더 바뀌지 않아서, 행 잠금 전에 확인해도 결과가 같다.
     */
    public StartResult start(GuestSession session, Long chatId) {
        Chat chat = chatService.getOwnedChat(session, chatId);
        if (chat.getHintLevel() < Chat.MAX_HINT_LEVEL) {
            throw new BusinessException(AnalysisErrorCode.BASIC_MODE_NOT_ALLOWED);
        }
        LockService.StartResult started = lockService.startJob(session, chatId, AnalysisMode.BASIC);
        AnalysisJob job = started.job();
        return new StartResult(
                new BasicModeResponse(job.getId(), job.getMode(), job.getStatus(), started.chat().getStatus()),
                started.created());
    }

    /** created가 false면 이미 기초 학습을 시작한 대화 (200) */
    public record StartResult(BasicModeResponse response, boolean created) {
    }
}
