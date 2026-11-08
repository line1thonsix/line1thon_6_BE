package com.line1thon.team6.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecretMaskerTest {

    private final SecretMasker masker = new SecretMasker();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "key = sk-abcdefghijklmnopqrstuvwxyz123456           | key = [REDACTED]",
            "aws = AKIAIOSFODNN7EXAMPLE                         | aws = [REDACTED]",
            "gh = ghp_abcdefghijklmnopqrstuvwxyz0123456789AB     | gh = [REDACTED]",
            "g = AIzaSyA1234567890abcdefghijklmnopqrstuv         | g = [REDACTED]",
            "slack = xoxb-1234567890-abcdefghij                 | slack = [REDACTED]",
            "jwt = eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0.SflKxwRJSMeKKF2Q | jwt = [REDACTED]",
    })
    void 형식으로_알_수_있는_키는_값_전체를_가린다(String code, String expected) {
        SecretMasker.Result result = masker.mask(code);

        assertThat(result.code()).isEqualTo(expected);
        assertThat(result.masked()).isTrue();
    }

    @Test
    void 비밀값_이름에_대입한_따옴표_값을_가린다() {
        assertThat(masker.mask("password = \"hunter2\"").code()).isEqualTo("password = \"[REDACTED]\"");
        assertThat(masker.mask("const dbPassword = 'abc123';").code()).isEqualTo("const dbPassword = '[REDACTED]';");
        assertThat(masker.mask("\"apiKey\": \"my-key\"").code()).isEqualTo("\"apiKey\": \"[REDACTED]\"");
        assertThat(masker.mask("client_secret: 'zzz'").code()).isEqualTo("client_secret: '[REDACTED]'");
        assertThat(masker.mask("ACCESS_TOKEN='tok'").code()).isEqualTo("ACCESS_TOKEN='[REDACTED]'");
    }

    @Test
    void 비밀_단어가_키_이름_끝에_올_때만_가린다() {
        assertThat(masker.mask("String accessToken = \"abc123\";").code())
                .isEqualTo("String accessToken = \"[REDACTED]\";");
        assertThat(masker.mask("jwt.secret: 'zzz'").code()).isEqualTo("jwt.secret: '[REDACTED]'");
        assertThat(masker.mask("String secretKey = \"zzz\";").code()).isEqualTo("String secretKey = \"[REDACTED]\";");
        assertThat(masker.mask("\"x-api-key\": \"zzz\"").code()).isEqualTo("\"x-api-key\": \"[REDACTED]\"");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "String tokenType = \"Bearer\";",
            "String passwordLabel = \"비밀번호를 입력하세요\";",
            "const tokenizer = 'whitespace';",
            "int maxTokens = \"100\";",
            "String password = \"비밀번호를 입력하세요\";",
    })
    void 비밀값이_아닌_코드는_가리지_않는다(String code) {
        SecretMasker.Result result = masker.mask(code);

        assertThat(result.code()).isEqualTo(code);
        assertThat(result.masked()).isFalse();
    }

    @Test
    void 같은_문자가_길게_이어져도_빠르게_끝난다() {
        String code = "a".repeat(100_000);

        long start = System.nanoTime();
        masker.mask(code);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(500);
    }

    @Test
    void env_형식의_대입문은_값을_가린다() {
        String code = "DB_HOST=localhost\nDB_PASSWORD=abc123\nexport API_KEY=xyz\n";

        assertThat(masker.mask(code).code())
                .isEqualTo("DB_HOST=localhost\nDB_PASSWORD=[REDACTED]\nexport API_KEY=[REDACTED]\n");
    }

    @Test
    void 개인_키_블록은_통째로_가린다() {
        String code = "key = `-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nAAAA\n-----END RSA PRIVATE KEY-----`";

        assertThat(masker.mask(code).code()).isEqualTo("key = `[REDACTED]`");
    }

    @Test
    void 비밀값이_없으면_그대로_두고_masked는_false() {
        String code = "const [users, setUsers] = useState();\nconst password = getPassword();";

        SecretMasker.Result result = masker.mask(code);

        assertThat(result.code()).isEqualTo(code);
        assertThat(result.masked()).isFalse();
    }

    @Test
    void 코드가_없으면_null() {
        SecretMasker.Result result = masker.mask(null);

        assertThat(result.code()).isNull();
        assertThat(result.masked()).isFalse();
    }
}
