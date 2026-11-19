# 0003. Starter 라이브러리(`ees.mock.sensor.*`)와 컨슈머 앱(`app-custom-config.*`) 프로퍼티 프리픽스 분리

Status: accepted

`ADR-0002`에서 Starter 설정 프리픽스를 `app-custom-config.mock.sensor.*`로 지정했으나, `app-custom-config.*`는 본래 컨슈머 애플리케이션이 외부 의존성 설정과 분리해 관리하는 앱 고유 네임스페이스였다. 그 결과 `example-app`의 운영 컨트롤러와 Starter 설정이 동일 네임스페이스에 섞이는 역방향 결합이 발생했다. 따라서 `mock-data-spring-boot-starter`의 모든 프로퍼티는 라이브러리 고유 프리픽스 `ees.mock.sensor.*`로 분리하고, `example-app`은 컨슈머 앱 고유 설정으로 `app-custom-config.sensor.*`를 사용하도록 분리한다.

## Consequences

- `ADR-0002`의 Starter 프로퍼티 네임스페이스(`app-custom-config.mock.sensor.*`) 결정은 본 ADR로 대체된다(`timestampMs` 노이즈 입력과 URI 분리 커스터마이징 결정은 유지).
- 외부 컨슈머 앱은 자신의 `app-custom-config.*` 설정을 라이브러리 의존성과 독립적으로 유지한 채 `ees.mock.sensor.*`로 Starter 동작·URI·물리 프리셋을 구성한다.
