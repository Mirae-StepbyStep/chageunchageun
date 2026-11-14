package com.mirae.chageunchageun.runtime

import android.content.Context
import com.mirae.chageunchageun.core.port.ControlsSource
import com.mirae.chageunchageun.core.port.DriveStateSource
import com.mirae.chageunchageun.core.port.LocationSource
import com.mirae.chageunchageun.core.port.SpeechInput
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.SteeringSource
import com.mirae.chageunchageun.core.port.VehicleSpecSource
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.data.settings.PrefsSettingsStore
import com.mirae.chageunchageun.data.settings.StoredUserSettings
import com.mirae.chageunchageun.demo.FakeControlsSource
import com.mirae.chageunchageun.demo.FakeDriveStateSource
import com.mirae.chageunchageun.demo.FakeLocationSource
import com.mirae.chageunchageun.demo.FakeSpeechInput
import com.mirae.chageunchageun.demo.FakeSpeechOutput
import com.mirae.chageunchageun.demo.FakeSteeringSource
import com.mirae.chageunchageun.demo.FakeVehicleBus
import com.mirae.chageunchageun.demo.FakeVehicleSpecSource
import com.mirae.chageunchageun.demo.FakeWarningSource
import com.mirae.chageunchageun.demo.Scenario
import com.mirae.chageunchageun.demo.ScenarioParser
import com.mirae.chageunchageun.demo.ScenarioPlayer
import com.mirae.chageunchageun.domain.settings.UserSettings
import com.mirae.chageunchageun.feature.warning.WarningAnnouncer
import com.mirae.chageunchageun.platform.car.CarDriveStateSource
import com.mirae.chageunchageun.platform.car.CarSession
import com.mirae.chageunchageun.platform.car.CarVehicleSpecSource
import com.mirae.chageunchageun.platform.nav.PleosLocationSource
import com.mirae.chageunchageun.platform.speech.PleosSpeechInput
import com.mirae.chageunchageun.platform.speech.PleosSpeechOutput
import com.mirae.chageunchageun.platform.vehicle.PleosControlsSource
import com.mirae.chageunchageun.platform.vehicle.PleosSteeringSource
import com.mirae.chageunchageun.platform.vehicle.PleosVehicleSession
import com.mirae.chageunchageun.platform.vehicle.PleosWarningSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * 앱의 조립 지점(수동 DI). 포트마다 실제 구현(platform/)과 가짜 구현(demo/)을 둘 다 만들어 두고,
 * [portModes]에 따라 하나를 내보냅니다. 어댑터가 구현된 포트([CONNECTED_PORTS])는 실제로 시작하고, 디버그 패널에서 포트별로 바꿉니다.
 *
 * 어댑터를 구현할 때는 platform/의 자기 파일 본문만 채웁니다. 이 파일은 바꾸지 않아도 됩니다.
 * 이 파일을 바꾸려면 PR 리뷰를 받습니다.
 */
class AppGraph(private val context: Context, val appScope: CoroutineScope) {

    // 시연·테스트용 가짜 차량
    val fakeBus = FakeVehicleBus()
    val scenarioPlayer = ScenarioPlayer(fakeBus)

    // 실제 차량 연결. 어댑터가 처음 쓸 때 초기화됩니다.
    val vehicleSession = PleosVehicleSession(context)
    val carSession = CarSession(context)

    /** 포트별 실제·가짜 선택. */
    val portModes = PortModes(real = CONNECTED_PORTS)

    // 포트
    val driveState: DriveStateSource = SwitchingDriveStateSource(
        switch(PortId.DRIVE_STATE, CarDriveStateSource(carSession), FakeDriveStateSource(fakeBus)),
    )
    val controls: ControlsSource = SwitchingControlsSource(
        switch(PortId.CONTROLS, PleosControlsSource(vehicleSession), FakeControlsSource(fakeBus)),
    )
    val steering: SteeringSource = SwitchingSteeringSource(
        switch(PortId.STEERING, PleosSteeringSource(vehicleSession), FakeSteeringSource(fakeBus)),
    )
    val warningSource: WarningSource = SwitchingWarningSource(
        switch(PortId.WARNING, PleosWarningSource(vehicleSession), FakeWarningSource(fakeBus)),
    )
    val vehicleSpec: VehicleSpecSource = SwitchingVehicleSpecSource(
        switch(PortId.VEHICLE_SPEC, CarVehicleSpecSource(carSession), FakeVehicleSpecSource(fakeBus)),
    )
    val locationSource: LocationSource = SwitchingLocationSource(
        switch(PortId.LOCATION, PleosLocationSource(context), FakeLocationSource(fakeBus)),
    )
    val speechOutput: SpeechOutput = SwitchingSpeechOutput(
        switch(PortId.SPEECH_OUT, PleosSpeechOutput(context), FakeSpeechOutput(fakeBus)),
    )
    val speechInput: SpeechInput = SwitchingSpeechInput(
        switch(PortId.SPEECH_IN, PleosSpeechInput(context), FakeSpeechInput(fakeBus)),
    )

    // 앱 공용 런타임
    val driveRuntime = DriveRuntime(driveState, appScope)

    /** 앱 내부 설정(첫 실행 완료, 위치 동의, 안내 깊이). 포트가 아니라서 실제·가짜 전환이 없습니다. */
    val userSettings: UserSettings = StoredUserSettings(PrefsSettingsStore(context))

    /** 화면 전환 요청. MainActivity가 받아 해당 화면으로 바꿉니다. 사용자가 그 뒤에 다른 화면으로 가는 것은 막지 않습니다. */
    val screenRequests = MutableSharedFlow<AppScreen>(extraBufferCapacity = 1)

    init {
        // C1 경고 음성은 어떤 화면이 열려 있어도 나와야 합니다.
        // 주행 중에 켜진 경고가 남은 채 주차하면 C1 화면을 열어 조치 단계를 보여 줍니다.
        appScope.launch {
            WarningAnnouncer(warningSource, speechOutput, driveRuntime) { screenRequests.tryEmit(AppScreen.WARNING) }.run()
        }
    }

    private fun <P> switch(id: PortId, real: P, fake: P) = PortSwitch(portModes, id, real, fake)

    fun scenarioNames(): List<String> =
        context.assets.list("scenarios").orEmpty().filter { it.endsWith(".json") }.sorted()

    fun loadScenario(fileName: String): Scenario =
        context.assets.open("scenarios/$fileName").bufferedReader().use { ScenarioParser.parse(it.readText()) }
}
