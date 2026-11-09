package com.line1thon.team6.ai.service;

import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.ai.client.LlmMessage;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.config.AiProperties;
import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class StructuredAiCallerTest {

    private static final AiCallContext CONTEXT = new AiCallContext(AiCallType.PREPARE, 1L, "v-test");
    private static final LlmRequest REQUEST = new LlmRequest(AiCallType.PREPARE, "system",
            List.of(LlmMessage.user("질문")), Map.of(), 100);
    /** "ok"만 통과시키는 파서 */
    private static final AiOutputParser<String> PARSER = text -> {
        if (!"ok".equals(text)) {
            throw new AiOutputInvalidException(List.of("형식 오류: " + text));
        }
        return text;
    };

    private final AiUsageRecorder recorder = mock(AiUsageRecorder.class);
    private final ScriptedClient client = new ScriptedClient();
    private final StructuredAiCaller caller = new StructuredAiCaller(client, recorder,
            new AiProperties("key", "m", "http://x", Duration.ofSeconds(15), 0.2, 512, 3));

    @Test
    void 첫_응답이_맞으면_한_번만_부르고_성공으로_기록한다() {
        client.respond("ok");

        assertThat(caller.call(CONTEXT, REQUEST, PARSER)).isEqualTo("ok");

        assertThat(client.requests).hasSize(1);
        verify(recorder).success(eq(CONTEXT), eq("scripted"), any(), anyLong());
        verifyNoMoreInteractions(recorder);
    }

    @Test
    void 형식이_틀리면_이전_응답과_수정_지시를_붙여_다시_부른다() {
        client.respond("bad");
        client.respond("ok");

        assertThat(caller.call(CONTEXT, REQUEST, PARSER)).isEqualTo("ok");

        assertThat(client.requests).hasSize(2);
        List<LlmMessage> retried = client.requests.get(1).messages();
        assertThat(retried).hasSize(3);
        assertThat(retried.get(1)).isEqualTo(LlmMessage.model("bad"));
        assertThat(retried.get(2).role()).isEqualTo(LlmMessage.Role.USER);
        assertThat(retried.get(2).text()).contains("- 형식 오류: bad").contains("올바른 JSON 객체만 반환하세요.");
        verify(recorder).failure(eq(CONTEXT), eq("scripted"), any(), anyLong(), eq(AiErrorCode.AI_INVALID_JSON));
        verify(recorder).success(eq(CONTEXT), eq("scripted"), any(), anyLong());
    }

    @Test
    void 세_번_모두_틀리면_AI_INVALID_JSON() {
        client.respond("bad1");
        client.respond("bad2");
        client.respond("bad3");

        assertThatThrownBy(() -> caller.call(CONTEXT, REQUEST, PARSER))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_INVALID_JSON));

        assertThat(client.requests).hasSize(3);
        verify(recorder, times(3)).failure(eq(CONTEXT), eq("scripted"), any(), anyLong(),
                eq(AiErrorCode.AI_INVALID_JSON));
    }

    @Test
    void 시간_초과는_다시_부르지_않고_그대로_던진다() {
        client.fail(new AiException(AiErrorCode.AI_TIMEOUT, "timeout"));

        assertThatThrownBy(() -> caller.call(CONTEXT, REQUEST, PARSER))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_TIMEOUT));

        assertThat(client.requests).hasSize(1);
        verify(recorder).failure(eq(CONTEXT), eq("scripted"), isNull(), anyLong(), eq(AiErrorCode.AI_TIMEOUT));
    }

    /** 미리 정한 순서대로 응답하거나 실패하는 클라이언트 */
    static class ScriptedClient implements LlmClient {

        final List<LlmRequest> requests = new ArrayList<>();
        private final Deque<Object> script = new ArrayDeque<>();

        void respond(String text) {
            script.add(new LlmResponse(text, 10, 20, "STOP"));
        }

        void fail(AiException e) {
            script.add(e);
        }

        @Override
        public LlmResponse generate(LlmRequest request) {
            requests.add(request);
            Object next = script.removeFirst();
            if (next instanceof AiException e) {
                throw e;
            }
            return (LlmResponse) next;
        }

        @Override
        public String model() {
            return "scripted";
        }
    }
}
