package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.config.AiProperties;
import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로컬 HTTP 서버를 Gemini 대신 띄워 요청 형식, 응답 해석, 오류 구분을 확인한다 */
class GeminiClientTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();

    private HttpServer server;
    private volatile int status;
    private volatile String responseBody;
    private volatile long delayMs;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GeminiClient client(Duration timeout) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        return new GeminiClient(new AiProperties("test-key", "gemini-2.5-flash", baseUrl, timeout, 0.2, 512, 3),
                jsonMapper);
    }

    private static LlmRequest request() {
        return new LlmRequest(AiCallType.PREPARE, "시스템 규칙",
                List.of(LlmMessage.user("질문"), LlmMessage.model("이전 응답"), LlmMessage.user("수정 지시")),
                Map.of("type", "OBJECT"), 1500);
    }

    @Test
    void 설정대로_요청하고_생각을_뺀_출력과_토큰을_읽는다() {
        status = 200;
        responseBody = """
                {"candidates": [{"content": {"parts": [
                    {"text": "생각 내용", "thought": true},
                    {"text": "{\\"a\\":"}, {"text": "1}"}]},
                  "finishReason": "STOP"}],
                 "usageMetadata": {"promptTokenCount": 228, "candidatesTokenCount": 262, "thoughtsTokenCount": 405}}
                """;

        LlmResponse response = client(Duration.ofSeconds(5)).generate(request());

        assertThat(response.text()).isEqualTo("{\"a\":1}");
        assertThat(response.inputTokens()).isEqualTo(228);
        assertThat(response.outputTokens()).isEqualTo(262 + 405);
        assertThat(response.finishReason()).isEqualTo("STOP");

        assertThat(receivedPath.get()).isEqualTo("/v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(receivedKey.get()).isEqualTo("test-key");
        JsonNode body = jsonMapper.readTree(receivedBody.get());
        assertThat(body.at("/systemInstruction/parts/0/text").asString()).isEqualTo("시스템 규칙");
        assertThat(body.at("/contents/1/role").asString()).isEqualTo("model");
        assertThat(body.at("/contents/2/parts/0/text").asString()).isEqualTo("수정 지시");
        JsonNode config = body.get("generationConfig");
        assertThat(config.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(1500);
        assertThat(config.get("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(config.at("/thinkingConfig/thinkingBudget").asInt()).isEqualTo(512);
        assertThat(config.at("/responseSchema/type").asString()).isEqualTo("OBJECT");
    }

    @Test
    void 출력이_잘리면_finishReason을_그대로_돌려준다() {
        status = 200;
        responseBody = """
                {"candidates": [{"content": {"parts": [{"text": "{\\"choices\\": ["}]}, "finishReason": "MAX_TOKENS"}]}
                """;

        LlmResponse response = client(Duration.ofSeconds(5)).generate(request());

        assertThat(response.finishReason()).isEqualTo("MAX_TOKENS");
        assertThat(response.outputTokens()).isNull();
    }

    @Test
    void 사용량_초과_429는_AI_API_ERROR() {
        status = 429;
        responseBody = "{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\"}}";

        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generate(request()))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_API_ERROR);
                    assertThat(e.getMessage()).contains("429");
                });
    }

    @Test
    void 제한_시간_안에_응답하지_않으면_AI_TIMEOUT() {
        status = 200;
        responseBody = "{}";
        delayMs = 1_000;

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).generate(request()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_TIMEOUT));
    }

    @Test
    void 서버에_연결할_수_없으면_AI_API_ERROR() {
        GeminiClient client = client(Duration.ofSeconds(2));
        server.stop(0);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_API_ERROR));
    }
}
