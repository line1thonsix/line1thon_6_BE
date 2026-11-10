package com.line1thon.team6.session.controller;

import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.global.exception.GlobalExceptionHandler;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import com.line1thon.team6.session.service.GuestSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GuestSessionController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GuestSessionService.class})
class GuestSessionControllerTest {

    private static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    /** 예: 2026-11-05T13:00:00+09:00 (소수점 초 없음) */
    private static final String KOREA_TIME_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00$";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GuestSessionRepository guestSessionRepository;

    @Test
    void 토큰_없이_호출하면_201과_새_토큰을_돌려준다() throws Exception {
        given(guestSessionRepository.save(any(GuestSession.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestToken").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(KOREA_TIME_PATTERN)));
    }

    @Test
    void 응답한_토큰이_DB에_저장된다() throws Exception {
        given(guestSessionRepository.save(any(GuestSession.class))).willAnswer(inv -> inv.getArgument(0));

        MvcResult result = mockMvc.perform(post("/api/sessions")).andReturn();

        ArgumentCaptor<GuestSession> captor = ArgumentCaptor.forClass(GuestSession.class);
        verify(guestSessionRepository).save(captor.capture());
        assertThat(result.getResponse().getContentAsString())
                .contains("\"guestToken\":\"" + captor.getValue().getToken() + "\"");
    }

    @Test
    void 호출할_때마다_다른_토큰을_발급한다() throws Exception {
        given(guestSessionRepository.save(any(GuestSession.class))).willAnswer(inv -> inv.getArgument(0));

        String first = mockMvc.perform(post("/api/sessions")).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/sessions")).andReturn().getResponse().getContentAsString();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void GET으로_호출하면_METHOD_NOT_ALLOWED로_응답한다() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
