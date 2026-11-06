package com.line1thon.team6.global.exception;

import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.session.service.GuestSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class, RequestIdFilter.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    /** WebConfig의 토큰 인터셉터가 필요로 해서 등록 (/test/** 경로는 인증 대상이 아님) */
    @MockitoBean
    GuestSessionService guestSessionService;

    @Test
    void 비즈니스_예외는_정해진_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.CHAT_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.requestId").value(org.hamcrest.Matchers.startsWith("req_")));
    }

    @Test
    void 응답_본문의_requestId와_응답_헤더가_같다() throws Exception {
        var result = mockMvc.perform(get("/test/business"))
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andReturn();
        String headerId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("\"requestId\":\"" + headerId + "\"");
    }

    @Test
    void 검증_실패는_INVALID_INPUT과_필드_메시지로_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이름을 입력해 주세요."));
    }

    @Test
    void 잘못된_JSON은_INVALID_INPUT으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 필수_헤더가_없으면_INVALID_INPUT으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_URL은_API_NOT_FOUND로_응답한다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.API_NOT_FOUND.getMessage()));
    }

    @Test
    void 지원하지_않는_메서드는_METHOD_NOT_ALLOWED로_응답한다() throws Exception {
        mockMvc.perform(get("/test/valid"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.METHOD_NOT_ALLOWED.getMessage()));
    }

    @Test
    void 예상하지_못한_예외는_INTERNAL_ERROR로_응답하고_원인을_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.INTERNAL_ERROR.getMessage()));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/test/header")
        void header(@RequestHeader("X-Guest-Token") String token) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("내부 정보가 담긴 메시지");
        }
    }

    record NameRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
    }
}
