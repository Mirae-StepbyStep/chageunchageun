# 차근차근 (팀 미래)

초보·장롱면허 운전자를 위한 Pleos Connect(Android Automotive OS) 앱입니다.
이 저장소는 팀 공통 뼈대(v0)입니다. 각자 자기 `feature/`·`domain/` 폴더에서 작업합니다.

## 실행

- Android Studio(JDK 17 이상, 번들 JBR 21 사용 가능)로 열고 `app`을 실행합니다.
- 명령줄: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- 대상: Pleos Connect AVD(Android 14). 일반 에뮬레이터에도 adb로 설치는 됩니다.
- 왼쪽 메뉴 **디버그**에서 기어·등화·와이퍼·경고 등을 바꾸거나 `basic_drive` 시나리오를 재생합니다.

## 구조

```text
core/model     공용 데이터 타입 (Signal, Gear, SituationEvent ...)        ← 리뷰 필수
core/port      바깥 세계를 보는 인터페이스 8개 (포트당 파일 하나)                            ← 리뷰 필수
platform/      실제 구현(vehicle, car, nav, speech). Pleos SDK(ai.pleos.*)·AAOS(android.car.*)는 여기서만 import
demo/          가짜 구현(FakeVehicleBus), 시나리오 재생, 디버그 패널
runtime/       AppGraph(조립), PortModes(실제·가짜 전환), DriveRuntime(주행 상태·상황 이벤트), MainActivity ← 리뷰 필수
domain/        판단 로직. 순수 Kotlin, JVM 테스트
data/          Room DB, 설정
feature/       화면 + ViewModel
ui/            테마(PRD 5색), DrivingGate 등 공용 Composable
```

| 포트 | 쓰는 기능 | 담당 |
|---|---|---|
| `DriveStateSource` (기어, 주행 규제) | 주행 판단(`DriveRuntime`) | 후 |
| `ControlsSource` (등화, 앞 와이퍼, 브레이크 페달) | A5 | 후 |
| `SteeringSource` (조향각) | B1 | 민서 |
| `WarningSource` | C1 | 재균 |
| `VehicleSpecSource`, `LocationSource` | B1 | 민서 |
| `SpeechOutput`, `SpeechInput` | C1, B1, E2 | E2 담당 |

| 폴더 | 담당 |
|---|---|
| `feature/onboarding`, `feature/mirror`(A3), `feature/practice`(A5), `domain/drive`, `platform/vehicle`, `platform/car` | 후 |
| `feature/parking`, `platform/nav` | 민서 |
| `feature/toll`(B2) | 후(주행 전 미리 보기), 민서(주행 중 안내) |
| 비즈니스, 시연 시나리오(`assets/scenarios`) 등 | 수현 |
| `feature/warning`, `domain/guidance` | 재균 |
| `feature/voice`, `platform/speech` | E2 담당 |

## 규칙

1. **`feature/`와 `domain/`은 Pleos·AAOS 타입을 import하지 않습니다.** 차량 값은 `core/port`로만 받습니다.
2. **`core/`, `runtime/`, `gradle/`, `*.gradle.kts` 변경은 PR에서 후의 리뷰를 받습니다.** 라이브러리가 더 필요하면 직접 추가하지 말고 요청해 주세요.
3. `main`에 직접 push하지 않습니다. 브랜치(`이름/기능`, 예: `minseo/b1-arrival`)에서 작업하고 PR로 합칩니다.
4. 값을 못 받으면 `Signal.Unavailable`로 둡니다. 0이나 false로 바꾸지 않습니다.
5. 상세 화면은 `DrivingGate`로 감쌉니다. 주행 중이면 자동으로 짧은 안내로 바뀝니다.

## 새 기능 만드는 순서

`feature/home`(상향등 예시)을 따라 합니다.

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

## 실제 어댑터 붙이기

포트마다 실제 구현(`platform/`)과 가짜 구현(`demo/`)이 둘 다 `AppGraph`에 연결돼 있습니다. 어댑터가 구현된 포트(주행 상태, 등화·와이퍼, 조향각, 경고, 음성 말하기)는 실제로 연결된 채 시작해서, 에뮬레이터의 Virtual Console을 조작하면 바로 앱에 반영됩니다. 아직 빈 어댑터의 포트는 가짜로 시작합니다. **디버그 → 포트 연결**에서 포트별로 바꿀 수 있고, 어댑터를 채우면 `runtime/PortModes.kt`의 `CONNECTED_PORTS`에 추가합니다. 가짜 차량 시나리오를 재생하려면 먼저 **모두 가짜**를 누릅니다. 실제 값을 못 받아도 가짜로 자동 대체하지 않습니다.

| 포트 | 어댑터 파일 | 담당 |
|---|---|---|
| `DriveStateSource` | `platform/car/CarDriveStateSource.kt` | 후 |
| `ControlsSource` | `platform/vehicle/PleosControlsSource.kt` | 후 |
| `SteeringSource` | `platform/vehicle/PleosSteeringSource.kt` | 민서 |
| `WarningSource` | `platform/vehicle/PleosWarningSource.kt` | 재균 |
| `VehicleSpecSource` | `platform/car/CarVehicleSpecSource.kt` | 민서 |
| `LocationSource` | `platform/nav/PleosLocationSource.kt` | 민서 |
| `SpeechOutput`, `SpeechInput` | `platform/speech/PleosSpeechOutput.kt`, `PleosSpeechInput.kt` | E2 담당 |

1. 자기 어댑터 파일의 본문만 채웁니다. 본보기는 `CarDriveStateSource`이고, 나머지는 `Unavailable("adapter not implemented")`를 내보냅니다.
   "현재 값 조회 + 구독"은 `platform/SignalFlow.kt`의 `signalFlow`에 조회·등록·해제만 넘기면 됩니다(사용 예시는 그 파일 주석).
2. 생성자와 `AppGraph`는 바꾸지 않습니다. 그래야 브랜치를 합칠 때 충돌이 없습니다.
3. 실패(`onFailed`, 미지원, 권한 없음)는 사유를 담은 `Signal.Unavailable`로 내보냅니다.
4. 권한은 `AndroidManifest.xml`에 어댑터별 묶음으로 미리 선언돼 있습니다. 더 필요하면 자기 묶음 안에만 추가합니다.
5. 값이 들어오는지는 **신호 점검** 메뉴에서 봅니다. 포트별 최신 값, 출처, 못 받은 사유가 한 줄씩 나옵니다.

## C1 차량 경고 안내

담당: 재균. 자세한 기록은 `docs/c1/`에 있습니다(시험 결과 `01-probe-results.md`, 시연 순서 `02-demo-script.md`).

**하는 일**

- 차량 경고등이 켜지면 의미·심각도·조치를 한국어 음성으로 먼저 알립니다. 어떤 화면이 열려 있어도 말합니다.
- 주행 중에는 심각도 색 카드 한 장만 보여 주고(스크롤·애니메이션·터치 없음), 정차하면 **C1 경고** 화면으로 자동 전환해 조치 단계와 "다시 듣기"를 보여 줍니다.
- 심각도는 위험(빨강)·주의(주황)·정보(파랑)이며 색과 함께 기호·글자로 표시합니다. 정보는 음성과 주행 중 카드가 없습니다.
- 어떤 경고에서도 급정지를 지시하지 않습니다. 공기압 수치는 표시하지 않습니다.

**신호 경로**

| 값 | 경로 | 구분 |
|---|---|---|
| 경고등 | Pleos Vehicle SDK 2.3.2 `registerTelltales` → `platform/vehicle/PleosWarningSource.kt` | 실제 신호 |
| 기어, 주행 규제 | AAOS Car API → `platform/car/CarDriveStateSource.kt` | 실제 신호 |
| 음성 | Pleos TextToSpeech SDK 3.3.1, ON_DEVICE 모드, `Locale.KOREA` → `platform/speech/PleosSpeechOutput.kt` | 실제 출력 |
| 브레이크액, 워셔액, 엔진, 엔진오일, 안전벨트 경고 | 디버그 패널의 가짜 차량 | 시연용 신호 |

콘솔로 켤 수 있는 경고는 타이어 공기압, 주차 브레이크, 조향 보조, ABS, ESC, ESC 꺼짐, 전기차 시스템, 출력 제한, 12V 배터리, 에어백입니다. 브레이크액·워셔액은 콘솔에 경고등이 없어 디버그 패널로만 보여 주고, 안전벨트·엔진·엔진오일 압력 경고등은 일부러 연결하지 않았습니다.

**실행 환경**

- Pleos Connect 에뮬레이터 v2.1.0 (Android 14, `pleos_car_emulator` arm64-v8a)
- 화면: 2560 × 1440, 160 dpi, 가로 (AVD 설정과 `adb shell wm size`·`wm density`로 확인)
- 실제 차량은 쓰지 않습니다. 차량 값은 에뮬레이터의 Pleos Connect Virtual Console이 만듭니다.

**권한** (모두 `AndroidManifest.xml`에 선언, 에뮬레이터에서 부여 확인)

| 권한 | 쓰는 곳 |
|---|---|
| `pleos.car.permission.PRIVATE_CAR_SAFETY` | `registerTelltales` |
| `pleos.car.permission.CAR_SAFETY` | `getTelltalesInfo` |
| `pleos.car.permission.TTS_SERVICE` | Pleos TextToSpeech |
| `android.car.permission.CAR_POWERTRAIN` | 기어 (후의 어댑터) |

TextToSpeech SDK가 자기 매니페스트로 `android.permission.INTERNET`을 함께 넣습니다. ON_DEVICE 모드는 네트워크와 앱 등록 없이 동작합니다.

**공기압 경고 시연 (Virtual Console, 약 30초)**

1. `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088`을 열어 둡니다.
2. 앱의 **디버그** 메뉴에서 **C1 시연 준비**를 누릅니다(경고·주행 상태·음성 말하기를 실제로 전환). 이 세 포트는 이제 처음부터 실제로 연결돼 있어 누르지 않아도 됩니다.
3. `cd tools/console && python3 console.py run scenarios/c1_tpms.json`

콘솔 없이 보려면 디버그 메뉴에서 **모두 가짜**를 누른 뒤 `c1_tpms_demo` 시나리오를 재생합니다(가짜 차량, 시연용 신호).

**알아 둘 점**

- 에뮬레이터에는 Android 표준 TTS 엔진이 없어 Pleos TextToSpeech SDK를 씁니다.
- 오디오 포커스는 문장마다 요청하고 끝나면 돌려줍니다. SDK에 음량 조절이 없어 `LOSS_TRANSIENT_CAN_DUCK`에서는 음량을 낮추지 못하고 그대로 말합니다.
- `tools/console`은 공개 문서에 없는 Virtual Console 내부 API를 씁니다. 에뮬레이터 v2.1.0에서만 확인했고, 시연·시험 도구로만 씁니다.
- 조치 문구는 초안입니다. 차량 공식 매뉴얼과 대조가 필요한 항목은 `docs/c1/03-proposal-notes.md`에 있습니다.

## 다음 단계

- 나머지 어댑터 7개 구현(각 담당)
- "미확인"으로 적은 권한(FusedLocation, NaviHelper, TTS, STT) 이름 확인
- 주행 규제(`CarUxRestrictions`) 값이 콘솔 조작으로 바뀌는지 시험
