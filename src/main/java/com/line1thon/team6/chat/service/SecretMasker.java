package com.line1thon.team6.chat.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 첨부 코드 속 비밀값(API 키, 비밀번호, 개인 키 등)을 [REDACTED]로 바꾼다.
 * 비밀값이 DB와 AI 요청에 들어가지 않게 저장 전에 한 번만 적용한다.
 */
@Component
public class SecretMasker {

    public static final String REDACTED = "[REDACTED]";

    /** 값 전체를 가리는 패턴: 형식만 봐도 비밀값인 것 */
    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            // 개인 키 블록 전체
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            // OpenAI, Anthropic 등 sk- 키
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}"),
            // AWS 액세스 키
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            // GitHub 토큰
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}"),
            // Google API 키
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}"),
            // Slack 토큰
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}"),
            // JWT
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")
    );

    /** 비밀값을 뜻하는 단어 (대소문자 무시) */
    private static final String SECRET_WORD =
            "(?i:password|passwd|pwd|secret(?:[_-]?key)?|private[_-]?key|api[_-]?key|access[_-]?key|token)";

    /**
     * 키 이름 뒤의 값만 가리는 패턴.
     * 비밀 단어가 키 이름의 마지막 단어일 때만 맞춘다. (dbPassword, ACCESS_TOKEN, client.secret은 맞고
     * tokenType, passwordLabel, maxTokens, tokenizer는 안 맞는다)
     * 식별자 시작에서만 매칭을 시작해 긴 문자열에서 위치마다 다시 훑지 않게 한다.
     */
    private static final String SECRET_KEY_NAME =
            "(?<![A-Za-z0-9_.-])"
                    + "(?:[A-Za-z0-9_.-]*?(?:[_.-]|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])))?"
                    + SECRET_WORD
                    + "(?![A-Za-z0-9_.-])";

    /** password = "abc123", "apiKey": 'xyz' 처럼 따옴표로 감싼 값 (공백이 있는 값은 문구로 보고 두기) */
    private static final Pattern QUOTED_ASSIGNMENT = Pattern.compile(
            "(\"?" + SECRET_KEY_NAME + "\"?\\s*[:=]\\s*)([\"'])([^\"'\\s]+)\\2");

    /** .env 형식의 DB_PASSWORD=abc123 (줄 전체가 대입문인 경우만) */
    private static final Pattern ENV_ASSIGNMENT = Pattern.compile(
            "(?m)^([ \\t]*(?:export[ \\t]+)?" + SECRET_KEY_NAME + "[ \\t]*=[ \\t]*)([^\\s\"'#]+)[ \\t]*$");

    public Result mask(String code) {
        if (code == null) {
            return new Result(null, false);
        }
        String masked = code;
        for (Pattern pattern : TOKEN_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        }
        masked = QUOTED_ASSIGNMENT.matcher(masked)
                .replaceAll(m -> Matcher.quoteReplacement(m.group(1) + m.group(2) + REDACTED + m.group(2)));
        masked = ENV_ASSIGNMENT.matcher(masked)
                .replaceAll(m -> Matcher.quoteReplacement(m.group(1) + REDACTED));
        return new Result(masked, !masked.equals(code));
    }

    /** 가린 코드와 가린 값이 있었는지 */
    public record Result(String code, boolean masked) {
    }
}
