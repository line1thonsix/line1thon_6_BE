package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 키가 없으면 가짜 클라이언트로 비교 분석·기초 설명이 끝까지 진행된다 (로컬에서 키 없이 프론트 연동할 때).
 * 테스트 설정은 리스너가 꺼져 있어서 AnalysisService를 직접 부른다.
 */
@SpringBootTest
class FakeClientCompareTest {

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";

    @Autowired
    AnalysisService analysisService;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ThoughtRepository thoughtRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    /** 생각을 제출한 상태 (생각 저장 + PENDING 작업) */
    private AnalysisJob submittedJob() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "질문", null, false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        LockService.StartResult started = lockService.startJob(session, chatId, AnalysisMode.COMPARE);
        thoughtRepository.save(Thought.create(chatRepository.findById(chatId).orElseThrow(), THOUGHT, null));
        return started.job();
    }

    @Test
    void 키가_없으면_가짜_결과로_DONE이_되고_잠금이_풀린다() {
        AnalysisJob submitted = submittedJob();
        Long chatId = submitted.getChat().getId();

        analysisService.analyze(chatId, submitted.getId());

        AnalysisJob job = analysisJobRepository.findById(submitted.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getFindings()).isNotEmpty();
        assertThat(job.getFindings().getFirst().get("userQuote")).asString().isNotBlank();
        assertThat(THOUGHT).contains((String) job.getFindings().getFirst().get("userQuote"));
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.UNLOCKED_SELF);
    }

    @Test
    void 키가_없으면_기초_설명도_가짜_결과로_DONE이_되고_ASSISTED로_풀린다() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "질문", null, false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        AnalysisJob basic = lockService.startJob(session, chatId, AnalysisMode.BASIC).job();

        analysisService.analyze(chatId, basic.getId());

        AnalysisJob job = analysisJobRepository.findById(basic.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getBasicExplanation()).contains("summary").contains("exampleCode");
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.UNLOCKED_ASSISTED);
    }

    @Test
    void 이미_끝난_작업이면_다시_분석하지_않는다() {
        AnalysisJob submitted = submittedJob();
        Long chatId = submitted.getChat().getId();
        analysisService.analyze(chatId, submitted.getId());
        AnalysisJob done = analysisJobRepository.findById(submitted.getId()).orElseThrow();

        analysisService.analyze(chatId, submitted.getId());

        AnalysisJob again = analysisJobRepository.findById(submitted.getId()).orElseThrow();
        assertThat(again.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(again.getCompletedAt()).isEqualTo(done.getCompletedAt());
    }

    @Test
    void 다른_작업_id나_없는_채팅이면_아무것도_하지_않는다() {
        AnalysisJob submitted = submittedJob();

        analysisService.analyze(submitted.getChat().getId(), Long.MAX_VALUE);
        analysisService.analyze(Long.MAX_VALUE, submitted.getId());

        assertThat(analysisJobRepository.findById(submitted.getId()).orElseThrow().getStatus())
                .isEqualTo(AnalysisStatus.PENDING);
    }
}
