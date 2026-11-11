# 오류 코드

API가 돌려주는 오류 코드 목록입니다. 코드의 `ErrorCode` enum과 이 문서를 함께 고칩니다.

- 기준: 팀 Notion의 API 명세서 공통 규칙, 각 API 페이지, 잠금 상태 머신의 "막는 요청" 표
- 경로는 Base URL `/api`를 빼고 적습니다. `/chats/{chatId}`는 `/api/chats/{chatId}`입니다.
- 오류 문구(`message`)는 아직 정하지 않았습니다. 서버 문구를 그대로 보여 줄지, 프론트가 `code`별 문구를 쓸지 프론트와 정한 뒤 채웁니다.

## 응답 형식

모든 오류는 같은 형식으로 응답합니다.

```json
{
  "code": "THOUGHT_TOO_SHORT",
  "message": "이유나 확인 방법을 조금 더 적어 볼까요? 30자 이상이면 함께 비교할 수 있어요.",
  "requestId": "req_8f2c"
}
```

| 필드 | 설명 |
| --- | --- |
| `code` | 아래 표의 코드. 프론트는 이 값으로 화면을 나눕니다. |
| `message` | 사용자에게 보여 줄 수 있는 문구 |
| `requestId` | 요청 ID. 운영 로그에서 같은 요청을 찾을 때 씁니다. |

## 공통 오류

여러 API에서 함께 쓰는 오류입니다. API별 표에는 다시 적지 않습니다.

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 필수 값 누락, 형식 오류, 길이 초과 |
| 401 | `INVALID_TOKEN` | 토큰이 없거나 잘못됨. 프론트는 토큰을 지우고 `POST /sessions`로 다시 발급 |
| 404 | `CHAT_NOT_FOUND` | 없거나 다른 사람의 대화 |
| 404 | `API_NOT_FOUND` | 없는 URL |
| 405 | `METHOD_NOT_ALLOWED` | URL은 있지만 지원하지 않는 HTTP 메서드 |
| 429 | `RATE_LIMITED` | 질문 생성이 게스트당 하루 30회를 넘음 (한국 시간 자정 기준). **MVP 제외** |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

## API별 오류

| 상태 | 코드 | 상황 | API | 담당 |
| --- | --- | --- | --- | --- |
| 400 | `THOUGHT_TOO_SHORT` | 생각이 30자 미만 | `POST /chats/{chatId}/thoughts` | A |
| 400 | `THOUGHT_INVALID` | 공백을 빼고 서로 다른 문자가 8개 미만 | `POST /chats/{chatId}/thoughts` | A |
| 400 | `BASIC_MODE_NOT_ALLOWED` | `hintLevel`이 2가 아닌데 기초 학습 요청 | `POST /chats/{chatId}/basic-mode` | B |
| 404 | `ANALYSIS_NOT_FOUND` | 분석 작업이 없는 채팅에서 분석 조회·재시도 | `GET /chats/{chatId}/analysis`, `POST /chats/{chatId}/analysis/retry` | B |
| 409 | `PREPARE_NOT_DONE` | 질문 생성(`prepareStatus`)이 `DONE`이 아닌데 힌트·생각 제출·기초 학습 요청 | `/hints`, `/thoughts`, `/basic-mode` | A, B |
| 409 | `PREPARE_NOT_FAILED` | `prepareStatus`가 `FAILED`가 아닌데 질문 생성 재시도 | `POST /chats/{chatId}/prepare/retry` | A |
| 409 | `ALREADY_SUBMITTED` | 이미 분석 작업이 있음. 생각 제출 후 기초 학습, 기초 학습 후 생각 제출, 작업이 있는데(진행 중이거나 완료) 힌트 요청 | `/thoughts`, `/basic-mode`, `/hints` | A, B |
| 409 | `NOT_RETRYABLE` | 분석 작업이 `FAILED`가 아닌데 재시도 (`PENDING` 또는 `DONE`) | `POST /chats/{chatId}/analysis/retry` | B |
| 409 | `RETRY_LIMIT_EXCEEDED` | 첫 시도를 포함해 3회 모두 실패 | `POST /chats/{chatId}/analysis/retry` | B |
| 409 | `ANALYSIS_NOT_DONE` | 잠금이 풀리기 전에 회고 저장. **MVP 제외** | `POST /chats/{chatId}/reflections` | A |
| 409 | `CHAT_ALREADY_UNLOCKED` | 잠금이 풀린 뒤 막힌 지점 변경. **MVP 제외** | `PATCH /chats/{chatId}/stuck-point` | A |

같은 요청을 다시 보낸 경우는 오류가 아닙니다.

| 상황 | 응답 |
| --- | --- |
| 같은 `clientRequestId`로 질문 생성 | `200`, 기존 대화 |
| 같은 길로 생각 제출·기초 학습을 다시 요청 (중복 클릭) | `200`, 기존 `jobId` |
| `hintLevel`이 2인데 힌트 요청 | `200`, 마지막 힌트 다시 반환 |

## AI 작업 오류 코드

AI 호출이 실패해도 API는 오류로 응답하지 않습니다. 실패는 상태값으로 알리고, 조회 API는 `200`으로 응답합니다.

| 작업 | 실패 표시 | 오류 코드 |
| --- | --- | --- |
| 질문 생성 | `GET /chats/{chatId}`의 `prepareStatus: FAILED` | 없음. `chat` 테이블에 오류 코드 컬럼이 없어 운영 로그(`ai_usage_log.error_code`)에만 남습니다. |
| 비교 분석, 기초 설명 | `GET /chats/{chatId}/analysis`의 `analysisStatus: FAILED` | `errorCode`에 아래 값. `analysis_job.error_code`에 저장 |

| 코드 | 상황 |
| --- | --- |
| `AI_TIMEOUT` | AI 호출이 15초 안에 응답하지 않음 (호출마다 15초, 내부 재호출 포함). 오래 멈춘 작업을 실패로 바꿀 때도 사용 |
| `AI_INVALID_JSON` | 응답이 AI 출력 형식에 맞지 않음 (내부 재호출 포함) |
| `AI_API_ERROR` | AI 제공사 API 오류 (인증, 사용량 초과, 서버 오류 등) |

오래 멈춘 작업의 기준은 질문 생성이 1분 넘게 `PREPARING`일 때입니다 (DB 스키마의 `prepare_started_at`). 분석 작업은 지금 시도를 시작한 뒤(`analysis_job.started_at`) 2분 넘게 `PENDING`이면 `GET /chats/{chatId}/analysis`를 조회할 때 `FAILED`(`AI_TIMEOUT`)로 바꿉니다. 정상 작업은 최대 45초(15초 × 3번)라 걸리지 않습니다.

## 쓰지 않는 코드

팀 Notion의 일부 API 페이지에 아직 남아 있지만 쓰지 않기로 한 코드입니다. 해당 API 페이지를 고칠 때 함께 지웁니다.

| 코드 | 남아 있는 곳 | 바뀐 내용 |
| --- | --- | --- |
| `403 FORBIDDEN` | 분석 조회·재시도, 힌트, 기초 학습, 의견 | 다른 사람의 대화도 `404 CHAT_NOT_FOUND`로 통일 |
| `403 LOCKED` | 분석 조회 | 잠겨 있어도 `200`으로 상태값을 주고 결과만 뺌 |
| `429 RETRY_LIMIT_EXCEEDED` | 분석 재시도 | `409 RETRY_LIMIT_EXCEEDED`로 변경. `429`는 사용량 제한에만 사용 |
| `400 INVALID_STUCK_POINT` | 힌트 | 힌트 요청에 바디가 없어짐 |
| `409 ALREADY_UNLOCKED`, `409 ALREADY_UNLOCKED_SELF` | 힌트, 기초 학습 | `409 ALREADY_SUBMITTED`로 통일 |
| `FINDING_NOT_FOUND` | 의견 | 판정 이의 제기는 MVP에서 제외 (DB 스키마) |

## 코드를 추가할 때

1. 공통 오류로 표현할 수 있으면 새 코드를 만들지 않습니다.
2. 이름은 대문자와 밑줄로 쓰고, 상황을 그대로 드러냅니다 (`PREPARE_NOT_DONE`처럼 "무엇이 어떤 상태인지").
3. 상태 코드는 다음 기준을 따릅니다.
   - `400`: 요청 값이 잘못됨
   - `404`: 대상이 없음
   - `409`: 요청은 맞지만 지금 상태에서 할 수 없음
   - `429`: 사용량 제한
4. `ErrorCode` enum과 이 문서의 표를 같은 PR에서 고칩니다.
