package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.analysis.dto.AnalysisResponse;
import com.line1thon.team6.analysis.dto.AnalysisRetryResponse;
import com.line1thon.team6.analysis.service.AnalysisQueryService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 분석", description = "분석 결과 조회, 분석 재시도")
@RestController
@RequestMapping("/api/chats/{chatId}/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisQueryService analysisQueryService;

    /** 분석 상태와 결과. 프론트는 PENDING 동안 2초마다 조회한다 */
    @Operation(summary = "분석 결과 조회",
            description = "비교 분석(또는 기초 설명) 작업의 상태와 결과를 돌려줍니다. PENDING / DONE / FAILED 모두 200이고, "
                    + "잠금이 풀리기 전에는 결과 필드(referenceAnswer, findings, basicExplanation)가 null입니다. PENDING이면 2초마다 조회합니다.")
    @GetMapping
    public AnalysisResponse get(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return analysisQueryService.get(session, chatId);
    }

    /** 실패한 분석을 다시 실행. 바디 없음 */
    @Operation(summary = "분석 재시도",
            description = "FAILED인 분석(또는 기초 설명) 작업을 같은 입력으로 다시 실행합니다(202). 첫 시도 포함 최대 3번. "
                    + "진행 중이거나 끝났으면 409 NOT_RETRYABLE, 횟수를 다 썼으면 409 RETRY_LIMIT_EXCEEDED.")
    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisRetryResponse retry(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return analysisQueryService.retry(session, chatId);
    }
}
