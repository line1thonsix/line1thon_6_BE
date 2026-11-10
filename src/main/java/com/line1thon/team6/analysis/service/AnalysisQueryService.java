package com.line1thon.team6.analysis.service;

import com.line1thon.team6.ai.analysis.AnalysisResultWriter;
import com.line1thon.team6.analysis.dto.AnalysisResponse;
import com.line1thon.team6.analysis.dto.AnalysisRetryResponse;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.exception.AnalysisErrorCode;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/** 분석 조회·재시도 (B). 상태를 바꾸는 일은 채팅 행을 잠근 LockService, AnalysisResultWriter에 맡긴다 */
@Service
public class AnalysisQueryService {

    /**
     * 이 시간이 넘도록 PENDING이면 조회할 때 AI_TIMEOUT으로 실패 처리한다 (PR #14 리뷰에서 합의).
     * 정상 작업은 최대 45초(15초 × 3번)라 걸리지 않고, 서버 재시작 등으로 사라진 작업만 걸린다.
     */
    static final Duration ANALYSIS_TIMEOUT = Duration.ofMinutes(2);

    private final ChatService chatService;
    private final AnalysisJobService analysisJobService;
    private final ThoughtRepository thoughtRepository;
    private final AnalysisResultWriter resultWriter;
    private final LockService lockService;
    private final TransactionTemplate readOnlyTx;

    public AnalysisQueryService(ChatService chatService, AnalysisJobService analysisJobService,
                                ThoughtRepository thoughtRepository, AnalysisResultWriter resultWriter,
                                LockService lockService, PlatformTransactionManager transactionManager) {
        this.chatService = chatService;
        this.analysisJobService = analysisJobService;
        this.thoughtRepository = thoughtRepository;
        this.resultWriter = resultWriter;
        this.lockService = lockService;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    /**
     * 분석 상태와 결과. PENDING / DONE / FAILED 모두 200.
     * 없거나 남의 대화면 404 CHAT_NOT_FOUND, 분석 작업이 없으면 404 ANALYSIS_NOT_FOUND.
     */
    public AnalysisResponse get(GuestSession session, Long chatId) {
        LocalDateTime threshold = KoreaTime.now().minus(ANALYSIS_TIMEOUT);
        View view = read(session, chatId, threshold);
        // 폴링마다 행을 잠그지 않도록, 잠금 없이 읽은 값으로 오래됐을 때만 잠그고 다시 확인한다
        if (view.stale() && resultWriter.failIfStale(chatId, view.response().jobId(), threshold)) {
            view = read(session, chatId, threshold);
        }
        return view.response();
    }

    /** FAILED 작업을 다시 실행한다. 202, 결과는 GET /analysis 폴링 */
    public AnalysisRetryResponse retry(GuestSession session, Long chatId) {
        return AnalysisRetryResponse.from(lockService.retryJob(session, chatId));
    }

    private View read(GuestSession session, Long chatId, LocalDateTime threshold) {
        return readOnlyTx.execute(status -> {
            Chat chat = chatService.getOwnedChat(session, chatId);
            AnalysisJob job = analysisJobService.findByChatId(chat.getId())
                    .orElseThrow(() -> new BusinessException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
            return new View(AnalysisResponse.of(chat, job, thoughtRepository.findByChat(chat).orElse(null)),
                    job.isStale(threshold));
        });
    }

    /** @param stale 잠금 없이 읽었을 때 오래 멈춘 PENDING이었는지 */
    private record View(AnalysisResponse response, boolean stale) {
    }
}
