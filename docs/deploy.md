# 배포

- 플랫폼: Railway
- 프로젝트: `ideal-enthusiasm` (Railway 대시보드 Settings에서 이름 변경 가능)
- 서비스
  - `line1thon_6_BE`: GitHub 레포 `line1thonsix/line1thon_6_BE` 연결 (Spring Boot, Java 21)
  - `MySQL`: Railway MySQL 템플릿
- 주소: https://line1thon6be-production.up.railway.app
- API 문서(Swagger): https://line1thon6be-production.up.railway.app/swagger-ui/index.html (`POST /api/sessions`로 받은 토큰을 Authorize에 넣고 호출). 끄려면 `application-prod.properties`에 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`
- 기본 배포: `main` 브랜치에 push하면 GitHub 자동 배포
- 수동 배포: `railway link` 후 `railway up -d`
- 로그 확인: `railway logs`

## 빌드와 실행 (Railway 서비스 설정)

`railway.json`(Config as Code)은 Railway가 지원 중단해서 쓰지 않고, 서비스 설정에 직접 넣었어요.

| 항목 | 값 |
| --- | --- |
| 빌더 | Railpack (Java 자동 감지) |
| 빌드 명령 | `./gradlew clean build -x test` (테스트는 GitHub CI에서 실행) |
| 실행 명령 | `java -jar build/libs/app.jar` |
| 재시작 | 실패 시 최대 3번 |

- `build.gradle`에서 `-plain.jar`를 만들지 않고 실행 jar 이름을 `app.jar`로 고정해서, 실행 명령이 버전과 상관없이 jar를 찾아요.
- 포트는 Railway가 주는 `PORT` 환경변수를 씀 (`application.properties`의 `server.port=${PORT:8080}`).

## 환경변수 (`line1thon_6_BE` 서비스)

| 이름 | 값 | 비고 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | 없으면 dev 프로필로 떠서 localhost DB에 접속하려다 실패 |
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | Railway 내부망으로 접속 |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` | |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | 직접 입력하지 않고 MySQL 서비스 값을 참조 |
| `GEMINI_API_KEY` | (직접 입력) | **필수.** prod는 키가 없으면 앱이 시작되지 않음 |
| `CORS_ALLOWED_ORIGINS` | (프론트 배포 주소) | 프론트 배포 후 입력. 없으면 localhost만 허용 |
| `TZ` | `Asia/Seoul` | 서버 시간대 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | **첫 배포에서 테이블을 만들기 위한 임시 값.** 테이블이 생기면 삭제해서 prod 기본값 `validate`로 되돌림 |
| `DEVEIL_MODEL` | (선택) | 기본 `gemini-3.5-flash-lite`. 기존 값이 `gemini-2.5-flash`면 변경하거나 삭제 |
| `DEVEIL_THINKING_LEVEL` | (선택) | 기본 `MINIMAL`. `MINIMAL` / `LOW` / `MEDIUM` / `HIGH` |

`${{MySQL.…}}`의 `MySQL`은 Railway의 MySQL 서비스 이름이에요. 서비스 이름을 바꾸면 여기도 같이 바꿔야 해요.

## 배포 전 검증

- [ ] `main`에서 GitHub CI(`./gradlew build`) 통과
- [ ] 필수 환경변수 입력 (`GEMINI_API_KEY` 포함)
- [ ] 첫 배포 후 테이블 생성 확인 → `SPRING_JPA_HIBERNATE_DDL_AUTO` 삭제
- [ ] `POST /api/sessions` 호출해서 `201`과 토큰이 오는지 확인

```bash
curl -i -X POST https://line1thon6be-production.up.railway.app/api/sessions
```

## 커스텀 도메인

1. Railway `line1thon_6_BE` 서비스 → Settings → Networking에서 도메인 추가
2. Railway가 알려 주는 CNAME(또는 TXT) 레코드를 도메인 구입처 DNS에 등록
3. 예전 EC2를 가리키던 A 레코드가 남아 있으면 삭제 (서버가 사라진 IP를 계속 가리키게 됨)
