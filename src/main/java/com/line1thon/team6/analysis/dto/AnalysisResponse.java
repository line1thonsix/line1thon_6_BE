package com.line1thon.team6.analysis.dto;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.Thought;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * GET /api/chats/{chatId}/analysis 응답 (Notion 분석 결과 조회 명세).
 * 항상 같은 모양으로 응답하고, 지금 상태에서 보여 줄 수 없는 값은 null이다.
 * <ul>
 *   <li>잠금 가드: 기준 답변(referenceAnswer), findings, basicExplanation은 잠금이 풀린 뒤(DONE)에만 넣는다</li>
 *   <li>errorCode는 FAILED일 때만</li>
 *   <li>retryCount, remainingRetries는 재시도 버튼 표시용 (채팅 상세의 analysis와 같은 값)</li>
 * </ul>
 */
public record AnalysisResponse(
        Long jobId,
        AnalysisStatus analysisStatus,
        AnalysisMode mode,
        String unlockType,
        String myThought,
        String referenceAnswer,
        List<Finding> findings,
        String basicExplanation,
        String notice,
        boolean isDemo,
        String errorCode,
        int retryCount,
        int remainingRetries
) {

    public static final String NOTICE = "AI의 답변은 정답이 아닌 참고 관점입니다. AI도 오류를 범하거나 중요한 조건을 놓칠 수 있어요.";

    /**
     * 비교 항목 하나. id는 배열 순서(0부터)다. 이의 제기의 finding_index와 같은 값이다 (PR #14 리뷰).
     *
     * @param aiPerspective 항목별 "AI" 칸 (Figma 08). 기준 답변 전체는 최상위 referenceAnswer
     */
    public record Finding(int id, String category, String title, String userQuote, String aiPerspective,
                          String detail, String followUpQuestion) {

        static Finding of(int index, Map<String, Object> item) {
            return new Finding(index, text(item, "category"), text(item, "title"), text(item, "userQuote"),
                    text(item, "aiPerspective"), text(item, "detail"), text(item, "followUpQuestion"));
        }

        private static String text(Map<String, Object> item, String key) {
            Object value = item.get(key);
            return value == null ? null : value.toString();
        }
    }

    /** @param thought 제출한 생각. 기초 학습이면 null */
    public static AnalysisResponse of(Chat chat, AnalysisJob job, Thought thought) {
        boolean revealed = job.getStatus() == AnalysisStatus.DONE && chat.getStatus() != ChatStatus.LOCKED;
        return new AnalysisResponse(
                job.getId(),
                job.getStatus(),
                job.getMode(),
                unlockType(chat.getStatus()),
                thought == null ? null : thought.getContent(),
                revealed ? chat.getReferenceAnswer() : null,
                revealed ? findings(job.getFindings()) : null,
                revealed ? job.getBasicExplanation() : null,
                NOTICE,
                job.isDemo(),
                job.getStatus() == AnalysisStatus.FAILED ? job.getErrorCode() : null,
                job.retryCount(),
                job.remainingRetries());
    }

    private static String unlockType(ChatStatus status) {
        return switch (status) {
            case LOCKED -> null;
            case UNLOCKED_SELF -> "SELF";
            case UNLOCKED_ASSISTED -> "ASSISTED";
        };
    }

    /** 기초 설명(BASIC)은 비교 항목이 없어 빈 배열 */
    private static List<Finding> findings(List<Map<String, Object>> items) {
        if (items == null) {
            return List.of();
        }
        return IntStream.range(0, items.size()).mapToObj(i -> Finding.of(i, items.get(i))).toList();
    }
}
