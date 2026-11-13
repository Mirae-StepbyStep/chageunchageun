# C1 recon (Step 0)

Checked 2026-11-12 on `jaegyun/c1-appgraph-start` at `4a32fe6`, after `git fetch origin`.
Everything below describes this branch unless it says "on main".

## Sync with origin/main

The branch is 7 ahead and 4 behind `origin/main` (`d0b1168`). It has not been rebased.

| Incoming | Date | What it does |
|---|---|---|
| `9dc7ab2` | 11/10 | Signal foundation: ports split into one file each, real/fake switching, platform stubs |
| `5ce7228` | 11/10 | Merge of PR #2 (the commit above) |
| `1d98cfa` | 11/10 | A3 mirror guide screen |
| `d0b1168` | 11/11 | Merge of PR #3 (the commit above) |

What `9dc7ab2` changes in the areas C1 depends on:

| Area | Change on main | Effect on C1 |
|---|---|---|
| core/port | `Ports.kt` deleted, one file per port. `DrivingSignals` is now `DriveStateSource` (gear, uxRestricted only). | `WarningSource` and `SpeechOutput` signatures are unchanged. |
| AppGraph | Rewritten. Every port is a `Switching…` wrapper over a real and a fake implementation, chosen per port by `PortModes` (default all fake, switched in the debug panel). | The 7 lines from `4a32fe6` conflict. This is the only conflicting file in a dry-run merge. |
| DriveRuntime | Constructor takes `DriveStateSource`. `phase`, `canShowDetail`, `events`, `report` are unchanged. | `WarningAnnouncerTest` builds it with `FakeDrivingSignals`, which is renamed `FakeDriveStateSource`. Two lines need the new name or the tests will not compile. |
| Speaking port | `SpeechOutput.kt` split out, same signature. `platform/speech/PleosSpeechOutput.kt` added as a silent stub. | None yet. |
| Scenario loader | `ScenarioStep` drops `speedMps` and adds lamps, wiper, steering. | `c1_warning.json` still has `speedMps`. The parser ignores unknown keys, so it loads; the key is dead. |
| platform | `platform/vehicle/PleosWarningSource.kt` added as a stub owned by 재균. Its header says to fill the body and leave the constructor alone. | This is where the Step 5 adapter goes. |
| Manifest | Declares `pleos.car.permission.CAR_SAFETY` and `pleos.car.permission.PRIVATE_CAR_SAFETY` for the warning adapter. | Step 1 assumed we would add `PRIVATE_CAR_SAFETY` ourselves in a revertable commit. It is already on main. |
| feature/warning, domain/guidance | Not touched. | None. |

`1d98cfa` touches none of these areas.

## Build and tests

Command: `./gradlew assembleDebug testDebugUnitTest --console=plain`

Result on this branch (before any rebase): BUILD SUCCESSFUL. 24 unit tests, 0 failures, 0 skipped.

| Test class | Tests |
|---|---|
| `WarningGuidanceTest` | 7 |
| `WarningAnnouncerTest` | 8 |
| `WarningViewModelTest` | 3 |
| `ScenarioTest` | 3 |
| `DrivePhaseResolverTest` | 3 |

Lint and instrumented tests were not run.

## Layout

- One Gradle module, `app`, package `com.mirae.chageunchageun`. minSdk 28, targetSdk 35, compileSdk 36.
- Packages: `core/model`, `core/port`, `domain`, `feature/<name>`, `demo`, `platform`, `runtime`, `ui`.
- UI: Jetpack Compose with Material 3. Navigation is a `NavigationRail` over the `AppScreen` enum in `MainActivity.kt`; the current screen is local state in `AppRoot`.
- DI: manual. `ChageunApp` owns one `AppGraph`; screens receive the graph and build their ViewModel with `viewModel { … }`.
- SDKs: `libs.pleos.vehicle` from the Pleos Nexus repo, and `useLibrary("android.car")`.
- Demo vs platform on this branch: `AppGraph` hard-wires every port to its `Fake…` class. On main it is switchable per port (see above).

## Ports C1 uses

```kotlin
interface WarningSource {
    val activeWarnings: Flow<Signal<Set<WarningType>>>
}

class FakeWarningSource(bus: FakeVehicleBus) : WarningSource {
    override val activeWarnings = bus.warnings.map(::scripted)   // Source.SCRIPTED
}

interface SpeechOutput {
    suspend fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL)
    fun stop()
}

enum class SpeechPriority { NORMAL, WARNING }
enum class WarningType { TIRE_PRESSURE, ENGINE, BRAKE_FLUID, ENGINE_OIL, SEAT_BELT, AIRBAG, WASHER_FLUID }
```

- The port emits the full set of active warnings, so on/off is the difference between two emissions. `Signal.Value` carries `source` and `atMillis`.
- `FakeSpeechOutput.speak` records the sentence in `bus.spoken` and returns at once. It makes no sound.

Driving vs stopped, from `DriveRuntime`:

```kotlin
val phase: StateFlow<DrivePhase>          // PARKED only when gear is confirmed P
val canShowDetail: StateFlow<Boolean>     // PARKED and uxRestricted confirmed false
val events: SharedFlow<SituationEvent>
fun report(event: SituationEvent)
```

- The rule lives in `domain/drive/DrivePhaseResolver`. Unknown gear or unknown restriction counts as driving.
- `uxRestricted` is the port's view of CarUxRestrictions. On main the real source is `platform/car/CarDriveStateSource`.
- `ui/common/DrivingGate` wraps detail content and shows "정차 후에 자세히 볼 수 있어요" when `canShowDetail` is false.

## C1 files on this branch

| File | What it does |
|---|---|
| `domain/guidance/WarningGuidance.kt` | Catalog: one `WarningGuide` (type, severity, title, meaning, driveAction, parkedSteps) per `WarningType`. `ordered()` sorts by severity, `speech()` builds the voice sentence and returns null for INFO. |
| `feature/warning/WarningAnnouncer.kt` | Collects `activeWarnings`, and for each newly added warning reports a `SituationEvent` and speaks with `SpeechPriority.WARNING`. Ignores `Unavailable`. Speech failures are swallowed. |
| `feature/warning/WarningViewModel.kt` | Maps `activeWarnings` to `WarningUi(available, guides, source)`. |
| `feature/warning/WarningScreen.kt` | Header with severity counts, a glance banner while driving, guide cards with numbered steps when stopped. The whole screen is inside `verticalScroll`. |
| `assets/scenarios/c1_warning.json` | Park, drive, TPMS at 6 s, brake fluid at 14 s, washer fluid at 18 s, park at 25 s. Warnings are never cleared. |
| `runtime/AppGraph.kt` (`4a32fe6`) | Two imports and an `init` block that launches `WarningAnnouncer.run()` on `appScope`. |

## Open points

**Does the detail open by itself when the car stops?**
Only if the C1 tab is already showing: the banner is replaced by the step cards when `canShowDetail` turns true. Nothing switches to the C1 tab from another screen, and the tab state lives in `AppRoot` in `MainActivity.kt`, which is a shared file.

**What happens when a warning clears?**
- Screen: its card disappears immediately. With no warnings left the screen shows "확인된 경고가 없어요".
- Announcer: it forgets the warning and says nothing. It does not call `speech.stop()`, so a sentence already playing would finish. If the warning comes back it is announced again in full.
- An `Unavailable` signal is not treated as a clear. The screen shows "경고 상태를 확인할 수 없어요" and the announcer keeps its state.

**Which warnings are in the catalog, and which does the scenario fire?**

| Type | Severity | In scenario |
|---|---|---|
| TIRE_PRESSURE | CAUTION | yes |
| ENGINE | DANGER | no |
| BRAKE_FLUID | DANGER | yes |
| ENGINE_OIL | DANGER | no |
| SEAT_BELT | CAUTION | no |
| AIRBAG | CAUTION | no |
| WASHER_FLUID | INFO | yes |

**Platform speech adapter and audio focus**
- This branch has no platform speech adapter. On main, `PleosSpeechOutput` exists but does nothing.
- There is no `AudioFocusRequest`, `AudioAttributes` or `TextToSpeech` use anywhere, on this branch or on main.
- The speaking port was last touched by HooK in `9dc7ab2` (11/10), a file split with no signature change. Its header assigns it to "E2 담당".

## Questions for the framework owner

1. Where should app-lifetime work such as the C1 announcer start, now that `AppGraph` says adapters should not need to change it?
2. `PRIVATE_CAR_SAFETY` is declared on main with the note "주최 측 승인 필요". The contest office wrote that there is no approval process. Keep it declared until the Step 1 probe, or remove it?
3. Opening the C1 detail on stop from another screen needs a way to change the current `AppScreen`. Is that acceptable in `MainActivity.kt`?
