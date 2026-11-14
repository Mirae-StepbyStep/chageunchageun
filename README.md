# 차근차근 (팀 미래)

초보·장롱면허 운전자에게 상황이 오기 전에 먼저 알려 주는 Pleos Connect(Android Automotive OS) 운전 매뉴얼 앱입니다.

| 항목 | 내용 |
|---|---|
| 앱 이름 | 차근차근 (`com.mirae.chageunchageun`, 버전 0.1.0) |
| 팀 | 미래 |
| APK | [`apk/미래_차근차근_release.apk`](apk/미래_차근차근_release.apk) (서명된 release 빌드) |
| 확인한 환경 | Pleos Connect 에뮬레이터 v2.1.0, 2560 × 1440 가로 |

## 실행 방법

> **시연 모드: 홈 화면 왼쪽 위 차근차근 로고를 길게 누르세요.**
> 홈 아래에 시연 도구(**신호 점검**, **디버그**)와 "시연 모드 · 가짜 차량 신호는 실제 차량 상태가 아닙니다" 문구가 나타납니다. 앱을 다시 켜면 꺼집니다.

1. Pleos Connect 에뮬레이터 v2.1.0을 `-dns-server`를 붙여 시작합니다.

   ```
   ~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -dns-server 8.8.8.8,1.1.1.1
   ```

2. APK를 설치합니다. 서명이 다른 빌드(debug)가 깔려 있으면 먼저 지웁니다.

   ```
   adb -s emulator-5554 uninstall com.mirae.chageunchageun
   adb -s emulator-5554 install apk/미래_차근차근_release.apk
   ```

3. 에뮬레이터의 기어가 **P**인지 확인하고, 앱 목록에서 **차근차근**(도로 아이콘)을 누릅니다. 처음 켜면 **첫 실행** 화면이 열립니다.

4. 차량 신호를 바꾸려면 Virtual Console을 엽니다.

   ```
   adb -s emulator-5554 forward tcp:8088 tcp:8088
   ```

   Virtual Console 페이지(http://127.0.0.1:8088)를 브라우저에서 열어 두고 이 탭을 앞에 둔 상태로 진행하세요. 탭이 뒤로 가면 신호 변경이 거부됩니다(503 emulator_inactive).

5. 같은 순서를 스크립트로 재생하려면 `tools/console`을 씁니다(Python 3 표준 라이브러리만 사용, 자세한 설명은 `tools/console/README.md`).

   ```
   cd tools/console
   python3 console.py state                       # 기어, 속도, 켜진 경고등, 콘솔 준비 상태
   python3 console.py gear P                      # P, R, N, D
   python3 console.py telltale TPMS On            # On, Off, Blink
   python3 console.py run scenarios/c1_tpms.json  # 공기압 경고 시연, 약 30초
   ```

## 기능별 확인 순서

release APK와 Virtual Console만으로 확인하는 순서입니다. **스마트 주차 코치는 맨 마지막에 하세요.** 주차 시연에서 기어와 조향각을 가짜 차량으로 바꾸면 앱을 다시 켜기 전까지(또는 디버그 → **처음 상태로**) Virtual Console의 기어가 앱에 반영되지 않습니다.

| 기능 | 순서 | 보이는 것 |
|---|---|---|
| 첫 실행 | 새로 설치 → 기어 P → 앱 실행 → **동의해요** → **앱 사용 중에만 허용** → **다음** → **미러부터 맞춰볼까요?** | 위치 동의(1/3), 안내 깊이(2/3), 준비 완료(3/3) 뒤 미러 맞추기로 이동 |
| 미러 맞추기 | 홈 → **미러 맞추기** → **다음**, **확인했습니다**를 차례로 누름 | 자세, 룸미러, 사이드미러를 그림으로 안내하는 5단계. 끝나면 홈 |
| 경고등 안내 | `python3 console.py run scenarios/c1_tpms.json` | 주행 중: 주황 카드 한 장과 음성 "주의 경고예요. 타이어 공기압이 낮을 수 있어요…". 정차(P)하면 **경고등 안내** 화면이 열리고 조치 단계가 보임 |
| 조작 연습 | 홈 → **조작 연습** → **전조등** 또는 **상향등** → **연습 시작** → Virtual Console에서 전조등·상향등을 끄고 켬 | "지금 차 상태"(출처: Pleos SDK)가 바뀌고 단계가 넘어가 "연습 완료!"까지 진행. 전조등과 상향등을 Virtual Console로 확인했습니다. 와이퍼·브레이크·핸들 연습도 지원하지만 release 빌드에서 따로 확인하지는 않았습니다 |
| 스마트 주차 코치 | 아래 순서 | 가상 후진, 핸들 안내 음성, 도착, 주차 완료 |

**스마트 주차 코치 (가짜 차량, 시연용 신호)**

1. 홈에서 로고를 길게 눌러 **시연 모드**를 켭니다.
2. **스마트 주차 코치** → 오른쪽 위 **개발자 모드**.
3. **가짜 차량으로 전환**을 누릅니다. 이 화면이 읽는 기어와 조향각만 가짜 차량으로 바뀌고 음성은 그대로 실제로 나옵니다. 실제 기어를 R로 바꾸면 차량 후방 카메라가 화면을 가리므로 시연은 가짜 기어로 합니다.
4. **가상 도착** → **R**.
5. **더보기** → **자동 조향(안내대로)**. 켜지 않으면 핸들이 0°에 머물러 차가 똑바로만 후진합니다. 직접 해 보려면 핸들 게이지를 끌어 목표에 맞춥니다.
6. **가상 후진 시작**. 약 1분 30초 뒤 "주차 위치에 도착했습니다…"가 나오면 **P**를 눌러 **주차 완료**를 봅니다.

화면의 경로와 카메라 그림은 휠베이스·조향비 가정값으로 그린 시뮬레이션이며 실제 카메라 영상이 아닙니다. 화면에도 "개발자 테스트 · 가상 데이터", "시뮬레이션 안내"로 표시됩니다.

**첫 실행을 다시 보려면** 시연 모드를 켠 뒤 홈 → **내 설정** → **처음 설정 다시 하기**를 누르거나, 앱을 지우고 다시 설치합니다(위치 권한 창까지 처음처럼 나옴).

## 테스트 환경과 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0 (AVD `Pleos_Connect_v2.1.0`, 태그 `android-automotive`) |
| 시스템 이미지 | Android 14 (API 34), `pleos_car_emulator`, arm64-v8a |
| 화면 | 2560 × 1440, 160 dpi, 가로 (AVD `config.ini`의 `hw.lcd.*`, `adb shell wm size`·`wm density`로 확인). 앱은 화면 오른쪽 앱 창에 표시됩니다 |
| 호스트 | macOS (Apple Silicon), Android SDK platform-tools의 adb, Python 3 |
| 앱 | minSdk 28, targetSdk 35, Pleos Vehicle SDK 2.3.2, Pleos TextToSpeech SDK 3.3.1 |

- 실제 차량은 쓰지 않았습니다. 차량 값은 에뮬레이터의 Virtual Console이 만듭니다.
- 앱을 켤 때 기어가 P여야 첫 실행 화면이 자동으로 열립니다. D로 켜면 홈의 "주행 중" 화면에 머뭅니다(정상 동작).
- 위치 권한은 첫 실행에서 묻습니다. 거부해도 앱은 동작합니다.
- `tools/console`은 공개 문서에 없는 Virtual Console 내부 API를 씁니다. 에뮬레이터 v2.1.0에서만 확인했습니다.

## 실제 신호와 시연용 신호

| 값 | 출처 | 구분 |
|---|---|---|
| 기어, 주행 규제 | AAOS Car API (Virtual Console의 기어) | 실제 신호 |
| 경고등 | Pleos Vehicle SDK `registerTelltales` (Virtual Console의 telltale) | 실제 신호 |
| 전조등·상향등·미등, 앞 와이퍼, 브레이크 페달 | Pleos Vehicle SDK | 실제 신호 |
| 조향각 | Pleos Vehicle SDK | 실제 신호 |
| 음성 안내 | Pleos TextToSpeech SDK (ON_DEVICE, 한국어) | 실제 출력 |
| 목적지 도착, 위치 | 가짜 차량 (`FakeVehicleBus`) | 시연용 신호 |
| 차량 크기 | 가짜 차량 | 시연용 신호 |
| 음성 듣기 | 가짜 차량 | 시연용 신호 |
| 주차 코치의 차 움직임·속도·경로·카메라 그림 | 앱 안의 시뮬레이션 | 시연용 신호 |
| 주차 시연 중 기어·조향각 | **가짜 차량으로 전환**을 누른 뒤 가짜 차량 | 시연용 신호 |

홈의 "지금 내 차"와 **신호 점검** 화면은 값마다 출처(AAOS, Pleos SDK, 시연 신호)를 함께 보여 줍니다. 실제 값을 못 받아도 가짜 값으로 자동 대체하지 않습니다.

## 소스에서 빌드

- JDK 17 이상(Android Studio 번들 JBR 가능), Android SDK platform 36.
- debug 빌드와 테스트: `./gradlew assembleDebug testDebugUnitTest`
- release 빌드: `./gradlew assembleRelease`. 서명 값(`CHAGEUN_RELEASE_STORE_FILE`, `CHAGEUN_RELEASE_STORE_PASSWORD`, `CHAGEUN_RELEASE_KEY_ALIAS`, `CHAGEUN_RELEASE_KEY_PASSWORD`)을 `~/.gradle/gradle.properties`나 환경 변수에서 읽습니다. 없으면 서명 없이 빌드됩니다. 키스토어와 비밀번호는 저장소에 없습니다.
- debug 빌드는 시연 모드가 켜진 채 시작합니다.

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

포트마다 실제 구현(`platform/`)과 가짜 구현(`demo/`)이 둘 다 `AppGraph`에 연결돼 있습니다. 어댑터가 구현된 포트(주행 상태, 등화·와이퍼, 조향각, 경고, 음성 말하기)는 실제로 연결된 채 시작해서, 에뮬레이터의 Virtual Console을 조작하면 바로 앱에 반영됩니다. 아직 빈 어댑터의 포트는 가짜로 시작합니다. 시연 모드의 **디버그 → 포트 연결**에서 포트별로 바꿀 수 있고, 어댑터를 채우면 `runtime/PortModes.kt`의 `CONNECTED_PORTS`에 추가합니다. 가짜 차량 시나리오를 재생하려면 먼저 **모두 가짜**를 누릅니다. 실제 값을 못 받아도 가짜로 자동 대체하지 않습니다.

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
5. 값이 들어오는지는 시연 모드의 **신호 점검**에서 봅니다. 포트별 최신 값, 출처, 못 받은 사유가 한 줄씩 나옵니다.

## C1 차량 경고 안내

담당: 재균. 자세한 기록은 `docs/c1/`에 있습니다(시험 결과 `01-probe-results.md`, 시연 순서 `02-demo-script.md`).

**하는 일**

- 차량 경고등이 켜지면 의미·심각도·조치를 한국어 음성으로 먼저 알립니다. 어떤 화면이 열려 있어도 말합니다.
- 주행 중에는 심각도 색 카드 한 장만 보여 주고(스크롤·애니메이션·터치 없음), 정차하면 **경고등 안내** 화면으로 자동 전환해 조치 단계와 "다시 듣기"를 보여 줍니다.
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
2. `cd tools/console && python3 console.py run scenarios/c1_tpms.json`

콘솔 없이 보려면 시연 모드의 **디버그**에서 **모두 가짜**를 누른 뒤 `c1_tpms_demo` 시나리오를 재생합니다(가짜 차량, 시연용 신호).

**알아 둘 점**

- 에뮬레이터에는 Android 표준 TTS 엔진이 없어 Pleos TextToSpeech SDK를 씁니다.
- 오디오 포커스는 문장마다 요청하고 끝나면 돌려줍니다. SDK에 음량 조절이 없어 `LOSS_TRANSIENT_CAN_DUCK`에서는 음량을 낮추지 못하고 그대로 말합니다.
- `tools/console`은 공개 문서에 없는 Virtual Console 내부 API를 씁니다. 에뮬레이터 v2.1.0에서만 확인했고, 시연·시험 도구로만 씁니다.
- 조치 문구는 초안입니다. 차량 공식 매뉴얼과 대조가 필요한 항목은 `docs/c1/03-proposal-notes.md`에 있습니다.
