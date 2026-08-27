# 백엔드 Render 배포 계획

- 검토일: 2026-08-26
- 검토 기준: 현재 `integration/render-test` 워킹 트리의 백엔드 코드와 설정
- 대상: Java/Spring Boot 백엔드의 Render Docker 배포 및 Supabase PostgreSQL 연결
- 현재 단계: 승인된 1단계 보안 수정과 2단계 seeder 분리를 워킹 트리에 적용했다. 빌드, 테스트, 이미지 생성, 컨테이너 실행, 배포, DB 접속·변경은 수행하지 않았으며 3단계 로컬 검증은 사용자가 직접 수행한다.

## 1. 표기 원칙

- **확인된 사실**: 현재 백엔드 코드나 파일에서 직접 확인한 내용이다.
- **기존 계획 유지**: 기존 문서의 내용이 코드 및 공식 문서와 일치해 유지한 내용이다.
- **결정 필요**: 코드만으로 확정할 수 없거나 구현 전에 승인이 필요한 내용이다.
- 실제 비밀번호, 키, 토큰, 프로젝트 식별자, 접속 문자열은 기록하지 않는다.

## 2. 오늘의 목표와 완료 조건

### 오늘의 목표

현재 백엔드가 실제로 어떻게 빌드·실행·설정되는지 확인하고, Render와 Supabase에 배포하기 전에 필요한 작업, 배포 차단 요소, 검증·장애 대응·롤백 절차를 실행 가능한 계획으로 정리한다.

### 이번 계획 검토의 완료 조건

- [x] Java, Spring Boot, Gradle Wrapper 버전을 코드 기준으로 확인했다.
- [x] 빌드·실행 명령과 Docker 실행 구조를 확인했다.
- [x] 서버 바인딩 주소, 실제 기본 포트, `PORT` 지원 여부를 확인했다.
- [x] DB, Supabase SSL, JWT, 관광·경로·카카오 API 환경변수를 확인했다.
- [x] CORS, 파일 업로드, 프로필, 테스트, 마이그레이션, Docker, 헬스 체크를 확인했다.
- [x] Render 배포를 차단하거나 공개 배포 전에 고쳐야 할 항목을 분리했다.
- [x] 구현 전에 승인이 필요한 결정을 정리했다.

### 실제 배포의 완료 조건

- [ ] 아래 `배포 차단 항목`의 필수 조치가 완료되고 사용자 로컬 검증을 통과했다.
- [ ] 배포에 필요한 파일이 Git에 추적되고 대상 브랜치에 Push됐다.
- [ ] 테스트가 실제 테스트 케이스를 실행하며 통과한다.
- [ ] Docker 이미지가 빌드되고 동일 이미지가 로컬 컨테이너에서 시작된다.
- [ ] Render가 `0.0.0.0:${PORT}`로 열린 애플리케이션을 감지한다.
- [ ] Supabase 연결, 스키마 버전, 공개 HTTPS API, 인증, 외부 API, CORS를 검증했다.
- [ ] 업로드 데이터의 임시성 또는 영속성 정책을 검증했다.
- [ ] 로그에 비밀값과 개인정보가 노출되지 않는다.
- [ ] 롤백 대상과 DB 복구 기준점을 확보했다.

## 3. 실제 코드에서 확인된 백엔드 실행 구조

```text
Flutter 모바일/웹
  -> Render Web Service HTTPS
     -> Docker: Java 17 JRE + Spring Boot 3.2.4
        -> Spring Security + 자체 JWT
        -> Spring Data JPA + PostgreSQL JDBC
           -> Supabase Supavisor Session Pooler 5432
        -> 관광 API / 두루누비 API / Kakao Mobility / 공개 OSRM
        -> 컨테이너 로컬 업로드 디렉터리
```

| 항목 | 확인된 사실 | 근거 |
| --- | --- | --- |
| Java | Java 17 | `build.gradle`, `Dockerfile` |
| Spring Boot | 3.2.4 | `build.gradle` |
| Gradle | Wrapper 8.14 | `gradle/wrapper/gradle-wrapper.properties` |
| 패키징 | Spring Boot 실행 JAR(`bootJar`) | Spring Boot Gradle plugin, `Dockerfile` |
| 서버 바인딩 | `0.0.0.0` | `application.yml` |
| 서버 포트 | `${PORT:8080}`. Render의 `PORT`를 지원하고 로컬 기본값은 8080 | `application.yml` |
| 컨테이너 시작 | `java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar` | `Dockerfile` |
| DB | PostgreSQL JDBC + Spring Data JPA | `build.gradle`, `application.yml` |
| 인증 | 애플리케이션 자체 JWT, JJWT 0.12.6 | `build.gradle`, `JwtUtil.java` |
| 설정 로딩 | Spring 환경변수 + 선택적 `.env` 로딩. `.env` 부재는 무시 | `SightseeingProjectApplication.java` |
| 활성 프로필 | 별도 프로필 강제 없음 | 전체 설정 파일 검색 |
| 업로드 | 로컬 파일 시스템에 저장하고 `/uploads/user-routes/**`로 노출 | `FileStorageService.java`, `WebConfig.java` |
| 시작 시 작업 | 기본 기동에서는 seeder가 생성되지 않는다. `APP_SEED_ENABLED=true`인 명시적 초기화 기동에서만 두루누비 동기화와 순례길 시드 저장을 시도한다. | `PilgrimageDataSeeder.java`, `application.yml` |

## 4. 기존 계획과 실제 코드의 차이

| 기존 계획 또는 가정 | 코드 대조 결과 | 반영 |
| --- | --- | --- |
| Java 17 / Spring Boot 3.2.4 | 일치 | 유지 |
| Render가 `PORT`를 주입하면 실행 가능 | `server.port=${PORT:8080}`, `server.address=0.0.0.0`로 확인 | 유지 |
| Supavisor Session Pooler 5432 사용 | Render는 IPv4 기반이고, 지속 실행되는 JDBC 백엔드와 prepared statement에는 Session mode가 적합 | 유지 |
| Supabase SSL 필수 | 코드는 SSL을 별도로 강제하지 않는다. `DB_URL`의 JDBC 파라미터에 전적으로 의존 | `sslmode` 책임과 검증 절차 추가 |
| `./gradlew clean test` 통과를 완료 조건으로 사용 | 테스트 의존성과 JUnit Platform 설정은 있으나 `src/test`에 테스트 파일이 없다. 현재는 0개 테스트로 성공할 수 있음 | 완료 조건 보완 |
| Docker 이미지 빌드 시 테스트도 검증 | `Dockerfile`은 `clean bootJar`만 실행하므로 테스트를 실행하지 않음 | 별도 테스트 게이트 필요 |
| 운영 CORS는 환경변수로 제한 | 중앙 CORS 설정만 사용하도록 `AuthController`의 `@CrossOrigin(origins="*")`를 제거했다 | 코드 반영 완료, 런타임 CORS 검증 필요 |
| 초기 업로드는 `/tmp` | Docker가 `FILE_UPLOAD_DIR=/tmp/uploads/user-routes`를 기본 설정 | 유지. 단, 재시작·재배포·무료 인스턴스 유휴 종료 시 손실 |
| 상태 확인은 공개 GET API 사용 | 전용 헬스 엔드포인트와 Actuator 의존성이 없음 | TCP 확인과 기능 확인을 분리하고 헬스 구현 결정 필요 |
| DB 스키마 준비 절차 | Flyway/Liquibase가 없고 Hibernate 기본값이 `ddl-auto=update` | 마이그레이션 계획 추가 |

## 5. Gradle 빌드와 실행 명령

다음 명령은 현재 Gradle 구성에서 유효한 절차다. 이 문서 검토 단계에서는 실행하지 않았다.

```bash
# 현재 테스트 파일이 없어 품질 게이트로는 불충분
./gradlew clean test

# Dockerfile과 동일한 실행 JAR 생성
./gradlew clean bootJar

# 로컬 개발 실행
./gradlew bootRun

# 생성된 실행 JAR 직접 실행
java -jar build/libs/*.jar
```

- **확인된 사실**: Java source compatibility는 17이고 test task는 JUnit Platform을 사용한다.
- **확인된 사실**: Docker 빌드는 `./gradlew --no-daemon clean bootJar`를 실행하며 테스트를 생략한다.
- **결정 필요**: Docker 빌드를 `clean build`로 바꿀지, CI에서 `clean test`를 선행하고 Docker는 `bootJar`만 수행할지 선택한다. 권장안은 CI 테스트와 이미지 빌드를 분리하는 것이다.

## 6. Docker 구성과 선택 이유

### 현재 구성

- `Dockerfile`과 `.dockerignore`가 백엔드 루트에 존재한다.
- 빌드 단계는 `eclipse-temurin:17-jdk-jammy`, 실행 단계는 `eclipse-temurin:17-jre-jammy`를 사용한다.
- Gradle Wrapper와 빌드 파일을 먼저 복사해 의존성 레이어 캐시를 유도한 뒤 소스를 복사한다.
- 실행 이미지는 JRE만 포함하고 `spring` 비루트 사용자로 실행한다.
- JVM 메모리는 컨테이너 메모리의 최대 75%를 사용하도록 설정한다.
- `EXPOSE 8080`은 이미지 메타데이터다. 런타임 포트는 `PORT`가 결정하므로 Render 기본 `PORT=10000`과 충돌하지 않는다.
- Dockerfile 자체 `HEALTHCHECK`는 없다.
- `.dockerignore`는 `.env`, 로컬 프로필, 빌드 산출물, IDE 파일, 문서, 업로드를 제외한다.

### Docker를 유지하는 이유

- Java와 OS 런타임을 Render 로컬 런타임 변화와 분리한다.
- 개발·CI·Render에서 동일한 실행 이미지를 검증할 수 있다.
- 멀티 스테이지 빌드와 비루트 실행이 이미 적용돼 있다.
- 모노레포 Root Directory를 `backend`로 고정하면 Docker 경로가 단순해진다.

### 현재 주의사항

- **확인된 사실**: `Dockerfile`과 `.dockerignore`는 현재 Git 미추적 상태다. Push 전에는 Render가 사용할 수 없다.
- **확인된 사실**: `src/main/resources/application.yml`도 Git 미추적 상태다. Push되지 않으면 현재 환경변수 매핑과 `PORT` 설정이 원격 빌드에 포함되지 않는다.
- **결정 필요**: 전용 HTTP health 구현 후 Dockerfile에도 `HEALTHCHECK`를 추가할지는 선택 사항이다. Render 외부 check만 사용할 수도 있다.

## 7. 생성·수정이 필요한 파일 목록

아래 `반영 완료`는 현재 워킹 트리 기준이며 빌드·테스트 완료를 의미하지 않는다. 기존 사용자 변경은 보존했다.

| 파일 | 현재 상태 | 필요한 작업 | 우선순위 |
| --- | --- | --- | --- |
| `docs/deployment-plan.md` | 존재, Git 미추적 | 현재 구현 상태와 사용자 검증 절차 반영 | 반영 완료 |
| `Dockerfile` | 존재, Git 미추적 | 내용 승인 후 Git 추가. 테스트/헬스/CA 전략에 따라 후속 수정 | 배포 전 필수 |
| `.dockerignore` | 존재, Git 미추적 | 비밀·로컬 파일 제외 상태를 유지하고 Git 추가 | 배포 전 필수 |
| `src/main/resources/application.yml` | 존재, Git 미추적 | `APP_SEED_ENABLED=false` 기본값 반영. 검증 후 Git 추가 | 코드 반영 완료/검증 필요 |
| `build.gradle` | 존재 | Actuator 및 migration 의존성, 테스트 게이트 결정 | 결정 필요 |
| `SecurityConfig.java` | 수정됨 | `/test/**` 허용 제거, `/api/v1/admin/**` 명시적 차단. health 허용은 추후 결정 | 코드 반영 완료/검증 필요 |
| `AuthController.java` | 수정됨 | 전체 와일드카드 `@CrossOrigin` 제거 | 코드 반영 완료/검증 필요 |
| `TestController.java` | 삭제됨 | 사용자·password 공개 조회와 평문 삽입 경로 제거 | 코드 반영 완료/검증 필요 |
| `User.java` | 수정됨 | password가 JSON 응답으로 직렬화되지 않도록 write-only 처리 | 코드 반영 완료/검증 필요 |
| `TrailController.java` | 수정됨 | 실제 역할 검증이 없던 동기화 관리 HTTP endpoint 제거 | 코드 반영 완료/검증 필요 |
| `DurunubiApiService.java`, `TourApiService.java` | 수정됨 | 키 포함 전체 URL, raw 응답 일부, URL 포함 가능 예외 메시지 로그 제거 | 코드 반영 완료/검증 필요 |
| `PilgrimageDataSeeder.java` | 수정됨 | 전체 seeder를 기본 비활성화하고 명시적 초기화 기동으로 전환 | 코드 반영 완료/검증 필요 |
| health controller 또는 Actuator 설정 | 없음 | 민감 정보 없는 liveness/readiness 생성 | 권장, HTTP health 전 필수 |
| `src/main/resources/db/migration/**` | 없음 | 선택한 migration 도구의 baseline과 버전 migration 생성 | 운영 배포 전 필수 |
| `src/test/**` | 디렉터리만 존재 | 컨텍스트, 인증, CORS, 저장소, 업로드, health 테스트 추가 | 배포 신뢰성 필수 |
| `application-prod.yml` | 없음 | 환경변수만으로 충분한지 검토 후 필요할 때만 생성 | 결정 필요 |
| `render.yaml` | 없음 | Dashboard 설정을 유지할지 Blueprint로 코드화할지 결정 | 선택 |

## 8. 환경변수 이름·용도·필수 여부

`필수`는 현재 코드가 정상 시작·기능 제공하기 위한 기준이다. 실제 값은 Render Secret/Environment에만 저장한다.

| 이름 | 필수 | 비밀 취급 | 기본값/대체 | 실제 코드 용도 |
| --- | --- | --- | --- | --- |
| `PORT` | Render 제공 | 아니요 | `8080` | HTTP 수신 포트 |
| `APP_LOG_LEVEL` | 아니요 | 아니요 | `INFO` | 애플리케이션 로그 레벨 |
| `DB_URL` | 예 | 예 | 없음 | PostgreSQL JDBC URL. SSL 파라미터 포함 필요 |
| `DB_USERNAME` | 예 | 예 | 없음 | PostgreSQL 사용자 |
| `DB_PASSWORD` | 예 | 예 | 없음 | PostgreSQL 비밀번호 |
| `JPA_DDL_AUTO` | 아니요 | 아니요 | `update` | Hibernate 스키마 동작. 운영값 결정 필요 |
| `JPA_SHOW_SQL` | 아니요 | 아니요 | `false` | SQL 로그 출력 |
| `JWT_SECRET` | 예 | 예 | 없음 | 자체 JWT HMAC 서명 키 |
| `JWT_EXPIRATION_TIME` | 아니요 | 아니요 | `86400000`ms | JWT 유효시간 |
| `TOUR_API_BASE_URL` | 예 | 아니요 | 없음 | 한국관광공사 관광 API 기본 주소 |
| `API_TOKEN` | 예 | 예 | 없음 | 관광 API 키이며 걷기 코스 키의 기본 대체값 |
| `TOUR_API_LOCATION_BASED_ENDPOINT` | 아니요 | 아니요 | `/locationBasedList2` | 위치 기반 관광 조회 |
| `TOUR_API_AREA_BASED_ENDPOINT` | 아니요 | 아니요 | `/areaBasedList2` | 지역 기반 관광 조회 |
| `TOUR_API_SEARCH_KEYWORD_ENDPOINT` | 아니요 | 아니요 | `/searchKeyword2` | 관광 키워드 검색 |
| `TOUR_API_IMAGE_LIST_ENDPOINT` | 아니요 | 아니요 | `/detailImage2` | 설정에는 있으나 현재 Java 코드에서 미사용 |
| `KAKAO_API_KEY` | 예 | 예 | 없음 | Kakao Mobility Directions 인증 |
| `ROUTE_API_BASE_URL` | 예 | 아니요 | 없음 | 두루누비/걷기 코스 API 기본 주소 |
| `WALKING_COURSE_SERVICE_KEY` | 조건부 | 예 | `API_TOKEN` | 두루누비 키. 별도 값이 없으면 `API_TOKEN` 사용 |
| `WALKING_COURSE_LIST_ENDPOINT` | 아니요 | 아니요 | `/courseList` | 두루누비 코스 목록 |
| `WALKING_COURSE_ROUTE_ENDPOINT` | 아니요 | 아니요 | `/routeList` | 설정에는 있으나 현재 Java 코드에서 미사용 |
| `FILE_UPLOAD_DIR` | 아니요 | 아니요 | 앱 `./.local/uploads/user-routes`; Docker `/tmp/uploads/user-routes` | 업로드 디렉터리 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Flutter Web 사용 시 예 | 아니요 | `http://localhost:*` | 쉼표 구분 브라우저 허용 Origin pattern |
| `APP_SEED_ENABLED` | 아니요 | 아니요 | `false` | `true`일 때만 두루누비 동기화와 순례길 초기 데이터를 실행. 일반 Web Service에서는 생략하거나 `false` 유지 |

추가 확인 사항:

- Kakao와 OSRM 기본 URL은 Java 코드에 하드코딩돼 있다. Kakao는 키만 환경변수이고 공개 OSRM은 환경변수와 키가 없다.
- 전역 `RestTemplate`의 연결 timeout은 5초, 읽기 timeout은 10초다.
- `JWT_SECRET`은 JJWT HMAC 요구 길이를 만족하는 충분히 긴 무작위 값이어야 한다. 짧으면 인증 시 `WeakKeyException`이 날 수 있다.

## 9. 이미지 빌드 및 컨테이너 실행 절차

아래 절차는 사용자가 백엔드 루트에서 직접 수행한다. 현재 Fedora 환경에서는 Podman을 사용한다. 실제 비밀값을 명령행에 직접 쓰지 않고 로컬 `.env` 파일 경로만 전달한다.

```bash
./gradlew clean test
./gradlew clean bootJar
podman build -t sightseeing-backend:local .
podman run --rm \
  --name sightseeing-backend-local \
  --env-file .env \
  -e PORT=8080 \
  -p 8080:8080 \
  sightseeing-backend:local
```

정상 Web Service 검증에서는 `APP_SEED_ENABLED`를 지정하지 않는다. 기본값 `false`가 적용되어야 한다.

명시적 초기화는 DB 쓰기와 외부 API 호출·quota 소비를 발생시킨다. 운영 DB에서 임의 실행하지 않고, 대상 DB의 백업·중복 처리·키 권한을 확인한 뒤 폐기 가능한 로컬/스테이징 DB에 한해 별도 승인하여 실행한다. 같은 이미지를 `APP_SEED_ENABLED=true`로 한 번 기동한 후 완료 로그를 확인하고 종료하는 방식이며, 일반 Render Web Service 환경변수에는 이 값을 남기지 않는다.

검증 기준:

1. `clean test`와 `clean bootJar`가 종료 코드 0으로 끝나는지 확인한다. 현재 테스트 파일이 없으므로 test 성공만으로 기능 검증 완료로 보지 않는다.
2. 이미지에 `.env`, 로컬 설정, 업로드 파일, 문서가 포함되지 않았는지 확인한다.
3. 비루트 `spring` 사용자로 시작되는지 확인한다.
4. `0.0.0.0:8080` 바인딩과 Spring Boot 시작 완료를 확인한다.
5. 종료 시 SIGTERM을 받고 정상 종료하는지 확인한다.
6. 로그에 JDBC URL, 비밀번호, JWT, API 키, `serviceKey=`가 나오지 않는지 확인한다.
7. 기본 기동 로그에 두루누비 동기화가 없고 DB 초기 데이터가 새로 쓰이지 않는지 확인한다.

## 10. 데이터베이스 연결 및 마이그레이션 절차

### 현재 연결 방식

- Spring은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 PostgreSQL datasource에 전달한다.
- Render는 IPv4 연결 환경이므로 Supabase Direct connection보다 Shared Pooler의 **Session mode 5432**를 사용한다.
- 지속 실행되는 Spring/HikariCP와 prepared statement 호환성 때문에 Transaction mode 6543은 현재 계획에서 제외한다.
- JDBC URL 형식은 값 없이 다음처럼 관리한다.

```text
jdbc:postgresql://<session-pooler-host>:5432/<database>?sslmode=require
```

### SSL 정책

- **확인된 사실**: 현재 코드는 SSL 옵션이나 CA 인증서를 추가하지 않는다. `DB_URL`이 SSL 사용 여부를 결정한다.
- 초기 검증의 최소 기준은 `sslmode=require`다. 전송 암호화는 강제하지만 서버 인증서와 호스트 이름은 검증하지 않는다.
- 운영 권장 목표는 Supabase CA 인증서를 사용한 `sslmode=verify-full`이다.
- **결정 필요**: CA 인증서를 Render Secret File 등 이미지 외부에서 주입하고 JDBC가 읽게 하는 방식, Supabase SSL enforcement 활성화 시점을 승인한다.
- SSL enforcement 변경은 DB 재시작을 유발할 수 있으므로 배포 시간대와 복구 계획을 먼저 잡는다.

### 현재 스키마 관리 상태

- Flyway/Liquibase 의존성과 migration 파일이 없다.
- `JPA_DDL_AUTO` 기본값은 `update`다.
- 따라서 Hibernate가 시작 시 스키마를 암묵적으로 변경할 수 있고 변경 이력·순서·rollback SQL이 없다.
- Supabase 연결 실패, DDL 권한 부족, 기존 스키마 불일치가 있으면 JPA 초기화에서 배포가 실패할 수 있다. seeder는 기본 비활성이라 일반 기동 실패 원인에서 제외된다.

### 권장 마이그레이션 절차

1. 대상 Supabase 프로젝트와 기존 데이터 보존 요구를 확정한다.
2. 변경 전 스키마·데이터 백업 또는 복구 지점을 확보한다.
3. **결정 필요**: Flyway 우선안을 채택할지 Liquibase를 사용할지 승인한다.
4. 현재 운영 스키마를 기준으로 baseline을 만들고 JPA entity와 대조한다.
5. 빈 PostgreSQL과 운영 스키마 사본에서 migration을 각각 검증한다.
6. 실행 주체를 CI/관리 작업/Render pre-deploy 중에서 결정한다. 다중 인스턴스 시작과 migration을 동시에 수행하지 않는다.
7. 도입 후 운영 `JPA_DDL_AUTO`를 `validate`로 바꿔 불일치만 검출한다.
8. migration 성공 후 배포하고 DB 읽기·쓰기 smoke test를 수행한다.

초기 일회성 검증에서 `update`를 유지하려면 대상 DB가 폐기 가능하거나 복구 가능한지 확인하고 별도 승인한다. 운영 기본안으로 간주하지 않는다.

## 11. 파일 업로드 경로와 영속성

### 확인된 사실

- `FileStorageService`가 `FILE_UPLOAD_DIR`을 만들고 UUID 기반 파일명으로 로컬 디스크에 복사한다.
- DB에는 `/uploads/user-routes/<generated-name>` 형태 URL이 저장된다.
- `WebConfig`가 로컬 디렉터리를 `/uploads/user-routes/**`로 공개한다.
- Docker 기본 경로는 `/tmp/uploads/user-routes`다.
- 파일 크기, MIME type, 확장자 allowlist, 악성 파일 검사는 현재 코드에 없다.

### Render 적용

- Render Free Web Service 파일 시스템은 임시이며 15분간 인바운드 트래픽이 없으면 종료될 수 있다. 재시작, 유휴 종료, 재배포 시 업로드가 사라진다.
- 무료 플랜에는 persistent disk를 붙일 수 없다.
- 초기 검증에서는 파일 손실을 허용하고 업로드·조회 형식만 확인할 수 있다.
- 영속성이 필요하면 다음 중 하나를 승인한다.
  1. Supabase Storage로 전환하고 Storage RLS·공개 URL·삭제 정책을 설계한다. 운영 권장안이다.
  2. Render 유료 서비스와 단일 인스턴스 persistent disk를 사용하고 `FILE_UPLOAD_DIR`을 mount path에 맞춘다.

DB 메타데이터와 실제 파일의 생명주기가 달라질 수 있으므로 업로드 실패·DB 저장 실패·파일 삭제의 보상 정책도 결정한다.

## 12. 프로필별 설정 파일

- **확인된 사실**: 현재 `application.yml` 하나뿐이다.
- `application-prod.yml`, `application-test.yml` 등 프로필별 설정은 없다.
- `.gitignore`와 `.dockerignore`는 `application-local.yml`, `application-*.local.yml`을 제외한다.
- `SPRING_PROFILES_ACTIVE` 없이도 현재 환경변수 기반 구성은 동작한다.
- **확인된 사실**: seeder는 공통 `application.yml`에서 기본 `false`이므로 프로필과 무관하게 명시적 opt-in 없이는 실행되지 않는다.
- 테스트에는 운영 Supabase를 사용하지 않는 `application-test.yml` 또는 Testcontainers 구성이 필요하다.

## 13. 테스트 구성

### 확인된 사실

- `spring-boot-starter-test`가 있고 JUnit Platform을 사용한다.
- `src/test/java`, `src/test/resources` 디렉터리는 있지만 테스트 파일은 없다.
- H2, Testcontainers, 별도 테스트 DB, 테스트 프로필이 없다.
- 따라서 현재 `clean test` 성공은 시작, DB, 인증, CORS, 업로드를 검증하지 않는다.

### 배포 전 최소 테스트

- Spring context 시작과 필수 환경변수 누락 테스트
- PostgreSQL repository 통합 테스트(Testcontainers 권장)
- 회원가입·로그인·JWT 유효/만료/변조 테스트
- 인증 필요 경로의 401/403 및 공개 경로 테스트
- CORS 허용/차단 Origin과 preflight 테스트
- health liveness/readiness 테스트
- 파일 업로드 확장자·크기·경로·조회 테스트
- 외부 API mock 정상·timeout·fallback 테스트
- 기본 설정에서 seeder bean이 생성되지 않고, 명시적 opt-in에서만 생성되는지 테스트

## 14. 상태 확인 방법

### 현재 가능한 확인

- Render 기본 TCP check로 컨테이너가 포트를 열었는지 확인한다.
- Render 로그에서 포트 바인딩, Spring context, Hikari datasource를 확인하고 seeder 로그가 없는지 확인한다.
- `/api/v1/pilgrimages` 같은 DB 조회 API는 DB smoke test로만 사용하고 liveness로 쓰지 않는다.
- 관광·Kakao API 경로는 외부 장애의 영향을 받으므로 health path로 쓰지 않는다.

### 현재 없는 것과 권장 목표

- Actuator 의존성, `/health`, `/actuator/health`, Docker `HEALTHCHECK`가 없다.
- 민감한 상세를 반환하지 않는 `/actuator/health` 또는 `/health`를 구현한다.
- liveness는 JVM/Spring 응답만 확인하고 readiness에 DB를 포함할지는 결정한다.
- Render HTTP Health Check Path에는 5초 안에 2xx/3xx를 반환하는 전용 path만 설정한다.
- 구현 전 첫 배포에서는 비즈니스 API를 health로 쓰지 않고 기본 TCP check를 사용한다.

## 15. Render 설정 절차

1. 차단 항목을 수정하고 테스트·이미지 검증을 완료한다.
2. `Dockerfile`, `.dockerignore`, `application.yml`을 포함한 변경을 `integration/render-test`에 커밋·Push한다.
3. Render에서 `New > Web Service`를 선택하고 Git 저장소를 연결한다.
4. Branch를 `integration/render-test`, Root Directory를 `backend`로 지정한다.
5. Language/Runtime은 `Docker`로 선택한다.
6. Root Directory 기준 Dockerfile Path는 `./Dockerfile`, Docker Context는 `.`로 지정한다.
7. Docker Command는 비워 Dockerfile `ENTRYPOINT`를 사용한다.
8. 초기 검증은 Free instance를 사용할 수 있으나 cold start와 업로드 손실을 허용한다.
9. 환경변수 표의 필수값을 Environment에 등록하고 비밀값은 Secret으로 취급한다.
10. `PORT`는 Render가 제공하게 두고 고정 포트를 강제하지 않는다.
11. 초기에는 기본 TCP check를 사용하고 전용 health 구현 후 HTTP Health Check Path를 설정한다.
12. 첫 배포 로그에서 build, JAR 시작, 포트 감지, DB SSL 연결을 확인하고 seeder가 실행되지 않았는지 확인한다.
13. `onrender.com` HTTPS 주소로 smoke test를 수행한다.
14. Flutter API base URL을 Render HTTPS 주소로 바꾸고 모바일·웹 통합 검증을 수행한다.
15. 검증 후 Auto-Deploy 유지 여부와 운영 브랜치를 결정한다.

## 16. 배포 후 핵심 검증 시나리오

1. **Cold start**: 무료 인스턴스 첫 요청과 완전 기동 시간을 기록한다.
2. **상태 확인**: TCP 또는 전용 health가 성공하고 비즈니스 API 장애가 liveness를 실패시키지 않는지 확인한다.
3. **DB 읽기**: 공개 조회 API가 Supabase 데이터를 정상 조회한다.
4. **인증**: 테스트 계정으로 회원가입·로그인하고 JWT를 검증한다. 토큰 없음/변조/만료는 거절한다.
5. **권한**: 다른 사용자의 경로·댓글·업로드를 수정·삭제할 수 없는지 확인한다.
6. **관광 API**: 위치·지역·키워드 조회와 timeout 응답을 확인한다.
7. **두루누비 API**: 일반 Web Service 기동에서는 동기화가 실행되지 않는다. 별도 승인된 초기화에서만 동기화와 서비스 키 비노출을 확인한다.
8. **Kakao/OSRM**: 정상 경로와 외부 API 실패 fallback을 확인한다.
9. **CORS**: 승인된 Flutter Web HTTPS Origin은 허용하고 임의 Origin은 차단한다.
10. **업로드**: 업로드·조회 및 선택한 임시/영속 정책을 확인한다.
11. **공개 표면**: `/test/**`가 사용할 수 없고 사용자 password 필드가 응답에 없음을 확인한다.
12. **로그**: DB 접속 정보, JWT, API 키, 사용자 비밀번호가 검색되지 않는지 확인한다.

## 17. 로그 확인 및 장애 진단

실제 비밀값을 검색어·스크린샷·이슈에 붙이지 않는다. 환경변수는 이름만 확인한다.

| 증상 | 우선 확인 | 조치 방향 |
| --- | --- | --- |
| Render가 포트를 찾지 못함 | `server.address`, `PORT`, 바인딩 로그 | `0.0.0.0:${PORT}` 유지, Docker Command override 제거 |
| `Could not resolve placeholder` | 누락 환경변수 이름 | 환경변수 표와 Render 설정 대조 후 재배포 |
| Hikari timeout/connection refused | Session Pooler host/5432, 프로젝트 상태 | Direct IPv6 대신 Supavisor Session Pooler 확인 |
| DB 인증 실패 | username 형식과 password secret | 값을 출력하지 말고 Dashboard와 재대조·회전 |
| SSL handshake 실패 | JDBC `sslmode`, CA, SSL enforcement | `require`/`verify-full` 정책과 CA 경로 대조 |
| Hibernate schema/DDL 실패 | `JPA_DDL_AUTO`, DB 권한, migration | migration 확인 후 배포 중단, 임의 DDL 금지 |
| 시작이 오래 걸림 | `APP_SEED_ENABLED`가 실수로 `true`인지, DB/JPA 초기화와 외부 연결 timeout | 일반 Web Service 값을 제거 또는 `false`로 복구하고 재배포 |
| JWT `WeakKeyException` | `JWT_SECRET` 길이 | 충분히 긴 무작위 키로 회전 후 재배포 |
| Flutter Web CORS 실패 | 실제 Origin, 중앙 pattern, controller annotation | 와일드카드 제거 후 단일 정책으로 통합 |
| 업로드 404/손실 | `FILE_UPLOAD_DIR`, 재시작·유휴 종료 | 초기 한계 확인 또는 Storage/disk 전환 |
| 외부 API 실패 | status, timeout, quota | 키를 노출하지 않고 공급자 상태·quota 확인 |

확인 순서는 Render Events의 실패 단계 → 포트 → 필수 placeholder → DB DNS/SSL/인증 → schema → seeder → 외부 API다. 동일 오류를 수정 없이 반복 배포하지 않는다. 비밀 노출이 확인되면 즉시 키를 회전한다.

## 18. 롤백 절차

1. 데이터 손상 또는 비밀 노출이면 먼저 트래픽·Auto-Deploy를 중지하고 키를 회전한다.
2. Render Events에서 직전 성공 deploy를 선택해 `Rollback`한다.
3. Dashboard 롤백 후 Auto-Deploy가 비활성화됐는지 확인하고 원인 수정 전 다시 켜지 않는다.
4. 롤백 인스턴스의 health, DB 조회, 인증을 다시 검증한다.
5. 애플리케이션 rollback은 이미 실행된 DB schema와 데이터를 되돌리지 않는다.
6. 호환되지 않는 migration은 준비한 forward-fix 또는 승인된 DB 복구 지점을 사용한다. JPA `update` 변경은 자동 rollback할 수 없다.
7. persistent disk를 채택하면 애플리케이션 rollback과 disk 상태는 별개이므로 필요 시 snapshot을 복구한다.
8. 원인 수정과 전체 검증 후 Auto-Deploy를 다시 켠다.

## 19. 보안 점검 항목

- [ ] `.env`, 접속 문자열, 비밀번호, JWT, API 키가 Git·이미지·문서에 없다.
- [ ] Render Secret 접근 권한이 최소 인원으로 제한된다.
- [x] 현재 워킹 트리에서 두루누비/관광 API 전체 URL과 raw 응답 일부 로그를 제거했다. 사용자 런타임 검증은 남아 있다.
- [x] 현재 워킹 트리에서 공개 `/test/**`와 사용자 전체 조회·평문 비밀번호 삽입 경로를 제거하고 password 직렬화를 차단했다. 사용자 런타임 검증은 남아 있다.
- [x] 현재 워킹 트리에서 `AuthController`의 `@CrossOrigin("*")`를 제거하고 중앙 CORS allowlist만 남겼다. 사용자 런타임 검증은 남아 있다.
- [x] 현재 워킹 트리에서 역할 체계가 없는 `/api/v1/admin/trails/sync`를 제거하고 `/api/v1/admin/**`를 기본 차단했다. 사용자 런타임 검증은 남아 있다.
- [x] seeder를 기본 비활성화했다. `APP_SEED_ENABLED=true`는 승인된 초기화에서만 사용한다.
- [ ] JWT secret이 충분히 길고 무작위이며 만료·회전 정책이 있다.
- [ ] 로그인·회원가입에 입력 검증, 중복 처리, brute-force 방어가 있다.
- [ ] DB runtime role과 migration 권한을 가능한 한 분리한다.
- [ ] Supabase `public` schema의 Data API 노출과 RLS를 확인한다. 직접 JDBC `postgres` 연결은 RLS를 우회할 수 있음을 전제로 한다.
- [ ] PostgreSQL SSL을 최소 `require`, 운영 목표 `verify-full`로 검증한다.
- [ ] SQL 출력과 앱 로그를 운영에서 `INFO` 이상으로 두고 민감 query parameter를 마스킹한다.
- [ ] 업로드 크기·MIME·확장자 allowlist, 경로 containment, 악성 파일, 삭제 정책을 구현한다.
- [ ] 공개 Swagger/OpenAPI와 관리 API 노출 필요성을 검토한다.
- [ ] OWASP dependency-check를 CI에서 실행할지 결정한다.
- [ ] Spring Boot 3.2.4 및 직접 지정 의존성의 보안 지원·업그레이드를 별도 검토한다.

## 20. 배포를 차단하는 기존 오류와 위험

현재 상태는 공개 Render 배포 기준 **NO-GO**다. 1·2단계 코드는 반영됐지만 사용자 로컬 검증과 나머지 필수 항목 해결 또는 명시적 위험 승인 전에는 공개 URL을 열지 않는다.

| 등급 | 확인된 문제 | 배포 영향 | 필요한 조치 |
| --- | --- | --- | --- |
| 차단 | `Dockerfile`, `.dockerignore`, `application.yml`이 Git 미추적 | 원격 빌드에 Docker·환경 설정이 전달되지 않음 | 내용 승인 후 Git 추가·Push |
| 검증 대기 | `/test/**`, 평문 삽입, password 응답 | 코드에서 제거·직렬화 차단 완료 | 사용자 빌드 및 HTTP 부재 확인 |
| 검증 대기 | 두루누비/관광 API key 포함 URL 로그 | 코드에서 URL·민감 예외 로그 제거 완료 | 사용자 실행 로그 확인; 과거 배포 로그에 노출됐다면 키 회전 |
| 검증 대기 | 빈 DB 시작 시 자동 두루누비/GPX 호출과 DB write | `APP_SEED_ENABLED=false` 기본값으로 전체 seeder 비활성화 완료 | 사용자 기본 기동에서 미실행 확인 |
| 검증 대기 | 중앙 CORS와 controller 와일드카드 공존 | controller annotation 제거 완료 | 허용·비허용 Origin preflight 확인 |
| 검증 대기 | 관리자 역할 없이 동기화 endpoint 호출 가능성 | endpoint 제거 및 `/api/v1/admin/**` deny-all 완료 | 일반 JWT로 2xx가 아닌지 확인 |
| 남은 보안 위험 | Kakao/OSRM 실패 로그에 정확한 좌표와 예외 메시지가 포함됨 | 사용자 위치정보가 로그에 남을 수 있음 | 별도 보안 로그 정리 승인 후 좌표·raw message 제거 |
| 운영 차단 | migration 없이 `ddl-auto=update` | 재현 가능한 schema 배포·DB rollback 불가 | baseline migration과 `validate` 전환 |
| 운영 차단 | 업로드가 무료 Render 임시 파일 시스템 | 재시작·유휴 종료·배포 때 파일 손실 | Supabase Storage 또는 유료 disk 선택 |
| 품질 차단 | 테스트 파일과 테스트 DB가 없음 | `clean test`가 기능을 검증하지 않음 | 최소 테스트 스위트 추가 |
| 관측성 공백 | 전용 health endpoint 없음 | 애플리케이션 readiness 판단 불가 | 구현 전 TCP, 이후 전용 health 추가 |

모든 필수 환경변수가 없으면 Spring placeholder 해석 또는 bean 생성에서 시작이 실패한다. JWT secret 길이 문제는 시작 후 최초 JWT 사용 시 드러날 수 있다.

## 21. 이번 작업에서 제외할 항목

- `build.gradle`, Dockerfile, `.dockerignore` 수정
- 빌드·테스트·Docker/Podman 명령 실행
- Render 서비스 생성·수정·배포
- Supabase 접속, SQL 실행, schema/data/RLS/Storage 변경
- 실제 환경변수·비밀값 조회, 검증, 복사, 회전
- Flutter 코드와 API base URL 수정
- 새 health, migration, test, CI, Blueprint 파일 생성
- 커밋, Push, PR 생성

## 22. 아직 결정되지 않은 사항과 구현 승인 요청 목록

1. **1단계 보안 수정**: 구현 결정 완료. 현재 워킹 트리에 반영했으며 사용자의 빌드·런타임 검증 결과에 따라 보완한다.
2. **2단계 seeder 분리**: 구현 결정 완료. 기본 비활성, `APP_SEED_ENABLED=true`인 승인된 초기화 기동만 허용한다. Render에서 초기화를 수행할 운영 방식은 배포 전 별도 결정한다.
3. **health**: 첫 배포 뒤 Actuator(`/actuator/health`) 도입을 검토한다. 도입 전에는 TCP check를 사용한다.
4. **DB migration**: Flyway baseline과 `JPA_DDL_AUTO=validate` 전환 승인. 초기 검증에서 한시적 `update` 허용 여부도 결정.
5. **Supabase SSL**: 첫 검증 `require`, 운영 `verify-full` 단계적 전환과 CA 주입 방식 승인.
6. **업로드 영속성**: Supabase Storage 전환 방향은 선택했다. 이번 배포 단계에서는 구현 시점·RLS·파일 접근 정책을 결정해야 한다.
7. **테스트 환경**: 운영 Supabase Cloud를 자동 테스트 대상으로 바로 사용하지 않는다. 별도 Supabase 프로젝트/브랜치 또는 Testcontainers 중 선택하고 최소 배포 게이트를 결정한다.
8. **Render 관리**: Dashboard 수동 설정 또는 저장소 루트 `render.yaml` Blueprint 선택.
9. **배포 등급**: Free의 cold start·15분 유휴 종료·비영속 파일을 수용할지 유료로 시작할지 결정.
10. **DB 권한·RLS**: runtime 최소 권한 role과 Data API/RLS 검증을 이번 범위에 포함할지 결정.
11. **의존성 업그레이드**: Java 17은 유지하되 Spring Boot 3.2.4 등 업그레이드를 배포 전에 할지 검토.

## 23. 공식 참고 자료

- [Render Web Services와 PORT](https://render.com/docs/web-services)
- [Render Docker 배포](https://render.com/docs/docker)
- [Render 모노레포 Root Directory](https://render.com/docs/monorepo-support)
- [Render Health Checks](https://render.com/docs/health-checks)
- [Render Free 서비스 제한](https://render.com/docs/free)
- [Render Persistent Disks](https://render.com/docs/disks)
- [Render Rollbacks](https://render.com/docs/rollbacks)
- [Supabase PostgreSQL 연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase PostgreSQL SSL Enforcement](https://supabase.com/docs/guides/platform/ssl-enforcement)

---

이 문서는 구현 승인을 받기 위한 검증 계획이다. 미결정 사항과 차단 항목의 처리 방향이 승인되기 전에는 공개 배포를 진행하지 않는다.
