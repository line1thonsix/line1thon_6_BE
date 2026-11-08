# AI 출력 형식

LLM이 돌려주는 JSON의 형식과, 백엔드 B가 이를 검증해 DB와 API 응답으로 바꾸는 규칙입니다. DB 테이블 구조는 팀 Notion의 "DB 스키마"를 따릅니다.

> **상태: 초안.** AI 담당의 "API 출력 형식" 결정안을 기준으로 작성했습니다. 그 결정안과 API 명세서, DB 스키마, Figma가 서로 다른 부분이 있어 [협의할 것](#협의할-것)에 모았습니다. 협의가 끝나면 이 문서를 고치고 상태를 "확정"으로 바꿉니다.

## 공통 규칙

| 규칙 | 내용 |
| --- | --- |
| 필드 이름 | AI 출력은 `snake_case`, API 응답은 `camelCase`. B가 바꿉니다. |
| 기준 답변 | `reference_answer`는 서버에만 저장합니다. 잠금이 풀리기 전에는 어떤 응답에도 넣지 않습니다. |
| 원문 인용 검증 | `user_quote`는 사용자가 쓴 생각에 그대로 있는 연속된 문자열이어야 합니다. 서버가 확인합니다. |
| 점수 | 사용자의 이해도나 노력을 점수로 매기지 않습니다. |
| 형식 오류 | 검증에 실패하면 내부에서 한 번 다시 호출하고, 또 실패하면 `AI_INVALID_JSON`으로 작업을 실패 처리합니다. 검증하지 않은 결과는 응답하지 않습니다. |

## 1. 질문 생성 (PREPARE)

질문을 받으면 한 번 호출해 칩, 예시 문장, 힌트, 기준 답변, 질문 유형을 함께 만듭니다.

### AI 출력

```json
{
  "question_type": "DEBUGGING",
  "choices": ["첫 렌더링 때 users 값", "데이터가 도착하는 시점", "useState 초기값"],
  "example_sentence": "users를 처음 렌더링할 때 값이 무엇인지 console.log로 찍어서 확인해 볼래요.",
  "hints": ["힌트 1단계", "힌트 2단계"],
  "reference_answer": "기준 답변 전문"
}
```

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `question_type` | string | `CONCEPT`, `DEBUGGING`, `PROBLEM_SOLVING`, `DESIGN` 중 하나. 사용자가 `FREE`로 보냈을 때 AI가 정합니다. |
| `choices` | string[] | 정확히 3개. 짧은 구절 |
| `example_sentence` | string | 1개 |
| `hints` | string[] | 정확히 2개. 1단계보다 2단계가 더 구체적 |
| `reference_answer` | string | 비어 있으면 안 됨 |

### 저장과 응답

| AI 출력 | DB (`chat`) | API 응답 (`GET /chats/{chatId}`) |
| --- | --- | --- |
| `question_type` | `type` | `type` |
| `choices` | `choices` | `choices` |
| `example_sentence` | (컬럼 없음, 협의 P2) | `exampleSentence` |
| `hints` | `hints` | 공개된 단계만 `POST /hints` 응답의 `hint` |
| `reference_answer` | `reference_answer` | 잠금이 풀린 뒤에만 `referenceAnswer` |

## 2. 비교 분석 (COMPARE)

사용자의 생각과 기준 답변을 비교해 다섯 가지로 나눕니다.

### AI 출력

```json
{
  "common": [
    { "user_quote": "사용자 원문 인용", "ai_view": "AI의 대응 관점", "reason": "공통점으로 판단한 근거" }
  ],
  "supplement": [
    { "user_quote": "", "ai_view": "추가로 알아야 할 내용", "reason": "추가 정보가 필요한 이유" }
  ],
  "unique": [
    { "user_quote": "사용자의 독창적인 관점", "reason": "타당한 이유" }
  ],
  "error": [
    { "user_quote": "사용자의 오류 발언", "conflict": "기준 답변과 충돌하는 내용", "how_to_check": "직접 확인하는 방법" }
  ],
  "judgment_hold": {
    "status": false,
    "reason": "",
    "follow_up_question": ""
  }
}
```

| 분류 | 의미 | `user_quote` |
| --- | --- | --- |
| `common` | 사용자와 AI가 함께 이해한 내용 | 필수 |
| `supplement` | 사용자가 놓친 추가 정보. 사실이고 확인할 수 있는 것만 | 해당 발언이 없으면 `""` |
| `unique` | 기준 답변에는 없지만 타당한 사용자의 관점 | 필수 |
| `error` | 사실과 분명히 다른 사용자 발언 | 필수 |
| `judgment_hold` | 근거가 부족하거나 뜻이 모호해 판단을 보류한 내용 | 없음 |

- 네 분류(`common`, `supplement`, `unique`, `error`)는 항목이 없어도 빈 배열로 둡니다.
- 분명한 오류는 `error`, 근거가 부족하면 `judgment_hold`로 나눕니다.

### 저장과 응답

B는 분류별 배열을 하나의 `findings` 배열로 펼쳐 `analysis_job.findings`에 저장하고, 그대로 `GET /chats/{chatId}/analysis`의 `findings`로 응답합니다.

| AI 분류 | `category` | `userQuote` | `detail` | `followUpQuestion` |
| --- | --- | --- | --- | --- |
| `common` | `COMMON` | `user_quote` | `ai_view`, `reason` | |
| `supplement` | `COMPLEMENT` | `user_quote` (`""`면 `null`) | `ai_view`, `reason` | |
| `unique` | `UNIQUE` | `user_quote` | `reason` | |
| `error` | `CONFLICT` | `user_quote` | `conflict`, `how_to_check` | |
| `judgment_hold` (`status: true`일 때만) | `UNDETERMINED` | `null` | `reason` | `follow_up_question` |

`title`은 API와 DB에 있지만 AI 출력에는 없습니다 (협의 P3).

## 3. 기초 설명 (BASIC)

힌트 2단계를 본 뒤 기초 학습을 고르면 만듭니다.

### AI 출력

```json
{
  "concept": "핵심 개념 설명",
  "why_it_matters": "개념이 중요한 이유",
  "example": "쉬운 예시",
  "starting_steps": ["학습 시작 방법 1", "학습 시작 방법 2"],
  "check_question": "이해 확인 질문"
}
```

### 저장과 응답

DB는 `analysis_job.basic_explanation` (TEXT) 하나, API는 `basicExplanation` (string) 하나입니다. Figma 10번 화면은 요약, 예시 코드, "먼저 관찰해요", "그다음 바꿔요"로 나뉘어 있어 세 곳이 서로 다릅니다 (협의 P4).

## 4. B의 검증 순서

1. JSON으로 파싱합니다.
2. 필수 필드, 타입, 개수(`choices` 3개, `hints` 2개)를 확인합니다.
3. enum 값(`question_type`)을 확인합니다.
4. 비교 분석이면 `user_quote`가 사용자 생각에 그대로 있는지 확인합니다. 없는 인용은 항목을 버릴지, 전체를 실패로 볼지 정해야 합니다 (협의 P6).
5. 하나라도 실패하면 한 번 다시 호출하고, 다시 실패하면 `AI_INVALID_JSON`으로 처리합니다.

## 협의할 것

| # | 내용 | 차이 | B 제안 | 대상 |
| --- | --- | --- | --- | --- |
| P1 | AI 결정안의 별도 API와 `session_id` | 결정안에는 `POST /api/question`, `/api/compare`, `/api/hint`, `/api/basic-learning`과 `session_id`, `expires_in_seconds`, `security`가 있습니다. 팀 API 명세서에는 이런 API가 없고 대화는 `chatId`로 구분합니다. | 별도 API 없이 B가 서버 안에서 LLM을 호출합니다. 결정안의 JSON은 프롬프트의 출력 형식으로만 쓰고, `session_id`는 `chatId`로 대신합니다. | AI 담당 |
| P2 | 질문 생성 출력에 힌트·기준 답변 포함 | 결정안의 질문 생성 출력에는 `hints`와 `reference_answer`가 없고, 힌트는 따로 만듭니다. 팀 명세와 DB는 한 번에 만들어 `chat`에 저장합니다. DB 스키마의 `chat`에는 `example_sentence` 컬럼이 없습니다. | 한 번에 만듭니다 (위 1번 형식). `chat`에 `example_sentence` 컬럼 추가를 A에게 요청합니다. | AI 담당, A |
| P3 | 비교 항목의 `title` | API와 DB에는 `title`이 있는데 AI 출력에는 없습니다. | 모든 분류의 항목에 `title`(짧은 제목)을 추가합니다. | AI 담당 |
| P4 | 기초 설명 형식 | AI 결정안, DB·API(문자열 하나), Figma(4칸)가 모두 다릅니다. | Figma에 맞춰 칸을 나누고, DB는 JSON으로 저장합니다. API도 객체로 바꿉니다. | AI 담당, A, 프론트 |
| P5 | 판단 보류의 개수 | AI 출력은 객체 하나(최대 1개)이고, API는 `UNDETERMINED` 항목을 여러 개 담을 수 있습니다. | 다른 분류처럼 배열로 맞춥니다. | AI 담당 |
| P6 | 원문에 없는 인용 | 검증에 실패한 항목의 처리가 정해지지 않았습니다. | 그 항목만 버리고, 남은 항목이 없으면 다시 호출합니다. | AI 담당 |
| P7 | 근거 펼침 (Figma 08-1~08-5) | Figma에는 "직접 확인하는 방법"과 코드 예시가 있지만, AI 출력에는 `error`의 `how_to_check`만 있습니다. | MVP는 `how_to_check`만 쓰고, 다른 분류는 `reason`을 근거로 보여 줍니다. | AI 담당, 프론트 |
