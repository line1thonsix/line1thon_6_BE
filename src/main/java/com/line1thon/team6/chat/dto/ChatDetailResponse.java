package com.line1thon.team6.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.global.common.KoreaTime;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/chats/{chatId} 응답 (새로고침 복구).
 * 기준 답변(referenceAnswer)과 비교 결과는 넣지 않는다. 결과는 B의 GET /analysis에서 조회한다.
 * thought, selectedChoice, hintUsed, analysis는 생각 제출과 분석 작업 테이블이 생기면 채운다 (지금은 null/false).
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

    /** 진행 중인 분석 작업. 분석 작업 테이블이 생기면 채운다 */
    public record Analysis(Long jobId, String mode, String status, int retryCount, int remainingRetries) {
    }

    public static ChatDetailResponse from(Chat chat) {
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
                // TODO 분석 작업 테이블이 생기면 "진행 중인 분석 작업이 없을 때" 조건 추가
                hintLevel == MAX_HINT_LEVEL,
                null,
                null,
                false,
                null,
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
