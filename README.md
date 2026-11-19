# mock-data-gen

센서 기능 테스트를 위해 상태 저장 없이 결정론적(Deterministic) Mock 데이터를 생성하는 Gradle 멀티 프로젝트입니다.

## 모듈 구성

| 모듈 | 설명 | Maven 좌표 |
| :--- | :--- | :--- |
| `mock-data-core` | Spring 비의존 순수 Java 결정론적 해시·파형·격자 정렬 엔진 | `com.samsung.ees.infra.mock:mock-data-core:0.1.0-SNAPSHOT` |
| `mock-data-spring-boot-starter` | `@Profile({"local", "dev"})` 격리, `MockSensorController`, Springdoc OpenAPI 지원 Starter | `com.samsung.ees.infra.mock:mock-data-spring-boot-starter:0.1.0-SNAPSHOT` |
| `example-app` | `project(':mock-data-spring-boot-starter')`를 참조하는 응용 예시 Spring Boot 앱 | 배포 제외 |

## 빌드 및 로컬 Maven 배포

```powershell
# 전체 테스트, publication 메타데이터(JAR/sources/javadoc/POM) 검증
.\gradlew.bat clean check

# 외부 프로젝트 참조를 위한 로컬 Maven 저장소(~/.m2) 배포
.\gradlew.bat publishToMavenLocal
```

## 외부 프로젝트에서 참조하기

### 1. 로컬 Maven 저장소(`mavenLocal`) 사용

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Spring Boot 웹 프로젝트
    implementation 'com.samsung.ees.infra.mock:mock-data-spring-boot-starter:0.1.0-SNAPSHOT'

    // 또는 순수 Java 엔진만 사용 시
    // implementation 'com.samsung.ees.infra.mock:mock-data-core:0.1.0-SNAPSHOT'
}
```

### 2. Gradle Composite Build(`includeBuild`) 사용

외부 프로젝트의 `settings.gradle`에 아래를 추가하면 소스 변경 사항을 즉시 참조할 수 있습니다.

```groovy
includeBuild '../mock-data-gen'
```

## Starter 설정 (`ees.mock.sensor.*`)

`local` 또는 `dev` 프로필이 활성화된 서블릿 웹 환경에서 `ees.mock.sensor.enabled`가 `true`(기본값, 미설정 포함)일 때만 Mock 빈(`GeneratorConfig`, `DeterministicMockGenerator`, `MockSensorController`)이 등록됩니다. `prod` 등 그 외 프로필에서는 등록되지 않습니다. 엔드포인트 URI는 프로젝트의 URI 규칙에 맞게 오버라이드할 수 있습니다.

```yaml
ees:
  mock:
    sensor:
      enabled: true
      base-path: /api/v1/sensors
      current-path: /current
      timeseries-path: /timeseries
      default-current-interval-ms: 1000
      default-series-interval-ms: 10000
      min-interval-ms: 100
      max-points-per-sensor: 10000
      decimal-scale: 2
```

### 요청 라우팅 및 예외 격리

- `GET {base-path}{current-path}` (`headers = "X-Mock-Data=true"`): 현재 값 조회 (`sensorIds`, 선택적 `intervalMs`). `CurrentMockResponse`(`timestamp`, `epochMillis`, `mock: true`, `sensors`)를 반환합니다.
- `GET {base-path}{timeseries-path}` (`headers = "X-Mock-Data=true"`): 시계열 조회 (`sensorIds`, `from`, `to`, 선택적 `intervalMs`). `from`/`to`는 ISO-8601 UTC 문자열 또는 Epoch ms 숫자 문자열을 지원하며 `TimeSeriesMockResponse`(`from`, `to`, `intervalMs`, `mock: true`, `series`)를 반환합니다.
- `X-Mock-Data` 헤더가 없거나 `true`가 아닌 요청은 기존 운영 컨트롤러로 그대로 라우팅됩니다.
- 잘못된 요청 파라미터(`InvalidMockRequestException`, 누락·타입 불일치)는 `@RestControllerAdvice` 없이 `MockSensorController` 내부 `@ExceptionHandler`로 격리되어 `400 Bad Request` (`MockErrorResponse`: `status`, `message`, `timestamp`)를 반환합니다.

### 조건부 Springdoc OpenAPI 지원 (`MockHeaderOperationCustomizer`)

`mock-data-spring-boot-starter`는 `org.springdoc:springdoc-openapi-starter-webmvc-api`를 `compileOnly`로만 참조하므로 소비자 POM에 Springdoc 의존성이 전파되지 않습니다. 소비자 애플리케이션 클래스패스에 Springdoc(`OperationCustomizer`)이 존재하고 `local`/`dev` 프로필에서 Mock 엔드포인트가 활성화된 경우에만 `MockSensorOpenApiConfiguration`이 `MockHeaderOperationCustomizer` 빈을 등록하여 `MockSensorController` 오퍼레이션에만 선택적 `X-Mock-Data` 헤더 파라미터 명세를 주입합니다.

## 프로그래밍 방식 커스터마이징 (`GeneratorConfig` / `SensorPhysicsPreset`)

순수 Java 환경이나 Spring 빈 오버라이드(`@ConditionalOnMissingBean`)로 물리 파형 범위(`SensorPhysicsPreset`)와 생성 한도(`GeneratorConfig`)를 직접 지정할 수 있습니다.

```java
SensorPhysicsPreset preset = SensorPhysicsPreset.builder()
        .baseline(20.0, 80.0)
        .amplitude(2.0, 18.0)
        .period(300.0, 3300)
        .noiseRatio(0.05)
        .stepDutyCycle(0.65)
        .drift(4.0, 1.5)
        .anomaly(60.0, 5, 2.5)
        .build();

GeneratorConfig config = GeneratorConfig.builder()
        .defaultCurrentIntervalMs(1000L)
        .defaultSeriesIntervalMs(10000L)
        .minIntervalMs(100L)
        .maxPointsPerSensor(10000)
        .decimalScale(2)
        .physicsPreset(preset)
        .build();

// 순수 Java 사용 시
DeterministicMockGenerator generator = new DeterministicMockGenerator(config, Clock.systemUTC());
```

Spring Boot 앱에서는 `@Bean SensorPhysicsPreset`을 등록하면 YAML의 `ees.mock.sensor.*` 간격·한도·소수점 설정을 유지한 채 물리 프리셋만 교체할 수 있으며, `application.yml`의 `ees.mock.sensor.physics.*` 프로퍼티로도 동일한 물리 파라미터를 조정할 수 있습니다. 전체 설정을 직접 대체해야 할 때는 `@Bean GeneratorConfig` 등록도 계속 지원됩니다.

## 관련 문서

- [프로젝트사양서.md](./프로젝트사양서.md)
- [GLOSSARY.md](./GLOSSARY.md)
- [docs/adr/0001-multi-module-library-boundary.md](./docs/adr/0001-multi-module-library-boundary.md)
- [docs/adr/0002-deterministic-noise-and-starter-customization.md](./docs/adr/0002-deterministic-noise-and-starter-customization.md)
- [docs/adr/0003-starter-property-prefix-separation.md](./docs/adr/0003-starter-property-prefix-separation.md)
