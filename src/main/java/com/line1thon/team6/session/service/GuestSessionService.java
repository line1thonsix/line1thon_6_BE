package com.line1thon.team6.session.service;

import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestSessionService {

    private final GuestSessionRepository guestSessionRepository;

    /** 토큰으로 세션을 찾는다. 토큰이 비어 있거나 없는 토큰이면 401 INVALID_TOKEN */
    public GuestSession getByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_TOKEN);
        }
        return guestSessionRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INVALID_TOKEN));
    }
}
