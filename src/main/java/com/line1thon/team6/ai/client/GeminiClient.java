package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.config.AiProperties;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini generateContent 호출.
 * JSON 구조화 출력(responseSchema)을 쓰지만, 형식이 맞는지는 호출하는 쪽에서 다시 검증한다 (R4).
 * 키는 URL이 아니라 헤더로 보내서 접근 로그에 남지 않게 한다.
 */
public class GeminiClient implements LlmClient {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final AiProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public GeminiClient(AiProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // 요청을 보낸 뒤 응답이 끝날 때까지의 제한 시간
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .build();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String body = jsonMapper.writeValueAsString(toBody(request));
        String response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 응답 본문에는 요청 내용이 들어 있지 않지만, 길어서 상태만 남긴다
            throw new AiException(AiErrorCode.AI_API_ERROR, "Gemini 응답 오류: HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new AiException(AiErrorCode.AI_TIMEOUT, "Gemini 응답 시간 초과: " + properties.timeout(), e);
            }
            throw new AiException(AiErrorCode.AI_API_ERROR, "Gemini 연결 실패", e);
        } catch (RestClientException e) {
            throw new AiException(AiErrorCode.AI_API_ERROR, "Gemini 호출 실패", e);
        }
        return parse(response);
    }

    private Map<String, Object> toBody(LlmRequest request) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", request.responseSchema());
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", properties.thinkingLevel().name()));

        List<Map<String, Object>> contents = request.messages().stream()
                .map(m -> Map.<String, Object>of(
                        "role", m.role() == LlmMessage.Role.USER ? "user" : "model",
                        "parts", List.of(Map.of("text", m.text()))))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        body.put("contents", contents);
        body.put("generationConfig", generationConfig);
        return body;
    }

    /** 생각(thought) 부분을 빼고 출력 텍스트만 모은다. 후보가 없으면(차단 등) 빈 텍스트 → 형식 오류로 처리된다 */
    private LlmResponse parse(String response) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(response == null ? "" : response);
        } catch (JacksonException e) {
            throw new AiException(AiErrorCode.AI_API_ERROR, "Gemini 응답을 읽을 수 없음", e);
        }
        JsonNode candidate = root.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        String finishReason = candidate.isMissingNode()
                ? root.path("promptFeedback").path("blockReason").asString(null)
                : candidate.path("finishReason").asString(null);

        JsonNode usage = root.path("usageMetadata");
        Integer inputTokens = intOrNull(usage.path("promptTokenCount"));
        Integer candidateTokens = intOrNull(usage.path("candidatesTokenCount"));
        Integer thoughtTokens = intOrNull(usage.path("thoughtsTokenCount"));
        Integer outputTokens = candidateTokens == null && thoughtTokens == null ? null
                : (candidateTokens == null ? 0 : candidateTokens) + (thoughtTokens == null ? 0 : thoughtTokens);
        return new LlmResponse(text.toString(), inputTokens, outputTokens, finishReason);
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
