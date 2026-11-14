# Home / first-run recon (Step H0)

Checked 2026-11-13 on `jaegyoon/c1-appgraph-start` at `6e2db1f` (same commit as `origin/main`).
Paths are under `app/src/main/java/com/mirae/chageunchageun/` unless they start with `app/` or `docs/`.

## Git

- Current branch `jaegyoon/c1-appgraph-start`, working tree clean. Note the spelling: `jaegyoon`, not `jaegyun`.
- `HEAD` is exactly `origin/main` (0 ahead, 0 behind). C1 is already merged into main; there are no unmerged C1 commits to depend on. `jaegyoon/c1-warning`'s six extra commits are patch-equivalent to commits on main (`git cherry` shows all `-`).
- The local branch is 14 commits ahead of its own remote `origin/jaegyoon/c1-appgraph-start` (the remote branch is stale, not main).
- `home-firstrun-plan.md` and `c1-plan.md` are ignored through `.git/info/exclude`.
- Proposed branch: `jaegyoon/home-firstrun` from `origin/main`. Not created.

## Tabs and routing

- `runtime/MainActivity.kt` holds everything: `enum class AppScreen` and `AppRoot(graph)`.
- Tab state: `var current by rememberSaveable { mutableStateOf(AppScreen.HOME) }` inside `AppRoot`. Always starts on `HOME`.
- Identifiers: 홈 `AppScreen.HOME`, 첫 실행 `AppScreen.ONBOARDING`, 미러 맞추기 `AppScreen.MIRROR`, C1 경고 `AppScreen.WARNING`, 조작 연습 `AppScreen.PRACTICE`. Others: `PARKING`, `TOLL`, `VOICE`, `SIGNALS` and `DEBUG` (debug builds only).
- Switching tabs from a screen today, two existing ways:
  1. `graph.screenRequests.tryEmit(AppScreen.X)` — a `MutableSharedFlow<AppScreen>` on `AppGraph`, collected by `AppRoot`. Only user so far: `WarningAnnouncer` (opens `WARNING` when parked with a warning left on). Needs no `MainActivity` change, but makes `feature/` import `runtime.AppScreen`.
  2. A callback parameter set in `AppRoot`'s `when`: only `MirrorScreen(graph, onDone = { current = AppScreen.HOME })`.
- There is no "edit mode" argument for any tab; screens take only `graph` (plus `onDone` for mirror).

## Home example

- `feature/home/HomeViewModel.kt`: `HomeViewModel(driveState: DriveStateSource, controls: ControlsSource, runtime: DriveRuntime)`. Exposes `ui: StateFlow<HomeUi>` with `phase`, `gear`, `gearSource`, `highBeam`, `highBeamSource`. Reads `runtime.phase`, `driveState.gear`, `controls.lamp(Lamp.HIGH_BEAM)`. It does not read `canShowDetail`.
- `feature/home/HomeScreen.kt`: `HomeScreen(graph: AppGraph)`. Title from `ui.phase`, gear and high-beam `InfoCard`s in a `FlowRow`, then `DrivingGate(graph.driveRuntime)` around the placeholder card. No scroll container. The gear and high-beam cards are shown while driving too.
- Places that point other features at it:
  - `README.md:55` "`feature/home`(상향등 예시)을 따라 합니다", followed by the four-step recipe.
  - `ui/common/Common.kt:62`, inside `PlaceholderScreen`: "feature/home 의 예시(HomeViewModel → HomeScreen)를 따라 만드세요." Shown on every placeholder tab.
  - `feature/home/HomeViewModel.kt:30` KDoc "예시 기능: 포트 → ViewModel → 화면".
  - `feature/home/HomeScreen.kt:47` comment "상세 영역 예시".
  - `app/src/test/.../demo/ScenarioTest.kt:60-64` constructs `HomeViewModel(driveState, FakeControlsSource(bus), runtime)` with three positional arguments. Adding constructor parameters without defaults breaks this test.

## 첫 실행 placeholder

- `feature/onboarding/OnboardingScreen.kt` (package `feature.onboarding`, not `feature.firstrun`). It calls `PlaceholderScreen(title, owner = "후", ports = "없음 (앱 내부 설정, data/)")`. Its KDoc still says "A4 첫 실행 설정 화면".

## Persistence

- No `data/` package exists. `README.md:22` reserves it: "data/ Room DB, 설정".
- No DataStore dependency. No SharedPreferences use or wrapper anywhere.
- Room 2.6.1 (`room-runtime`, `room-ktx`, KSP compiler) is already in `app/build.gradle.kts`, but no `@Database`, `@Entity` or `@Dao` exists.
- So the only persistence that needs no new dependency is framework `SharedPreferences` (or Room, which the task rules exclude).

## AndroidManifest location permissions

- `android.permission.ACCESS_FINE_LOCATION`: not declared.
- `android.permission.ACCESS_COARSE_LOCATION`: not declared.
- `pleos.car.permission.FUSED_LOCATION` and `pleos.car.permission.NAVI_ROUTE`: declared, under a comment marked "미확인" (names copied from the PRD's SDK table). `README.md:153` lists confirming them as open work.
- Could not verify the Pleos permission string or its protection level. No FusedLocation SDK artifact is on the classpath (only `Vehicle:2.3.2`, `vehicle-aidl:1.1.1`, `vehicle-data:1.1.11`, `TextToSpeech:3.3.1`, `universal-access:0.6.0`), and no doc in the repo defines it.

## FusedLocation.showLocationAccessBlockedPopup()

- Not on the classpath. Unpacked every Pleos AAR in the Gradle cache: no class or string matching `FusedLocation`, `FUSED_LOCATION` or `showLocationAccessBlockedPopup`. The only "location" classes are `PortLocationType*` (charge port position).
- Its purpose could not be verified from the SDK or repo docs.
- `platform/nav/PleosLocationSource.kt` (민서) is a stub: `location = flowOf(NOT_IMPLEMENTED)`, `arrivals = emptyFlow()`.

## DriveRuntime and C1 state

- `runtime/DriveRuntime.kt`: `val canShowDetail: StateFlow<Boolean>`, eagerly shared, initial `false`. True only when gear is confirmed `P` and UX restriction is confirmed off. Collect with `collectAsStateWithLifecycle()` in a composable (as `DrivingGate` does) or `combine` it in a ViewModel. Also `val phase: StateFlow<DrivePhase>` (PARKED only when gear is P).
- `ui/common/Common.kt` `DrivingGate(runtime) { ... }` replaces content with a centred card "기어를 P에 두면 볼 수 있습니다". The first-run and Home driving sentences differ from this, so those screens need to read `canShowDetail` directly.
- C1 active warnings are readable without touching C1 code:
  - `graph.warningSource.activeWarnings: Flow<Signal<Set<WarningType>>>` (port).
  - `domain/guidance/WarningGuidance` is public: `relevant(types, phase)`, `ordered(types): List<WarningGuide>`, `severityLabel(severity)`, `speech(guide)`. `WarningGuide` has `title`, `severity`, `meaning`, `driveAction`, `parkedSteps`.
  - `WarningViewModel.ui` is per-screen, not shared; Home would build its own from the port, the same way.
- Unknown from this pass: which exact sentence C1's driving card shows, and whether a telltale-icon lookup is public or private to `WarningScreen.kt`. Only `res/drawable/ic_telltale_tpms.xml` exists as an icon resource.
- Colour: the task brief says C1's red is `#FF3B30` on a black tile. The code has `AppColors.WarningRed = #B3261E` and `AppColors.WarningOrange = #E8710A` in `ui/theme/Theme.kt`; `#FF3B30` appears nowhere.

## A3 and A5

- A3 미러 맞추기: real. `feature/mirror/MirrorScreen.kt` (457 lines), `MirrorViewModel`, `domain/mirror/MirrorGuide.kt`, tests.
- A5 조작 연습: real, marked "초안" in its KDoc. `feature/practice/PracticeScreen.kt` (367 lines), `PracticeGameScreen.kt` (631), two ViewModels, `domain/practice/`, tests.
- Still placeholders: `ParkingScreen`, `TollScreen`, `VoiceScreen`, `OnboardingScreen`.

## Launcher icon

- `minSdk = 28`, `targetSdk = 35`, `compileSdk = 36`.
- Manifest: `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"`, `android:label="@string/app_name"`.
- Files, all Android Studio defaults:
  - `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive icons with background, foreground and monochrome.
  - `res/drawable/ic_launcher_background.xml` (green `#3DDC84` grid), `res/drawable/ic_launcher_foreground.xml` (Android robot).
  - `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` and `ic_launcher_round.webp`: ten files, never used at minSdk 28.
- Monochrome layer: present, but it points at `@drawable/ic_launcher_foreground`; there is no separate monochrome drawable.
- `app_name` = `차근차근` (`res/values/strings.xml`, the only string resource; all other UI copy is hard-coded in Kotlin).

## Theme notes

- `ui/theme/Theme.kt` sets colours only. No typography or font family is defined and there is no `res/font/`, so text uses the Material default font, not Hyundai Sans or Asta Sans.
- A dark colour scheme exists and follows `isSystemInDarkTheme()` (Hyundai-blue background, white text).

## Build and tests

Run from the repo root on this commit:

```
./gradlew --offline assembleDebug testDebugUnitTest --console=plain
```

- `BUILD SUCCESSFUL`. All 47 tasks were `UP-TO-DATE`, so this confirmed existing outputs rather than recompiling.
- `assembleDebug`: `app/build/outputs/apk/debug/app-debug.apk` present.
- `testDebugUnitTest`: 16 suites, 113 tests, 0 failures, 0 errors, 0 skipped (from `app/build/test-results/testDebugUnitTest/*.xml`).
- `--offline` was my addition; instrumented tests were not run.
