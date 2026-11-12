package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareOutputParserTest {

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요.\n데이터가 오기 전에는 undefined라서요. useEffect가 늦게 실행돼요.";

    private final CompareOutputParser parser = new CompareOutputParser(JsonMapper.builder().build());

    private List<CompareFinding> parse(String json) {
        return parser.forThought(THOUGHT).parse(json);
    }

    private static final String VALID = """
            {
              "common": [{"title": "초기값 확인", "user_quote": "첫 렌더링 때 users 값부터 볼게요.", "ai_view": "초기 상태가 원인", "reason": "같은 지점"}],
              "supplement": [{"title": "옵셔널 체이닝", "user_quote": "", "ai_view": "users?.map", "reason": "놓친 방어 코드"}],
              "unique": [{"title": "실행 순서", "user_quote": "useEffect가 늦게 실행돼요.", "reason": "타당한 관점"}],
              "error": [{"title": "원인 오해", "user_quote": "데이터가 오기 전에는 undefined라서요.", "conflict": "초기값이 undefined", "how_to_check": "console.log"}],
              "judgment_hold": [{"title": "의도 불명", "user_quote": "useEffect가 늦게 실행돼요.", "reason": "근거 부족", "follow_up_question": "어떤 값을 기대했나요?"}]
            }
            """;

    @Test
    void 다섯_분류를_순서대로_findings로_바꾼다() {
        List<CompareFinding> findings = parse(VALID);

        assertThat(findings).extracting(CompareFinding::category)
                .containsExactly("COMMON", "COMPLEMENT", "UNIQUE", "CONFLICT", "UNDETERMINED");
        assertThat(findings.get(0)).isEqualTo(new CompareFinding("COMMON", "초기값 확인",
                "첫 렌더링 때 users 값부터 볼게요.", "초기 상태가 원인", "같은 지점", null));
        assertThat(findings.get(1).userQuote()).as("빈 인용은 null").isNull();
        assertThat(findings.get(2).aiPerspective()).isNull();
        assertThat(findings.get(3)).isEqualTo(new CompareFinding("CONFLICT", "원인 오해",
                "데이터가 오기 전에는 undefined라서요.", "초기값이 undefined", "console.log", null));
        assertThat(findings.get(4)).isEqualTo(new CompareFinding("UNDETERMINED", "의도 불명",
                "useEffect가 늦게 실행돼요.", null, "근거 부족", "어떤 값을 기대했나요?"));
    }

    @Test
    void 저장할_때는_항목마다_같은_키를_가진다() {
        assertThat(parse(VALID).get(1).toMap()).containsOnlyKeys(
                "category", "title", "userQuote", "aiPerspective", "detail", "followUpQuestion");
    }

    @Test
    void 생각에_없는_인용은_그_항목만_버린다() {
        List<CompareFinding> findings = parse("""
                {"common": [
                   {"title": "a", "user_quote": "첫 렌더링 때 users 값부터 볼게요.", "ai_view": "v", "reason": "r"},
                   {"title": "b", "user_quote": "사용자가 쓰지 않은 문장", "ai_view": "v", "reason": "r"}],
                 "supplement": [], "unique": [], "error": [], "judgment_hold": []}
                """);

        assertThat(findings).extracting(CompareFinding::title).containsExactly("a");
    }

    @Test
    void 줄바꿈이_공백으로_바뀐_인용은_같은_문장으로_본다() {
        List<CompareFinding> findings = parse("""
                {"common": [{"title": "a", "user_quote": "볼게요. 데이터가 오기 전에는", "ai_view": "v", "reason": "r"}],
                 "supplement": [], "unique": [], "error": [], "judgment_hold": []}
                """);

        assertThat(findings).hasSize(1);
    }

    @Test
    void 모든_항목의_인용이_없으면_형식_오류로_다시_부르게_한다() {
        assertThatThrownBy(() -> parse("""
                {"common": [{"title": "a", "user_quote": "지어낸 문장", "ai_view": "v", "reason": "r"}],
                 "supplement": [], "unique": [], "error": [], "judgment_hold": []}
                """))
                .isInstanceOfSatisfying(AiOutputInvalidException.class,
                        e -> assertThat(e.getProblems()).singleElement().asString().contains("common[0]"));
    }

    @Test
    void 판단_보류는_빈_인용도_허용하고_null로_저장한다() {
        assertThat(parse("""
                {"common": [], "supplement": [], "unique": [], "error": [],
                 "judgment_hold": [{"title": "t", "user_quote": "", "reason": "r", "follow_up_question": "q"}]}
                """))
                .containsExactly(new CompareFinding("UNDETERMINED", "t", null, null, "r", "q"));
    }

    @Test
    void 항목이_하나도_없으면_형식_오류() {
        assertThatThrownBy(() -> parse("""
                {"common": [], "supplement": [], "unique": [], "error": [], "judgment_hold": []}
                """))
                .isInstanceOf(AiOutputInvalidException.class);
    }

    @Test
    void 키_타입_빈_값_문제를_한_번에_모두_모은다() {
        assertThatThrownBy(() -> parse("""
                {"common": [{"title": "", "user_quote": "", "ai_view": "v", "reason": "r"}],
                 "supplement": {}, "unique": [{"title": "t", "user_quote": "첫 렌더링", "reason": 1}],
                 "error": []}
                """))
                .isInstanceOfSatisfying(AiOutputInvalidException.class, e -> assertThat(e.getProblems())
                        .containsExactlyInAnyOrder(
                                "common[0].title가 비어 있습니다",
                                "common[0].user_quote가 비어 있습니다",
                                "supplement는 배열이어야 합니다",
                                "unique[0].reason는 문자열이어야 합니다",
                                "judgment_hold 키가 없습니다 (항목이 없으면 빈 배열)"));
    }

    @Test
    void JSON이_아니면_형식_오류이고_코드_블록은_벗겨서_읽는다() {
        assertThatThrownBy(() -> parse("not json")).isInstanceOf(AiOutputInvalidException.class);
        assertThat(parse("```json\n" + VALID + "\n```")).hasSize(5);
    }
}
