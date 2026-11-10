package com.line1thon.team6.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.global.common.KoreaTime;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/chats/{chatId} 응답 (새로고침 복구).
 * 기준 답변(referenceAnswer)과 비교 결과는 넣지 않는다. 결과는 B의 GET /analysis에서 조회한다.
 * 생각을 제출하지 않았으면 thought, selectedChoice는 null, hintUsed는 false.
 * 분석 작업이 없으면 analysis는 null.
 */
public record ChatDetailResponse(
        Long chatId,
        QuestionType type,
        String question,
        String code,
        boolean secretMasked,
        ChatStatus status,
        PrepareStatus prepareStatus,
        List<String> choices,
        int hintLevel,
        List<Hint> hints,
        boolean hasNextHint,
        boolean basicModeAvailable,
        String thought,
        Integer selectedChoice,
        boolean hintUsed,
        Analysis analysis,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime createdAt
) {

    private static final int MAX_HINT_LEVEL = 2;

    /** 공개된 힌트 한 단계 */
    public record Hint(int level, String text) {
    }

    /** 이 채팅의 분석 작업 (채팅당 1개). 완료나 실패한 작업도 그대로 내보낸다 */
    public record Analysis(Long jobId, AnalysisMode mode, AnalysisStatus status, int retryCount, int remainingRetries) {

        static Analysis from(AnalysisJob job) {
            return new Analysis(job.getId(), job.getMode(), job.getStatus(), job.retryCount(), job.remainingRetries());
        }
    }

    /**
     * @param thought 제출한 생각. 아직 제출 안 했으면 null
     * @param job     분석 작업. 생각 제출이나 기초 학습 전이면 null
     */
    public static ChatDetailResponse from(Chat chat, Thought thought, AnalysisJob job) {
        int hintLevel = chat.getHintLevel();
        return new ChatDetailResponse(
                chat.getId(),
                chat.getType(),
                chat.getQuestion(),
                chat.getCode(),
                chat.isSecretMasked(),
                chat.getStatus(),
                chat.getPrepareStatus(),
                // 칩은 선생성이 끝났을 때만
                chat.getPrepareStatus() == PrepareStatus.DONE ? chat.getChoices() : null,
                hintLevel,
                openedHints(chat.getHints(), hintLevel),
                hintLevel < MAX_HINT_LEVEL,
                // 작업은 채팅당 1개라서, 생각 제출이나 기초 학습으로 작업이 생겼으면 기초 학습은 더 못 한다
                hintLevel == MAX_HINT_LEVEL && job == null,
                thought == null ? null : thought.getContent(),
                thought == null ? null : thought.getSelectedChoice(),
                thought != null && thought.isHintUsed(),
                job == null ? null : Analysis.from(job),
                KoreaTime.toOffset(chat.getCreatedAt()));
    }

    /** hintLevel 단계까지만. 아직 안 연 힌트는 내보내지 않는다 */
    private static List<Hint> openedHints(List<String> hints, int hintLevel) {
        List<Hint> opened = new ArrayList<>();
        if (hints == null) {
            return opened;
        }
        for (int level = 1; level <= Math.min(hintLevel, hints.size()); level++) {
            opened.add(new Hint(level, hints.get(level - 1)));
        }
        return opened;
    }
}
