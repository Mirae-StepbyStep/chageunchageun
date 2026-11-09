package com.example.chaguenchaguen.runtime

import android.content.Context
import com.example.chaguenchaguen.core.port.DrivingSignals
import com.example.chaguenchaguen.core.port.LocationSource
import com.example.chaguenchaguen.core.port.SeatController
import com.example.chaguenchaguen.core.port.SpeechInput
import com.example.chaguenchaguen.core.port.SpeechOutput
import com.example.chaguenchaguen.core.port.VehicleSpecSource
import com.example.chaguenchaguen.core.port.WarningSource
import com.example.chaguenchaguen.demo.DemoSeatPlant
import com.example.chaguenchaguen.demo.FakeDrivingSignals
import com.example.chaguenchaguen.demo.FakeLocationSource
import com.example.chaguenchaguen.demo.FakeSeatController
import com.example.chaguenchaguen.demo.FakeSpeechInput
import com.example.chaguenchaguen.demo.FakeSpeechOutput
import com.example.chaguenchaguen.demo.FakeVehicleBus
import com.example.chaguenchaguen.demo.FakeVehicleSpecSource
import com.example.chaguenchaguen.demo.FakeWarningSource
import com.example.chaguenchaguen.demo.Scenario
import com.example.chaguenchaguen.demo.ScenarioParser
import com.example.chaguenchaguen.demo.ScenarioPlayer
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
    val speechOutput: SpeechOutput = FakeSpeechOutput(fakeBus)
    val speechInput: SpeechInput = FakeSpeechInput(fakeBus)

    // 앱 공용 런타임
    val driveRuntime = DriveRuntime(drivingSignals, appScope)

    fun scenarioNames(): List<String> =
        context.assets.list("scenarios").orEmpty().filter { it.endsWith(".json") }.sorted()

    fun loadScenario(fileName: String): Scenario =
        context.assets.open("scenarios/$fileName").bufferedReader().use { ScenarioParser.parse(it.readText()) }
}
