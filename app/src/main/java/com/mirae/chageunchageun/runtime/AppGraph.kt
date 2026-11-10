package com.mirae.chageunchageun.runtime

import android.content.Context
import com.mirae.chageunchageun.core.port.DrivingSignals
import com.mirae.chageunchageun.core.port.LocationSource
import com.mirae.chageunchageun.core.port.SeatController
import com.mirae.chageunchageun.core.port.SpeechInput
import com.mirae.chageunchageun.core.port.SpeechOutput
import com.mirae.chageunchageun.core.port.VehicleSpecSource
import com.mirae.chageunchageun.core.port.WarningSource
import com.mirae.chageunchageun.demo.DemoSeatPlant
import com.mirae.chageunchageun.demo.FakeDrivingSignals
import com.mirae.chageunchageun.demo.FakeLocationSource
import com.mirae.chageunchageun.demo.FakeSeatController
import com.mirae.chageunchageun.demo.FakeSpeechInput
import com.mirae.chageunchageun.demo.FakeVehicleBus
import com.mirae.chageunchageun.demo.FakeVehicleSpecSource
import com.mirae.chageunchageun.demo.FakeWarningSource
import com.mirae.chageunchageun.demo.Scenario
import com.mirae.chageunchageun.demo.ScenarioParser
import com.mirae.chageunchageun.demo.ScenarioPlayer
import com.mirae.chageunchageun.platform.AndroidTtsSpeechOutput
import kotlinx.coroutines.CoroutineScope

/**
 * 앱의 조립 지점(수동 DI). 포트마다 실제 구현과 가짜 구현 중 하나를 여기서 고릅니다.
 *
 * v0: 모든 포트가 가짜(demo/)입니다.
 * v1: platform/ 어댑터가 생기면 해당 포트만 실제 구현으로 바꿉니다. feature 코드는 바뀌지 않습니다.
 * 이 파일을 바꾸려면 PR 리뷰를 받습니다.
 */
class AppGraph(private val context: Context, val appScope: CoroutineScope) {

    // 시연·테스트용 가짜 차량
    val fakeBus = FakeVehicleBus()
    val scenarioPlayer = ScenarioPlayer(fakeBus)
    private val seatPlant = DemoSeatPlant(fakeBus, appScope)

    // 포트
    val drivingSignals: DrivingSignals = FakeDrivingSignals(fakeBus)
    val warningSource: WarningSource = FakeWarningSource(fakeBus)
    val seatController: SeatController = FakeSeatController(fakeBus, seatPlant)
    val vehicleSpec: VehicleSpecSource = FakeVehicleSpecSource(fakeBus)
    val locationSource: LocationSource = FakeLocationSource(fakeBus)

    // 음성 출력만 실제 소리가 나는 구현입니다. 말한 문장은 가짜 버스에도 계속 기록합니다.
    val speechOutput: SpeechOutput =
        AndroidTtsSpeechOutput(context, onSpoken = fakeBus::recordSpoken)
    val speechInput: SpeechInput = FakeSpeechInput(fakeBus)

    // 앱 공용 런타임
    val driveRuntime = DriveRuntime(drivingSignals, appScope)

    fun scenarioNames(): List<String> =
        context.assets.list("scenarios").orEmpty().filter { it.endsWith(".json") }.sorted()

    fun loadScenario(fileName: String): Scenario =
        context.assets.open("scenarios/$fileName").bufferedReader().use { ScenarioParser.parse(it.readText()) }
}