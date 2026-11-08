# line1thon_6_BE

## 개발 환경
- Java 21
- Spring Boot 4.1.2
- MySQL 8.x
- Gradle (wrapper 포함, 별도 설치 불필요)

## 로컬 실행 방법

1. **MySQL 설치 및 실행**
   - 로컬에 MySQL을 설치하고 서버가 켜져 있는지 확인합니다. (기본 포트 `3306`)
   - `team6` 데이터베이스는 앱 실행 시 자동으로 생성되므로 직접 만들 필요 없습니다.

2. **DB 계정 설정**
   - `config/application-local.properties.example` 파일을 복사해서
     같은 폴더에 `application-local.properties`로 저장합니다.
   - 본인 MySQL 계정(username / password)을 입력합니다.
   - 이 파일은 `.gitignore`에 등록되어 있어 깃허브에 올라가지 않고 jar에도 포함되지 않습니다. **절대 `git add -f`로 올리지 마세요.**
   - 예전 위치(`src/main/resources/`)에 이 파일이 있다면 `config/`로 옮겨 주세요. 예전 위치의 파일은 더 이상 읽지 않습니다.

3. **빌드 및 실행**
   ```bash
   ./gradlew build     # 빌드 + 테스트 (H2 인메모리 DB를 사용하므로 MySQL 불필요)
   ./gradlew bootRun   # 서버 실행 (http://localhost:8080, MySQL 필요)
   ```
   - Windows CMD/PowerShell에서는 `gradlew.bat build` 또는 `.\gradlew build`를 사용합니다.
   - IntelliJ에서는 `Team6Application`을 실행해도 됩니다. 작업 디렉터리는 프로젝트 루트여야 합니다.

## 실행 환경

| 프로필 | 사용 시점 | DB 접속 정보 | 스키마 |
| --- | --- | --- | --- |
| `dev` | 프로필을 지정하지 않은 로컬 실행 | `config/application-local.properties` | 앱이 자동 변경 (`update`) |
| `prod` | 배포 (`SPRING_PROFILES_ACTIVE=prod`) | 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 검증만 함 (`validate`) |
| `test` | 테스트 실행 시 자동 적용 | H2 인메모리 DB | 테스트마다 생성 후 삭제 (`create-drop`) |

## 패키지 구조

**기능(도메인)별로 패키지를 나눠서 개발합니다.** `controller`, `service`처럼 계층별로 최상위 패키지를 나누지 않습니다.

```
com.line1thon.team6
├── session          # 게스트 세션 (토큰 발급, 소유권 확인)
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── chat             # 채팅 · 질문 · 생각 제출
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── ...              # 기능이 늘어나면 같은 구조로 패키지 추가
└── global           # 여러 기능이 함께 쓰는 코드
    ├── config       # 설정 (CORS, Security 등)
    ├── exception    # 공통 예외, 전역 예외 처리
    └── common       # 공통 응답 형식, BaseEntity 등
```

### 규칙
- 새 기능은 최상위에 기능 이름으로 패키지를 만들고, 그 안에 `controller` / `service` / `repository` / `entity` / `dto`를 둡니다. 필요 없는 하위 패키지는 만들지 않아도 됩니다.
- 패키지 이름은 소문자 단수형으로 씁니다. (예: `chat`, `session`)
- 다른 기능의 데이터가 필요하면 그 기능의 `service`를 호출합니다. 다른 기능의 `repository`를 직접 쓰지 않습니다.
- 두 개 이상의 기능에서 쓰는 코드만 `global`에 둡니다. 한 기능에서만 쓰는 코드는 그 기능 패키지 안에 둡니다.
- 엔티티를 API 응답으로 그대로 반환하지 않고, `dto`로 변환해서 반환합니다.

## 브랜치 규칙

### 브랜치 구조
| 브랜치 | 용도 |
| --- | --- |
| `main` | 배포용. 안정적으로 동작하는 코드만 유지합니다. 직접 push 금지. |
| `dev` | 개발 통합 브랜치. 각자 작업한 기능이 모이는 곳입니다. 직접 push 금지. |
| `feat/기능명` | 새 기능 개발 (예: `feat/login`, `feat/post-crud`) |
| `fix/버그명` | 버그 수정 (예: `fix/login-error`) |
| `refactor/대상` | 리팩토링 (예: `refactor/user-service`) |

### 작업 흐름
1. 최신 `dev`를 받아서 작업 브랜치를 만듭니다.
   ```bash
   git checkout dev
   git pull origin dev
   git checkout -b feat/기능명
   ```
2. 작업 후 커밋하고 내 브랜치를 push합니다.
   ```bash
   git add .
   git commit -m "feat: 로그인 API 구현"
   git push origin feat/기능명
   ```
3. GitHub에서 **`feat/기능명` → `dev`** 로 Pull Request를 생성합니다.
4. CI(빌드와 테스트)가 통과하고 팀원 1명 이상 리뷰한 뒤 merge합니다. merge된 작업 브랜치는 삭제합니다.
5. `dev`가 안정적으로 동작하면 **`dev` → `main`** 으로 PR을 올려 merge합니다.

> PR을 올리기 전에 `dev`의 최신 내용을 내 브랜치에 반영(`git pull origin dev`)해서 충돌을 미리 해결해 주세요.

### 커밋 메시지 규칙
`타입: 내용` 형식으로 작성합니다.

| 타입 | 설명 |
| --- | --- |
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 코드 구조 개선 |
| `docs` | 문서 수정 (README 등) |
| `chore` | 빌드 설정, 패키지 등 기타 작업 |
| `test` | 테스트 코드 추가/수정 |
