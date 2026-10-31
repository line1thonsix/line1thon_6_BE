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
   - `src/main/resources/application-local.properties.example` 파일을 복사해서
     같은 위치에 `application-local.properties`로 이름을 바꿉니다.
   - 본인 MySQL 계정(username / password)을 입력합니다.
   - 이 파일은 `.gitignore`에 등록되어 있어 깃허브에 올라가지 않습니다. **절대 `git add -f`로 올리지 마세요.**

3. **빌드 및 실행**
   ```bash
   ./gradlew build     # 빌드 + 테스트 (MySQL이 켜져 있어야 통과)
   ./gradlew bootRun   # 서버 실행 (http://localhost:8080)
   ```
   - Windows CMD/PowerShell에서는 `gradlew.bat build` 또는 `.\gradlew build`를 사용합니다.
   - IntelliJ에서는 `Team6Application`을 실행해도 됩니다.
