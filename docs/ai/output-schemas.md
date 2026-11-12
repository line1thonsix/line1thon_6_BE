# AI 출력 형식

LLM이 돌려주는 JSON의 형식과, 백엔드 B가 이를 검증해 DB와 API 응답으로 바꾸는 규칙입니다. DB 테이블 구조는 `docs/db-schema.md`를 따릅니다.

> **상태: 확정 (AI 전달 명세 R1·R2·R4·R5 기준).** 비교 분석·기초 설명은 R1 v1.1.0 원문을 사용합니다. 판단 보류의 빈 인용은 최신 R1 변수 명세를 따릅니다. 질문 선생성은 임시 프롬프트(`prepare-tmp2`)를 유지하며, 시연 데이터(R7)는 아직 미반영입니다. 남은 협의는 [협의할 것](#협의할-것)의 P7입니다.

## 공통 규칙

| 규칙 | 내용 |
| --- | --- |
| 필드 이름 | AI 출력은 `snake_case`, API 응답과 DB의 JSON은 `camelCase`. B가 바꿉니다. |
| 기준 답변 | `reference_answer`는 서버에만 저장합니다. 잠금이 풀리기 전에는 어떤 응답에도 넣지 않습니다. |
| 원문 인용 검증 | `user_quote`는 사용자가 쓴 생각에 그대로 있는 연속된 문자열이어야 합니다. 서버가 확인합니다 (줄바꿈·연속 공백 차이는 같은 문장으로 봄). |
| 점수 | 사용자의 이해도나 노력을 점수로 매기지 않습니다. |
| 형식 오류 | 검증에 실패하면 이전 응답과 수정 지시를 붙여 다시 호출합니다. 첫 호출 포함 최대 3번, 끝내 실패하면 `AI_INVALID_JSON`으로 작업을 실패 처리합니다 (R5). 검증하지 않은 결과는 응답하지 않습니다. |
| 시연 데이터 | 예시 질문의 저장 응답(`isDemo: true`)도 이 문서의 AI 출력 형식으로 만들고, 실제 응답과 같은 검증을 거칩니다. |

| 작업 | 최대 출력 토큰 (R4) | 프롬프트 버전 (`ai_usage_log.prompt_version`) |
| --- | --- | --- |
| 질문 생성 (PREPARE) | 1,500 | `prepare-tmp2` |
| 비교 분석 (COMPARE) | 2,500 | `comparison_v1.1.0-r2` |
| 기초 설명 (BASIC) | 1,500 | `learning_v1.1.0-r2` |

### R1 프롬프트 원문과 입력

- 원문: [comparison_v1.1.0](https://app.notion.com/p/comparison_v1-1-0-415cdea39fa18174b2f5deb320bafee1), [learning_v1.1.0](https://app.notion.com/p/learning_v1-1-0-415cdea39fa181efac6feded9f3bb61f), [security_rules_v1.1.0](https://app.notion.com/p/security_rules_v1-1-0-415cdea39fa18104a62bfa61f15ba70f), [변수 명세](https://app.notion.com/p/R1_prompt_variable_spec-415cdea39fa181acb56dcc9d1ecf8152).
- `src/main/resources/prompts/`의 비교·기초 원문은 AI 담당이 별도 전달한 `comparison_v1.1.0.txt`, `learning_v1.1.0.txt`를 파일 그대로 사용합니다. 공통 보안 규칙은 기존 `security_rules_v1.1.0`을 공유합니다. 역할·보안·작성·출력 규칙은 system, 입력 구획은 user로 분리합니다. 원문에 없는 보완 문장은 추가하지 않았습니다.
- 원문 파일명은 v1.1.0을 유지하고, 사용 기록 버전에 `-r2`를 붙여 이전 노션 복사본과 구분합니다.
- 비교 분석에는 유형·질문·코드·최초 생각·기준 답변을 전달합니다. 기초 설명에는 유형·질문·코드만 전달하며 기준 답변과 생각은 보내지 않습니다. 코드가 없으면 `(코드 없음)`입니다.
- 공통 보안 규칙은 `AnalysisPromptTemplate.SECURITY_RULE` 상수로 공유합니다. 출력 스키마는 system의 JSON 문자열과 Gemini `responseSchema` 설정 양쪽으로 보냅니다.
- 전달 파일에는 `<USER_QUESTION>`, `<USER_CODE>`, 비교 입력의 `<USER_THOUGHT>`, `<REFERENCE_ANSWER>` 구획이 포함돼 있습니다. 이전 노션 복사본에서 빠졌던 비교 규칙 8번의 인용 대상 `<USER_THOUGHT>`도 전달 파일 원문으로 반영했습니다. 사용자 원문 속 변수 표기는 재치환하지 않습니다.
- 기초 설명은 원문의 화면 표시명(핵심 요약·예시 코드·먼저 관찰해요·그다음 바꿔요)과 기존 네 필드 유지 지침을 사용합니다. API 응답 키나 프론트 화면 구조를 바꾸는 변경은 아닙니다.
- 머지 전 확인: **새 Gemini 키로 실제 호출 확인 후 머지**. 실제 응답 품질·잘림·응답 시간은 별도 검증해야 합니다.

## 1. 질문 생성 (PREPARE)

질문을 받으면 한 번 호출해 칩, 힌트, 기준 답변을 함께 만듭니다. 칩은 질문에 맞춰 1~3개를 생성하며, 개수를 채우기 위해 비슷하거나 관련 없는 관점을 추가하지 않습니다. 힌트는 기존처럼 정확히 2개입니다.

`POST /api/chats/{chatId}/thoughts`의 `selectedChoice`는 실제 `choices` 배열의 인덱스(0부터)를 사용합니다. 새 제출에서 배열 범위를 벗어난 번호는 `400 INVALID_INPUT`입니다. 선택하지 않으면 `null`로 제출할 수 있습니다. 기존에 저장된 칩 3개도 그대로 사용합니다.

```json
{
  "choices": ["첫 렌더링 때 users 값", "데이터가 도착하는 시점", "useState 초기값"],
  "hints": ["힌트 1단계", "힌트 2단계"],
  "reference_answer": "기준 답변 전문"
}
```

| 필드 | 타입 | 규칙 | DB (`chat`) | API |
| --- | --- | --- | --- | --- |
| `choices` | string[] | 질문에 맞는 1~3개. 서로 다른 관점을 짧은 구절로 표현 | `choices` | `GET /chats/{chatId}`의 `choices` |
| `hints` | string[] | 정확히 2개. 1단계보다 2단계가 더 구체적 | `hints` | 공개된 단계만 `POST /hints`의 `hint` |
| `reference_answer` | string | 비어 있으면 안 됨 | `reference_answer` | 잠금이 풀린 뒤 `GET /analysis`의 `referenceAnswer` |

- 질문 유형은 사용자가 고른 값을 입력으로만 넘기고, AI는 출력하지 않습니다. `example_sentence`도 출력하지 않습니다 (R2).

## 2. 비교 분석 (COMPARE)

사용자의 생각과 기준 답변을 비교해 다섯 가지로 나눕니다. 다섯 분류는 항상 배열이고, 해당 항목이 없으면 빈 배열입니다. 모든 항목에 짧은 제목 `title`이 있습니다.

```json
{
  "common": [{ "title": "제목", "user_quote": "사용자 원문", "ai_view": "AI의 대응 관점", "reason": "공통점으로 판단한 근거" }],
  "supplement": [{ "title": "제목", "user_quote": "", "ai_view": "추가로 알아야 할 내용", "reason": "추가 정보가 필요한 이유" }],
  "unique": [{ "title": "제목", "user_quote": "사용자 원문", "reason": "타당한 이유" }],
  "error": [{ "title": "제목", "user_quote": "사용자 원문", "conflict": "기준 답변과 충돌하는 내용", "how_to_check": "직접 확인하는 방법" }],
  "judgment_hold": [{ "title": "제목", "user_quote": "사용자 원문", "reason": "근거 부족 또는 모호한 이유", "follow_up_question": "사용자에게 확인할 질문" }]
}
```

| 분류 | 의미 | `user_quote` |
| --- | --- | --- |
| `common` | 사용자와 기준 답변이 함께 짚은 내용 | 필수 |
| `supplement` | 사용자가 놓친 추가 정보. 기준 답변에 근거한, 사실이고 확인할 수 있는 것만 | 관련 발언이 없으면 `""` |
| `unique` | 기준 답변에는 없지만 타당한 사용자의 관점 | 필수 |
| `error` | 사실과 분명히 다른 사용자 발언 | 필수 |
| `judgment_hold` | 근거가 부족하거나 뜻이 모호해 판단을 보류한 발언 | 관련 발언이 없으면 `""` (R1 v1.1.0) |

### 저장과 응답

B는 분류별 배열을 하나의 `findings` 배열로 펼쳐 AI가 준 순서대로 `analysis_job.findings`에 저장하고, 잠금이 풀린 뒤 `GET /chats/{chatId}/analysis`의 `findings`로 응답합니다. 응답의 `id`는 배열 순서(0부터)입니다.

Figma 08번 화면은 맨 위에 "나의 첫 생각"과 "AI의 참고 관점"(기준 답변 전체, 응답의 `referenceAnswer`)이 있고, 비교 카드마다 "내 생각" 칸과 "AI" 칸이 따로 있습니다. 카드의 "AI" 칸이 `aiPerspective`이고, "근거와 확인 방법 보기"가 `detail`입니다 (협의 P8).

| AI 분류 | `category` | `userQuote` | `aiPerspective` | `detail` | `followUpQuestion` |
| --- | --- | --- | --- | --- | --- |
| `common` | `COMMON` | `user_quote` | `ai_view` | `reason` | |
| `supplement` | `COMPLEMENT` | `user_quote` (`""`면 `null`) | `ai_view` | `reason` | |
| `unique` | `UNIQUE` | `user_quote` | `null` | `reason` | |
| `error` | `CONFLICT` | `user_quote` | `conflict` | `how_to_check` | |
| `judgment_hold` | `UNDETERMINED` | `user_quote` (`""`면 `null`) | `null` | `reason` | `follow_up_question` |

## 3. 기초 설명 (BASIC)

힌트 2단계를 본 뒤 기초 학습을 고르면 만듭니다. Figma 10번 화면의 네 칸에 맞춘 형식입니다 (R2).

```json
{
  "summary": "핵심 개념을 이해하기 쉬운 수준으로 요약",
  "example_code": "개념을 이해할 수 있는 짧은 예시 코드",
  "observe": "먼저 관찰해야 할 부분과 확인 방법",
  "change": "그다음 직접 변경하거나 시도해 볼 내용"
}
```

- 네 키 모두 비어 있지 않은 문자열이어야 합니다. 코드가 필요 없는 개념이면 `example_code`에 짧은 예시를 씁니다.
- 초보자도 이해할 수 있게 쓰고, 정답 전체를 그대로 보여 주지 않습니다.

### 저장과 응답

`analysis_job.basic_explanation`(TEXT)에 camelCase JSON 문자열로 저장하고, 잠금이 풀린 뒤 `GET /analysis`의 `basicExplanation` **객체**로 응답합니다 (협의 P4).

```json
"basicExplanation": { "summary": "...", "exampleCode": "...", "observe": "...", "change": "..." }
```

## 4. B의 검증 순서

1. JSON으로 파싱합니다. ```json 코드 블록으로 감싸 오면 벗겨서 읽습니다.
2. 필수 키, 타입, 개수(`choices` 1~3개, `hints` 2개), 빈 값을 확인합니다. 문제는 한 번에 모두 모아 수정 지시에 넣습니다.
3. 비교 분석이면 `user_quote`가 사용자 생각에 그대로 있는지 확인합니다. `supplement`와 `judgment_hold`의 빈 문자열(`""`)은 인용이 없다는 뜻이라 검사하지 않습니다.
4. 원문에 없는 인용은 그 항목만 버립니다. 버리고 남은 항목이 없으면 형식 오류로 봅니다 (협의 P6).
5. 2~4에서 실패하면 수정 지시를 붙여 다시 호출하고, 첫 호출 포함 3번 모두 실패하면 `AI_INVALID_JSON`으로 처리합니다. 시간 초과(`AI_TIMEOUT`)와 제공사 오류(`AI_API_ERROR`)는 다시 호출하지 않고 작업을 실패로 바꿉니다. 사용자는 재시도 API로 다시 시도합니다.

## 협의할 것

| # | 내용 | 차이 | 결정 | 대상 |
| --- | --- | --- | --- | --- |
| P1 | AI 결정안의 별도 API와 `session_id` | 결정안에는 `POST /api/question`, `/api/compare`, `/api/hint`, `/api/basic-learning`과 `session_id`, `expires_in_seconds`, `security`가 있습니다. 팀 API 명세서에는 이런 API가 없고 대화는 `chatId`로 구분합니다. | **결정 (R3):** 별도 API 없이 B가 서버 안에서 LLM을 호출합니다. 결정안의 JSON은 프롬프트의 출력 형식으로만 쓰고, `session_id`는 `chatId`로 대신합니다. | AI 담당 |
| P2 | 질문 생성 출력에 힌트·기준 답변 포함 | 결정안의 질문 생성 출력에는 `hints`와 `reference_answer`가 없고, 힌트는 따로 만듭니다. 팀 명세와 DB는 한 번에 만들어 `chat`에 저장합니다. | **결정 (R2):** 한 번에 만듭니다 (위 1번 형식). | AI 담당, A |
| P3 | 비교 항목의 `title` | API와 DB에는 `title`이 있는데 AI 출력에는 없습니다. | **결정 (R2):** 모든 분류의 항목에 `title`(짧은 제목)을 추가합니다. | AI 담당 |
| P4 | 기초 설명 형식 | AI 결정안, DB·API(문자열 하나), Figma(4칸)가 모두 다릅니다. | **결정 (R2):** Figma 4칸(`summary`, `example_code`, `observe`, `change`). DB는 JSON 문자열, API는 객체로 응답합니다 (위 3번). Notion 분석 조회 명세는 A가 고칩니다. | AI 담당, A, 프론트 |
| P5 | 판단 보류의 개수 | AI 출력은 객체 하나(최대 1개)이고, API는 `UNDETERMINED` 항목을 여러 개 담을 수 있습니다. | **결정 (R1 v1.1.0):** 다른 분류처럼 배열입니다. 직접 인용이 없으면 `user_quote`는 `""`를 허용합니다. | AI 담당 |
| P6 | 원문에 없는 인용 | 검증에 실패한 항목의 처리가 정해지지 않았습니다. | **결정 (R5):** 그 항목만 버리고, 남은 항목이 없으면 수정 지시를 붙여 다시 호출합니다. | AI 담당 |
| P7 | 근거 펼침 (Figma 08-1~08-5) | Figma에는 "직접 확인하는 방법"과 코드 예시가 있지만, AI 출력에는 `error`의 `how_to_check`만 있습니다. | MVP는 `how_to_check`만 쓰고, 다른 분류는 `reason`을 근거로 보여 줍니다. | AI 담당, 프론트 |
| P8 | 비교 항목의 `aiPerspective` | Figma는 "내 생각"과 "AI 참고 관점"을 나눠 보여 주는데, DB의 `findings`와 API 응답에는 AI 관점을 담을 필드가 없습니다. | **결정:** `findings` 항목에 `aiPerspective`를 추가합니다. DB 스키마 문서는 반영했고, Notion 분석 조회 명세는 A가 고칩니다. | A, 프론트 |
