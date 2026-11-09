package com.line1thon.team6.chat.service;

import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.chat.dto.OpenedHint;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ChatService.openNextHint: B의 힌트 API가 부르는 힌트 공개 (전이 T3) */
@SpringBootTest
class OpenNextHintTest {

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    GuestSession session;

    @BeforeEach
    void setUp() {
        session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
    }

    private Long preparingChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "질문", null, false)).getId();
    }

    private Long doneChat() {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        return chatId;
    }

    private int hintLevelOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getHintLevel();
    }

    private static void assertError(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    void 처음_요청하면_1단계_힌트를_연다() {
        Long chatId = doneChat();

        OpenedHint hint = chatService.openNextHint(session, chatId);

        assertThat(hint).isEqualTo(new OpenedHint(1, "힌트1", true, false));
        assertThat(hintLevelOf(chatId)).isEqualTo(1);
    }

    @Test
    void 두_번째_요청하면_2단계_힌트를_열고_기초_학습이_가능해진다() {
        Long chatId = doneChat();
        chatService.openNextHint(session, chatId);

        OpenedHint hint = chatService.openNextHint(session, chatId);

        assertThat(hint).isEqualTo(new OpenedHint(2, "힌트2", false, true));
        assertThat(hintLevelOf(chatId)).isEqualTo(2);
    }

    @Test
    void 이미_2단계면_올리지_않고_마지막_힌트를_다시_준다() {
        Long chatId = doneChat();
        chatService.openNextHint(session, chatId);
        chatService.openNextHint(session, chatId);

        OpenedHint hint = chatService.openNextHint(session, chatId);

        assertThat(hint).isEqualTo(new OpenedHint(2, "힌트2", false, true));
        assertThat(hintLevelOf(chatId)).isEqualTo(2);
    }

    @Test
    void 힌트를_봐도_잠금_상태는_LOCKED_그대로() {
        Long chatId = doneChat();

        chatService.openNextHint(session, chatId);

        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.LOCKED);
    }

    @Test
    void 선생성이_끝나지_않았으면_PREPARE_NOT_DONE() {
        Long chatId = preparingChat(session);

        assertError(() -> chatService.openNextHint(session, chatId), ChatErrorCode.PREPARE_NOT_DONE);
        assertThat(hintLevelOf(chatId)).isZero();
    }

    @Test
    void 생각을_제출했으면_ALREADY_SUBMITTED() {
        Long chatId = doneChat();
        lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        assertError(() -> chatService.openNextHint(session, chatId), ChatErrorCode.ALREADY_SUBMITTED);
        assertThat(hintLevelOf(chatId)).isZero();
    }

    @Test
    void 남의_대화는_CHAT_NOT_FOUND() {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChatId = preparingChat(other);

        assertError(() -> chatService.openNextHint(session, othersChatId), GlobalErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    void 동시에_두_번_눌러도_한_단계씩_올라간다() throws Exception {
        Long chatId = doneChat();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<OpenedHint>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return chatService.openNextHint(session, chatId);
                }));
            }
            ready.await();
            start.countDown();

            List<Integer> levels = new ArrayList<>();
            for (Future<OpenedHint> future : futures) {
                levels.add(future.get(10, TimeUnit.SECONDS).hintLevel());
            }

            // 잠금 없이 둘 다 0을 읽고 1로 쓰면 [1, 1]이 된다
            assertThat(levels).containsExactlyInAnyOrder(1, 2);
            assertThat(hintLevelOf(chatId)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
