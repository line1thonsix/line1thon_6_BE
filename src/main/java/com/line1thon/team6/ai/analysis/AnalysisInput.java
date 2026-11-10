package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;

/**
 * 분석에 필요한 내용. 트랜잭션 안에서 엔티티를 읽어 만든다 (지연 로딩된 채팅·세션 포함).
 *
 * @param thought 제출한 생각. 기초 학습(BASIC)이면 null
 */
public record AnalysisInput(
        Long chatId,
        Long jobId,
        Long guestSessionId,
        AnalysisMode mode,
        AnalysisStatus status,
        QuestionType type,
        String question,
        String code,
        String referenceAnswer,
        String thought
) {

    public static AnalysisInput of(AnalysisJob job, Thought thought) {
        Chat chat = job.getChat();
        return new AnalysisInput(chat.getId(), job.getId(), chat.getGuestSession().getId(), job.getMode(),
                job.getStatus(), chat.getType(), chat.getQuestion(), chat.getCode(), chat.getReferenceAnswer(),
                thought == null ? null : thought.getContent());
    }
}
