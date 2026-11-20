# 신규 개발자 온보딩 안내서

`mock-data-gen`은 데이터베이스나 캐시 없이 `SensorId`와 시각만으로 결정론적 센서 측정값을 생성하는 Gradle 멀티 프로젝트다. 이 문서는 전체 구조, 로컬 빌드와 검증 절차, `example-app` 실행과 API 확인 방법, 개발 규칙을 안내한다.

## 1. 프로젝트 개요

운영 백엔드나 시계열 저장소가 없는 로컬과 개발 환경에서는 센서 현재 값과 시계열 데이터 조회 API를 테스트하기 어렵다. `mock-data-gen`은 요청 시점에 해시와 수학 파형으로 값을 즉시 계산하므로 별도 상태 저장소나 시드 데이터가 필요 없다.

### 핵심 동작 원리

- **결정론적 파형 생성.** `DeterministicWaveCalculator`가 `SensorId`를 FNV-1a 64비트 해시(`Sensor Seed`)로 변환하고, 5가지 `Pattern`(`STEADY`, `SINE`, `STEP`, `DRIFT`, `ANOMALY`) 중 하나와 기준값, 진폭, 주기, 위상을 고정 배정한다.
- **시각 정렬.** 현재 값 조회는 기준 시각을 `intervalMs` 단위로 내림 정렬(`Current Quantization`)하고, 시계열 조회는 시작 시각을 `intervalMs`의 정수 배수 격자로 올림 정렬(`Grid Alignment`)한다. 같은 시각과 주기로 호출하면 어느 인스턴스에서나 동일한 값을 반환한다.
- **헤더 기반 요청 분기.** `MockSensorController`는 `X-Mock-Data=true` 헤더가 있는 요청만 가로채어 Mock 응답(`mock: true`)을 반환한다. 헤더가 없거나 값이 다르면 애플리케이션의 운영 컨트롤러가 요청을 처리한다.
- **프로필과 예외 격리.** Starter 자동설정은 `local` 또는 `dev` 서블릿 웹 환경에서만 활성화된다. 입력 검증 예외는 전역 `@RestControllerAdvice` 없이 `MockSensorController` 내부 `@ExceptionHandler`에서 `400 Bad Request`로 처리한다.

도메인 용어 정의와 금지 동의어는 [GLOSSARY.md](../GLOSSARY.md)에서 확인한다. 설계 배경은 [ADR 목록](./adr/)에 기록되어 있다.

### 모듈 구성

| 모듈 | 역할 | 배포 여부 | 주요 패키지 |
| :--- | :--- | :--- | :--- |
| `mock-data-core` | Spring 비의존 순수 Java 21 해시, 파형, 격자 정렬 엔진 | Maven 배포 (`com.samsung.ees.infra.mock:mock-data-core:0.1.0-SNAPSHOT`) | `com.samsung.ees.infra.mock.core` |
| `mock-data-spring-boot-starter` | `@Profile({"local", "dev"})` 자동설정, `MockSensorController`, 조건부 Springdoc 커스터마이저 | Maven 배포 (`com.samsung.ees.infra.mock:mock-data-spring-boot-starter:0.1.0-SNAPSHOT`) | `com.samsung.ees.infra.mock.starter` |
| `example-app` | `mock-data-spring-boot-starter`와 운영 `SensorController`를 함께 실행해 라우팅과 프로필 격리를 검증하는 예시 앱 | 배포 제외 | `com.samsung.ees.infra.mock.example` |

3개 모듈로 나눈 이유와 Spring Boot Starter 프로퍼티 네임스페이스(`ees.mock.sensor.*`) 분리 근거는 [ADR-0001](./adr/0001-multi-module-library-boundary.md)과 [ADR-0003](./adr/0003-starter-property-prefix-separation.md)을 참고한다.

## 2. 사전 요구 사항

- **JDK 21 이상.** `build.gradle`의 `sourceCompatibility`, `targetCompatibility`, `options.release`는 모두 `21`이며, CI는 Temurin 21에서 실행된다.
- **Gradle Wrapper.** 저장소 루트의 `gradlew.bat` 또는 `./gradlew`(Gradle `9.8.2`)를 사용하므로 별도 Gradle 설치가 필요 없다.
- **Git과 `gh` CLI.** 소스 관리에 `git`을 사용한다. GitHub 이슈와 PR 작업에는 `gh` CLI를 사용한다.

## 3. 저장소 구조

```text
mock-data-gen/
├── build.gradle                     # 공통 Java 21 컴파일 옵션(-Werror), Javadoc, verifyMavenPublication 정의
├── settings.gradle                  # 3개 서브프로젝트(mock-data-core, mock-data-spring-boot-starter, example-app) 선언
├── README.md                        # 라이브러리 개요, Starter 설정 요약, 외부 프로젝트 연동 방법
├── GLOSSARY.md                      # 도메인 표준 용어집
├── CODING_STANDARDS.md              # 코드 리뷰 기준 및 모듈 경계·테스트 규칙
├── AGENTS.md                        # 에이전트 작업 규칙 및 agent-log.md 기록 규칙
├── 프로젝트사양서.md                   # 초기 상세 사양서(수식 및 요구사항 원문)
├── docs/
│   ├── ONBOARDING.md                # 이 문서
│   ├── adr/                         # 아키텍처 결정 기록(0001~0003)
│   └── agents/                      # 이슈 트래커·트리아지 라벨·도메인 문서 가이드
├── .github/workflows/ci.yml         # PR/푸시 시 clean check publishToMavenLocal 실행
├── mock-data-core/                  # 순수 Java 21 생성 엔진 및 단위 테스트
├── mock-data-spring-boot-starter/   # Spring Boot 3.5.16 자동설정, 웹 컨트롤러, OpenAPI 커스터마이저
└── example-app/                     # 로컬 실행 및 프로필별 통합 테스트용 Spring Boot 애플리케이션
```

## 4. 로컬 빌드, 테스트 및 배포 검증

저장소 루트에서 Gradle Wrapper를 실행한다. Windows PowerShell에서는 `.\gradlew.bat`을, macOS와 Linux에서는 `./gradlew`를 사용한다.

### 전체 빌드 및 검증 (`clean check`)

```powershell
.\gradlew.bat clean check
```

```bash
./gradlew clean check
```

`clean check`는 컴파일, 단위와 통합 테스트, JAR·sources JAR·javadoc JAR 생성, `verifyMavenPublication` 태스크를 실행한다. 컴파일 옵션에 `-Xlint:all,-processing -Werror`가 설정되어 있으므로 경고가 발생하면 빌드가 실패한다.

`verifyMavenPublication`은 아래 조건을 검사해 의존성 누수를 막는다.

- `mock-data-core`는 POM과 Gradle `apiElements`에 외부 의존성이 0개여야 한다.
- `mock-data-spring-boot-starter`는 POM 의존성으로 `mock-data-core`와 `spring-boot-starter-web` 두 개만 포함해야 하며, `springdoc`, `configuration-processor`, `test` 스코프 의존성이 전파되면 실패한다.

### 로컬 Maven 저장소 배포 (`publishToMavenLocal`)

외부 프로젝트에서 `mavenLocal()`로 이 라이브러리를 참조하려면 로컬 `~/.m2` 저장소에 배포한다.

```powershell
.\gradlew.bat publishToMavenLocal
```

CI(`.github/workflows/ci.yml`)와 동일한 전체 검증을 로컬에서 실행하려면 아래 명령을 사용한다.

```powershell
.\gradlew.bat clean check publishToMavenLocal --no-daemon --console=plain
```

외부 프로젝트 연동 설정(`mavenLocal`과 `includeBuild`)은 [README.md](../README.md)의 "외부 프로젝트에서 참조하기" 절에서 확인한다.

## 5. 로컬 실행 및 API 동작 확인 (`example-app`)

`example-app`은 기본 포트 `8080`과 기본 프로필 `local`(`application.yml`의 `spring.profiles.default: local`)로 기동한다.

### 앱 실행

기본 프로필(`local`)과 포트 `8080`으로 실행한다.

```powershell
.\gradlew.bat :example-app:bootRun
```

포트 `8080`이 사용 중이면 `--args`로 포트를 바꾼다.

```powershell
.\gradlew.bat :example-app:bootRun --args="--server.port=18080"
```

`dev` 또는 `prod` 프로필로 실행하려면 `--spring.profiles.active` 인자를 전달한다.

```powershell
# dev 프로필로 실행 (Mock 활성화)
.\gradlew.bat :example-app:bootRun --args="--spring.profiles.active=dev"

# prod 프로필로 실행 (Mock 비활성화, 모든 요청이 SensorController로 라우팅)
.\gradlew.bat :example-app:bootRun --args="--spring.profiles.active=prod"
```

### API 요청 확인 (`curl`)

앱이 기동된 상태에서 다른 터미널을 열어 운영 경로와 Mock 경로의 분기를 확인한다.

- **운영 컨트롤러 요청 (`X-Mock-Data` 헤더 없음).**

  ```bash
  curl -s "http://localhost:8080/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02"
  ```

  `SensorController`가 응답하며 `"mock":false`와 `"source":"LIVE_STORE"`를 반환한다.

- **현재 값 Mock 요청 (`X-Mock-Data: true`).**

  ```bash
  curl -s -H "X-Mock-Data: true" "http://localhost:8080/api/v1/sensors/current?sensorIds=TEMP-01,PRESS-02&intervalMs=1000"
  ```

  `MockSensorController`가 응답하며 `"mock":true`와 각 센서의 `value`, `pattern`을 반환한다. 동일 `intervalMs` 구간 내에서 다시 호출하면 같은 `timestamp`와 `value`가 반환된다.

- **시계열 Mock 요청 (`X-Mock-Data: true`).**

  ```bash
  curl -s -H "X-Mock-Data: true" "http://localhost:8080/api/v1/sensors/timeseries?sensorIds=TEMP-01&from=2026-01-01T00:00:00Z&to=2026-01-01T00:01:00Z&intervalMs=10000"
  ```

  `from`과 `to`는 ISO-8601 UTC 문자열 또는 Epoch 밀리초 문자열을 받는다.

- **입력 오류 격리 확인 (`400 Bad Request`).**

  ```bash
  curl -i -H "X-Mock-Data: true" "http://localhost:8080/api/v1/sensors/current?sensorIds=TEMP-01&intervalMs=10"
  ```

  기본 최소 주기(`minIntervalMs: 100`)보다 작으므로 `MockSensorController`의 `@ExceptionHandler`가 `400 Bad Request`와 `MockErrorResponse`(`status`, `message`, `timestamp`)를 반환한다.

- **OpenAPI와 Swagger UI 확인.**

  `local` 또는 `dev` 프로필로 기동한 뒤 브라우저에서 `http://localhost:8080/swagger-ui.html`을 연다. OpenAPI JSON 원문은 `http://localhost:8080/v3/api-docs`에서 확인한다. `MockHeaderOperationCustomizer`가 Mock 엔드포인트 오퍼레이션에 선택적 `X-Mock-Data` 헤더 파라미터 명세를 주입한다. `prod` 프로필로 기동하면 `MockSensorController`와 `MockHeaderOperationCustomizer`가 등록되지 않는다.

## 6. 코드 탐색 가이드

기능 수정 위치를 찾을 때 아래 핵심 클래스부터 확인한다.

- **핵심 생성 엔진 (`mock-data-core`).**
  - `DeterministicMockGenerator`는 현재 값(`generateCurrentValues`)과 시계열(`generateTimeSeries`) 진입점이며 입력 검증, 시각 정렬, 포인트 상한 검사를 담당한다.
  - `DeterministicWaveCalculator`는 FNV-1a 64비트 해시, SplitMix64(`mix64`), 5개 `MockPatternType` 파형, 가우시안 노이즈를 계산한다.
  - `GeneratorConfig`와 `SensorPhysicsPreset`은 기본 주기와 한도, 소수점 자리수, 물리 파형 파라미터 레코드와 빌더를 제공한다.
- **Spring Boot Starter (`mock-data-spring-boot-starter`).**
  - `MockSensorAutoConfiguration`은 `@Profile({"local", "dev"})`, `@ConditionalOnWebApplication(SERVLET)`, `ees.mock.sensor.enabled` 조건으로 빈을 등록한다.
  - `MockSensorProperties`는 `ees.mock.sensor.*`와 `ees.mock.sensor.physics.*` 프로퍼티를 바인딩하고 경로 유효성을 검증한다.
  - `MockSensorController`는 `X-Mock-Data=true` 헤더 매핑, 타임스탬프 파싱, 컨트롤러 내부 `@ExceptionHandler`를 제공한다.
  - `MockSensorOpenApiConfiguration`과 `MockHeaderOperationCustomizer`는 Springdoc이 클래스패스에 있을 때만 OpenAPI 헤더 명세를 주입한다.
- **예시 앱 (`example-app`).**
  - `SensorController`는 `app-custom-config.sensor.*` 경로를 사용하는 모의 운영 컨트롤러다.
  - `AbstractProfileRoutingIntegrationTest`는 `local`, `dev`, `prod` 프로필 분기와 커스텀 URI 동작을 검증하는 통합 테스트 베이스다.

## 7. 개발 표준 및 협업 규칙

코드를 수정하거나 PR을 올리기 전에 아래 저장소 규칙을 따른다.

1. **코딩 표준 준수.** [CODING_STANDARDS.md](../CODING_STANDARDS.md)에 따라 `mock-data-core`는 순수 Java 21로 유지하며 Spring과 Lombok을 추가하지 않는다. 테스트는 내부 헬퍼를 mocking하지 않고 `DeterministicMockGenerator`, `MockMvc`, `WebApplicationContextRunner` 등 공개 경계로 작성한다.
2. **용어 및 ADR 일치.** 도메인 개념은 [GLOSSARY.md](../GLOSSARY.md)의 용어로 통일한다. 기존 ADR과 다른 결정을 내릴 때는 새 ADR을 추가해 근거를 남긴다.
3. **한글 응답 및 `agent-log.md`.** AI 에이전트와 협업할 때 대화와 기록은 한글로 작성한다. 최상위 코디네이터는 매 턴 마무리에 저장소 루트의 [agent-log.md](../agent-log.md)에 작업 요약을 누적 기록한다.
4. **Windows Worktree 경로 규칙.** Windows에서 `git worktree`를 만들 때는 `MAX_PATH` 오류를 피하기 위해 `C:\Users\imoil\repo\_wt\<branch>` 같은 짧은 루트 경로 아래에 배치한다.
5. **이슈 및 PR 확인.** 이슈와 스펙은 `imoil/mock-data-gen` GitHub Issues에서 관리한다([docs/agents/issue-tracker.md](./agents/issue-tracker.md)). PR은 `main` 브랜치 기준이며 `.github/workflows/ci.yml`의 `clean check publishToMavenLocal` 통과가 필수다.
