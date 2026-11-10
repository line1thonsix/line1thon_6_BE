package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.analysis.dto.AnalysisResponse;
import com.line1thon.team6.analysis.dto.AnalysisRetryResponse;
import com.line1thon.team6.analysis.service.AnalysisQueryService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats/{chatId}/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisQueryService analysisQueryService;

    /** 분석 상태와 결과. 프론트는 PENDING 동안 2초마다 조회한다 */
    @GetMapping
    public AnalysisResponse get(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return analysisQueryService.get(session, chatId);
    }

    /** 실패한 분석을 다시 실행. 바디 없음 */
    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisRetryResponse retry(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return analysisQueryService.retry(session, chatId);
    }
}
