# 차근차근 (팀 미래)

초보·장롱면허 운전자를 위한 Pleos Connect(Android Automotive OS) 앱입니다.
이 저장소는 팀 공통 뼈대(v0)입니다. 각자 자기 `feature/`·`domain/` 폴더에서 작업합니다.

## 실행

- Android Studio(JDK 17 이상, 번들 JBR 21 사용 가능)로 열고 `app`을 실행합니다.
- 명령줄: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- 대상: Pleos Connect AVD(Android 14). 일반 에뮬레이터에도 adb로 설치는 됩니다.
- 왼쪽 메뉴 **디버그**에서 기어·방향지시등·경고 등을 바꾸거나 `basic_drive` 시나리오를 재생합니다.

## 구조

```text
core/model     공용 데이터 타입 (Signal, Gear, SituationEvent ...)        ← 리뷰 필수
core/port      바깥 세계를 보는 인터페이스 7개                             ← 리뷰 필수
platform/      실제 구현. Pleos SDK(ai.pleos.*)·AAOS(android.car.*)는 여기서만 import
demo/          가짜 구현(FakeVehicleBus), 시나리오 재생, 디버그 패널
runtime/       AppGraph(조립), DriveRuntime(주행 상태·상황 이벤트), MainActivity ← 리뷰 필수
domain/        판단 로직. 순수 Kotlin, JVM 테스트
data/          Room DB, 설정
feature/       화면 + ViewModel
ui/            테마(PRD 5색), DrivingGate 등 공용 Composable
```

| 포트 | 쓰는 기능 | 담당 |
|---|---|---|
| `DrivingSignals` (기어, 속도, 브레이크, 방향지시등, 조향각, 주행 규제) | D1, 주행 판단 | 후(어댑터) |
| `WarningSource` | C1 | 재균 |
| `SeatController` | A1 | 후 |
| `VehicleSpecSource`, `LocationSource` | B1 | 민서 |
| `SpeechOutput`, `SpeechInput` | C1, B1, E2 | E2 담당 |

| 폴더 | 담당 |
|---|---|
| `feature/onboarding`, `feature/seat`, `domain/drive`, `platform/vehicle` | 후 |
| `feature/parking`, `platform/nav` | 민서 |
| `feature/warning`, `domain/guidance` | 재균 |
| `feature/report`, `domain/trip`, `data/` | 수현 |
| `feature/voice`, `platform/speech` | E2 담당 |

## 규칙

1. **`feature/`와 `domain/`은 Pleos·AAOS 타입을 import하지 않습니다.** 차량 값은 `core/port`로만 받습니다.
2. **`core/`, `runtime/`, `gradle/`, `*.gradle.kts` 변경은 PR에서 후의 리뷰를 받습니다.** 라이브러리가 더 필요하면 직접 추가하지 말고 요청해 주세요.
3. `main`에 직접 push하지 않습니다. 브랜치(`이름/기능`, 예: `minseo/b1-arrival`)에서 작업하고 PR로 합칩니다.
4. 값을 못 받으면 `Signal.Unavailable`로 둡니다. 0이나 false로 바꾸지 않습니다.
5. 상세 화면은 `DrivingGate`로 감쌉니다. 주행 중이면 자동으로 짧은 안내로 바뀝니다.

## 새 기능 만드는 순서

`feature/home`(방향지시등 예시)을 따라 합니다.

1. `XxxViewModel(포트들)`을 만들고 포트 Flow를 `combine`해 `XxxUi` 상태를 만듭니다.
2. `XxxScreen(graph)`에서 `viewModel { XxxViewModel(graph.포트) }`로 만들고 상태를 그립니다.
3. 판단 로직은 `domain/`에 순수 함수로 두고 `src/test`에 테스트를 씁니다(`FakeVehicleBus` 사용).
4. 상황이 생기면 `graph.driveRuntime.report(SituationEvent("warning.tire_pressure", Severity.CAUTION))`처럼 알립니다.
5. 시연 흐름은 `app/src/main/assets/scenarios/*.json`에 추가합니다. 테스트가 파싱을 자동 검사합니다.

## 프로토타입 옮기기

따로 만든 프로토타입은 통째로 합치지 않고 나눠서 옮깁니다.

- SDK 호출 코드 → `platform/` 어댑터(포트 구현). 새 포트가 필요하면 먼저 이야기해 주세요.
- 판단·계산 로직 → `domain/`
- 화면 → `feature/` (Compose)

## 버전

`gradle/libs.versions.toml`에서만 관리합니다. Pleos Vehicle SDK Sample App 기준(AGP 8.8.2, Kotlin 2.0, compileSdk/targetSdk 35)에 가깝게 맞췄고, 개발 PC에 platform 35가 없어 AGP 8.13.0, compileSdk 36을 씁니다. targetSdk는 35입니다.

## 다음 단계 (v1, 11/10)

- `platform/vehicle`: 기어, 브레이크, 방향지시등, 조향각, 경고 실제 어댑터와 Pleos 권한 선언
- `platform/car`: CarUxRestrictions, PERF_VEHICLE_SPEED
- `AppGraph`: 포트별 실제·가짜 전환
