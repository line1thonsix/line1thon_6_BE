package com.line1thon.team6.session.auth;

import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.global.exception.GlobalExceptionHandler;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import com.line1thon.team6.session.service.GuestSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GuestTokenInterceptorTest.TestController.class)
@Import({GuestTokenInterceptorTest.TestController.class, GlobalExceptionHandler.class, RequestIdFilter.class,
        GuestSessionService.class})
class GuestTokenInterceptorTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GuestSessionRepository guestSessionRepository;

    @Test
    void 토큰_헤더가_없으면_INVALID_TOKEN으로_응답한다() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.INVALID_TOKEN.getMessage()));
    }

    @Test
    void 토큰_헤더가_비어_있으면_INVALID_TOKEN으로_응답한다() throws Exception {
        mockMvc.perform(get("/api/test/me").header(GuestTokenInterceptor.HEADER, " "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 없는_토큰이면_INVALID_TOKEN으로_응답한다() throws Exception {
        given(guestSessionRepository.findByToken(anyString())).willReturn(Optional.empty());

        mockMvc.perform(get("/api/test/me").header(GuestTokenInterceptor.HEADER, "unknown-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 올바른_토큰이면_컨트롤러에_세션이_주입된다() throws Exception {
        given(guestSessionRepository.findByToken("valid-token"))
                .willReturn(Optional.of(new GuestSession("valid-token")));

        mockMvc.perform(get("/api/test/me").header(GuestTokenInterceptor.HEADER, "valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("valid-token"));
    }

    @Test
    void 세션_발급_API는_토큰_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_URL은_토큰이_없어도_API_NOT_FOUND로_응답한다() throws Exception {
        mockMvc.perform(get("/api/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_NOT_FOUND"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        String me(@CurrentSession GuestSession session) {
            return session.getToken();
        }

        @PostMapping("/api/sessions")
        void issue() {
        }
    }
}
