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
# 전체 테스트 및 검증
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

## Starter 설정 (`app-custom-config.mock.sensor.*`)

`local` 또는 `dev` 프로필이 활성화된 경우에만 Mock 빈이 등록되며, `prod` 등 그 외 프로필에서는 등록되지 않습니다. 엔드포인트 URI는 프로젝트의 URI 규칙에 맞게 오버라이드할 수 있습니다.

```yaml
app-custom-config:
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

## 관련 문서

- [프로젝트사양서.md](./프로젝트사양서.md)
- [GLOSSARY.md](./GLOSSARY.md)
- [docs/adr/0001-multi-module-library-boundary.md](./docs/adr/0001-multi-module-library-boundary.md)
- [docs/adr/0002-deterministic-noise-and-starter-customization.md](./docs/adr/0002-deterministic-noise-and-starter-customization.md)
