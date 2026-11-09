package com.line1thon.team6.analysis.service;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 분석 작업 조회·생성. 다른 기능(채팅의 LockService)은 repository 대신 이 서비스를 쓴다 (README 규칙).
 * 생성과 완료는 잠금 상태와 같이 바뀌어야 해서 LockService의 트랜잭션 안에서만 부른다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;

    @Transactional(readOnly = true)
    public Optional<AnalysisJob> findByChatId(Long chatId) {
        return analysisJobRepository.findByChatId(chatId);
    }

    /** PENDING 작업 생성. 채팅 행을 잠근 상태에서 작업이 없을 때만 부른다 (LockService.startJob) */
    @Transactional
    public AnalysisJob start(Chat chat, AnalysisMode mode) {
        return analysisJobRepository.save(AnalysisJob.start(chat, mode));
    }
}
