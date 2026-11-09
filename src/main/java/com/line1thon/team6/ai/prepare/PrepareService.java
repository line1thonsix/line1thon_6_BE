package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.service.AiCallContext;
import com.line1thon.team6.ai.service.StructuredAiCaller;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 질문 선생성: 칩·힌트·기준 답변을 AI로 만들어 chat에 저장한다.
 * 상태 변경은 A의 ChatService.completePrepare / failPrepare로만 한다 (PREPARING일 때만 바뀌는 조건부 UPDATE).
 * AI 호출 중에는 DB 트랜잭션을 잡지 않는다.
 */
@Slf4j
@Service
public class PrepareService {

    private final ChatRepository chatRepository;
    private final ChatService chatService;
    private final StructuredAiCaller aiCaller;
    private final PrepareOutputParser outputParser;
    private final TransactionTemplate readOnlyTx;

    public PrepareService(ChatRepository chatRepository, ChatService chatService, StructuredAiCaller aiCaller,
                          PrepareOutputParser outputParser, PlatformTransactionManager transactionManager) {
        this.chatRepository = chatRepository;
        this.chatService = chatService;
        this.aiCaller = aiCaller;
        this.outputParser = outputParser;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    public void prepare(Long chatId) {
        PrepareInput input = readOnlyTx.execute(status ->
                chatRepository.findById(chatId).map(PrepareInput::from).orElse(null));
        if (input == null) {
            // 커밋 직후 사용자가 대화를 지운 경우
            log.info("선생성 건너뜀: 채팅 없음 chatId={}", chatId);
            return;
        }
        if (input.prepareStatus() != PrepareStatus.PREPARING) {
            log.info("선생성 건너뜀: PREPARING이 아님 chatId={} prepareStatus={}", chatId, input.prepareStatus());
            return;
        }

        PrepareOutput output;
        try {
            AiCallContext context = new AiCallContext(AiCallType.PREPARE, input.guestSessionId(), PreparePrompt.VERSION);
            output = aiCaller.call(context, PreparePrompt.request(input), outputParser);
        } catch (AiException e) {
            log.warn("선생성 실패: chatId={} errorCode={} reason={}", chatId, e.getErrorCode(), e.getMessage());
            chatService.failPrepare(chatId);
            return;
        } catch (RuntimeException e) {
            // 예상하지 못한 오류여도 PREPARING에 멈춰 있지 않게 실패로 바꾼다
            log.error("선생성 중 예상하지 못한 오류: chatId={}", chatId, e);
            chatService.failPrepare(chatId);
            return;
        }
        chatService.completePrepare(chatId, output.choices(), output.hints(), output.referenceAnswer());
        log.info("선생성 완료: chatId={}", chatId);
    }
}
