# 0001. 3-모듈 라이브러리 경계 분리 (Core / Spring Boot Starter / Example App)

Status: accepted

Mock data 생성 라이브러리를 외부 프로젝트가 다양한 형태(순수 Java 또는 Spring Boot 웹 애플리케이션)로 참조할 수 있도록 Gradle 멀티 프로젝트를 `mock-data-core`, `mock-data-spring-boot-starter`, `example-app` 3개 모듈로 분리한다.

## Considered Options

- **단일 Spring 통합 라이브러리 + 예시 앱 (2모듈)**: 구현은 단순하지만 비-Spring 프로젝트(배치, gRPC, 순수 SDK)가 Spring Web 의존성 없이 생성 엔진만 재사용할 수 없다.
- **순수 Java 코어 + 예시 앱 (2모듈)**: 코어는 재사용 가능하지만 외부 Spring Boot 서비스마다 `MockSensorController`, `@Profile` 격리, `X-Mock-Data` 헤더 라우팅, 유효성 검증을 매번 중복 구현해야 한다.
- **순수 Core + Spring Boot Starter + Example App (3모듈, 채택)**: `mock-data-core`는 Spring 의존성 없는 순수 Java 라이브러리로 유지하고, `mock-data-spring-boot-starter`가 자동설정과 웹 계층을 제공하며, `example-app`은 내부 `project(...)` 참조로 이를 소비한다.

## Consequences

- `mock-data-core`와 `mock-data-spring-boot-starter`는 각각 `java-library` + `maven-publish`로 독립 배포된다.
- Spring MVC 라우팅·예외 격리·프로필 제어 변경 시 `mock-data-core`의 결정론적 수식 엔진에는 영향을 주지 않는다.
