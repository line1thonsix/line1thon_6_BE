package com.line1thon.team6.session.service;

import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.dto.GuestSessionResponse;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestSessionService {

    private final GuestSessionRepository guestSessionRepository;

    /** 새 게스트 세션을 만들고 토큰(UUID)을 발급한다 */
    @Transactional
    public GuestSessionResponse create() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        return GuestSessionResponse.from(session);
    }

    /**
     * 전체 데이터 삭제. 이 세션과 세션의 모든 채팅(생각·분석 작업 포함)을 DB의 ON DELETE CASCADE로 한 번에 지운다.
     * 이후 같은 토큰으로 오는 요청은 세션이 없어서 401 INVALID_TOKEN.
     */
    @Transactional
    public void delete(GuestSession session) {
        guestSessionRepository.deleteSession(session.getId());
    }

    /** 토큰으로 세션을 찾는다. 토큰이 비어 있거나 없는 토큰이면 401 INVALID_TOKEN */
    public GuestSession getByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_TOKEN);
        }
        return guestSessionRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INVALID_TOKEN));
    }
}
