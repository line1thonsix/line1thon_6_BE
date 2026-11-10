package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.service.AiCallContext;
import com.line1thon.team6.ai.service.StructuredAiCaller;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.service.AnalysisJobService;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 분석 작업 실행: 생각 제출(COMPARE)이나 기초 학습(BASIC)으로 PENDING이 된 작업의 AI를 부르고 결과를 저장한다.
 * AI 호출 중에는 DB 트랜잭션을 잡지 않는다. 어떤 오류가 나도 작업이 PENDING에 멈추지 않게 FAILED로 바꾼다.
 */
@Slf4j
@Service
public class AnalysisService {

    private final AnalysisJobService analysisJobService;
    private final ThoughtRepository thoughtRepository;
    private final StructuredAiCaller aiCaller;
    private final CompareOutputParser compareOutputParser;
    private final AnalysisResultWriter resultWriter;
    private final TransactionTemplate readOnlyTx;

    public AnalysisService(AnalysisJobService analysisJobService, ThoughtRepository thoughtRepository,
                           StructuredAiCaller aiCaller, CompareOutputParser compareOutputParser,
                           AnalysisResultWriter resultWriter, PlatformTransactionManager transactionManager) {
        this.analysisJobService = analysisJobService;
        this.thoughtRepository = thoughtRepository;
        this.aiCaller = aiCaller;
        this.compareOutputParser = compareOutputParser;
        this.resultWriter = resultWriter;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    public void analyze(Long chatId, Long jobId) {
        AnalysisInput input = readInput(chatId, jobId);
        if (input == null) {
            // 커밋 직후 사용자가 대화를 지운 경우
            log.info("분석 건너뜀: 작업 없음 chatId={} jobId={}", chatId, jobId);
            return;
        }
        if (input.status() != AnalysisStatus.PENDING) {
            log.info("분석 건너뜀: PENDING이 아님 chatId={} jobId={} status={}", chatId, jobId, input.status());
            return;
        }
        switch (input.mode()) {
            case COMPARE -> compare(input);
            // TODO 기초 학습 API(P4 합의 후)를 만들 때 기초 설명 호출을 추가한다. 지금은 BASIC 작업을 만드는 API가 없다
            case BASIC -> log.warn("기초 설명은 아직 구현되지 않음: chatId={} jobId={}", chatId, jobId);
        }
    }

    private AnalysisInput readInput(Long chatId, Long jobId) {
        return readOnlyTx.execute(status -> analysisJobService.findByChatId(chatId)
                .filter(job -> job.getId().equals(jobId))
                .map(job -> AnalysisInput.of(job, thoughtRepository.findByChat(job.getChat()).orElse(null)))
                .orElse(null));
    }

    private void compare(AnalysisInput input) {
        List<CompareFinding> findings;
        try {
            if (input.thought() == null) {
                throw new IllegalStateException("비교 분석 작업에 제출한 생각이 없음");
            }
            AiCallContext context = new AiCallContext(AiCallType.COMPARE, input.guestSessionId(), ComparePrompt.VERSION);
            findings = aiCaller.call(context, ComparePrompt.request(input),
                    compareOutputParser.forThought(input.thought()));
        } catch (AiException e) {
            log.warn("비교 분석 실패: chatId={} jobId={} errorCode={} reason={}",
                    input.chatId(), input.jobId(), e.getErrorCode(), e.getMessage());
            fail(input, e.getErrorCode());
            return;
        } catch (RuntimeException e) {
            log.error("비교 분석 중 예상하지 못한 오류: chatId={} jobId={}", input.chatId(), input.jobId(), e);
            fail(input, AiErrorCode.AI_API_ERROR);
            return;
        }

        try {
            if (resultWriter.completeCompare(input.chatId(), input.jobId(), findings)) {
                log.info("비교 분석 완료: chatId={} jobId={} findings={}", input.chatId(), input.jobId(), findings.size());
            }
        } catch (RuntimeException e) {
            // 저장 트랜잭션이 롤백돼 작업은 아직 PENDING이다
            log.error("비교 분석 결과 저장 실패: chatId={} jobId={}", input.chatId(), input.jobId(), e);
            fail(input, AiErrorCode.AI_API_ERROR);
        }
    }

    private void fail(AnalysisInput input, AiErrorCode errorCode) {
        try {
            resultWriter.fail(input.chatId(), input.jobId(), errorCode);
        } catch (RuntimeException e) {
            log.error("분석 실패 상태 저장 실패: chatId={} jobId={}", input.chatId(), input.jobId(), e);
        }
    }
}
