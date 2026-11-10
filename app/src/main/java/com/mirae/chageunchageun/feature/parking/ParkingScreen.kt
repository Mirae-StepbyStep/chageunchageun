package com.mirae.chageunchageun.feature.parking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Signal
import com.mirae.chageunchageun.runtime.AppGraph
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Approved Hyundai brand colors.
private val Navy = Color(0xFF002C5F)
private val Sand = Color(0xFFF6F3F2)
private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

@Composable
fun ParkingScreen(graph: AppGraph) {
    var developerMode by remember { mutableStateOf(false) }
    var manualArrival by remember { mutableStateOf(false) }
    var receivedArrival by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }

    var carDimensions by remember { mutableStateOf("조회 중") }
    var dimensionSource by remember { mutableStateOf("미수신") }

    var gearSignal by remember {
        mutableStateOf<Signal<Gear>>(
            Signal.Unavailable("미수신")
        )
    }
    var steeringSignal by remember {
        mutableStateOf<Signal<Float>>(
            Signal.Unavailable("미수신")
        )
    }
    var speedSignal by remember {
        mutableStateOf<Signal<Float>>(
            Signal.Unavailable("미수신")
        )
    }

    var voiceError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Developer values never control the vehicle.
    var demoGear by remember { mutableStateOf(Gear.P) }
    var demoAngle by remember { mutableFloatStateOf(0f) }
    var demoTargetOn by remember { mutableStateOf(false) }
    var demoTarget by remember { mutableFloatStateOf(60f) }

    // Read vehicle dimensions.
    LaunchedEffect(graph) {
        try {
            when (
                val result = graph.vehicleSpec.exteriorSize()
            ) {
                is Signal.Value -> {
                    val size = result.value

                    carDimensions =
                        "전장 %.2f m · 전폭 %.2f m · 전고 %.2f m"
                            .format(
                                Locale.KOREA,
                                size.lengthMm / 1000.0,
                                size.widthMm / 1000.0,
                                size.heightMm / 1000.0
                            )

                    dimensionSource = result.source.name
                }

                is Signal.Unavailable -> {
                    carDimensions = "정보 없음"
                    dimensionSource = result.reason
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            carDimensions = "정보 없음"
            dimensionSource = "조회 실패"
        }
    }

    // Observe vehicle signals.
    LaunchedEffect(graph) {
        graph.drivingSignals.gear.collect {
            gearSignal = it
        }
    }

    LaunchedEffect(graph) {
        graph.drivingSignals.steeringAngleDeg.collect {
            steeringSignal = it
        }
    }

    LaunchedEffect(graph) {
        graph.drivingSignals.speedMps.collect {
            speedSignal = it
        }
    }

    // Listen for destination arrival.
    LaunchedEffect(graph) {
        graph.locationSource.arrivals.collect { name ->
            destination = name
            receivedArrival = true

            try {
                graph.speechOutput.speak(
                    "목적지에 도착했습니다. " +
                            "주차 전 주변을 확인하세요."
                )
                voiceError = ""
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                voiceError = "음성 출력 연결을 확인해 주세요"
            }
        }
    }

    val arrived =
        receivedArrival || (developerMode && manualArrival)

    val liveGear =
        (gearSignal as? Signal.Value)?.value
    val liveAngle =
        (steeringSignal as? Signal.Value)?.value
    val liveSpeed =
        (speedSignal as? Signal.Value)?.value

    val gearSource =
        (gearSignal as? Signal.Value)?.source?.name
            ?: "미수신"

    val angleSource =
        (steeringSignal as? Signal.Value)?.source?.name
            ?: "미수신"

    val speedSource =
        (speedSignal as? Signal.Value)?.source?.name
            ?: "미수신"

    // Keep developer data separate from vehicle data.
    val shownGear =
        if (developerMode) demoGear else liveGear

    val shownAngle =
        if (developerMode) demoAngle else liveAngle

    // The gauge target comes only from the developer test for now.
    // There is no verified source for a real target yet.
    val gaugeTarget =
        if (developerMode && demoTargetOn) demoTarget else null

    // Voice for the gauge. Speaks only when the gauge state changes.
    val gaugePercent = shownAngle?.let { wheelAnglePercent(it) }
    val gaugeNow =
        if (gaugePercent != null)
            gaugeState(gaugePercent, gaugeTarget)
        else
            GaugeState.NO_TARGET

    val gaugeVoice = when (gaugeNow) {
        GaugeState.ON_TARGET -> "좋아요, 거기서 멈춰요"
        GaugeState.NEAR -> "곧 멈춰요"
        GaugeState.OVERSHOOT -> "너무 많이 돌렸어요"
        else -> null
    }

    val reversing = shownGear == Gear.R

    LaunchedEffect(gaugeNow, reversing) {
        if (gaugeVoice == null || !reversing) return@LaunchedEffect

        try {
            // Short wait so quick passes through a state stay silent.
            delay(300)
            graph.speechOutput.stop()
            graph.speechOutput.speak(gaugeVoice)
            voiceError = ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            voiceError = "음성 출력 연결을 확인해 주세요"
        }
    }

    val gearText = shownGear?.name ?: "정보 없음"

    val angleText = if (shownAngle == null) {
        "정보 없음"
    } else {
        when {
            shownAngle < -1f ->
                "왼쪽 %.1f°".format(
                    Locale.KOREA, -shownAngle
                )

            shownAngle > 1f ->
                "오른쪽 %.1f°".format(
                    Locale.KOREA, shownAngle
                )

            else -> "정면"
        }
    }

    // Guidance does not prescribe unverified steering maneuvers.
    val guideText = when {
        !arrived ->
            "목적지에 도착하면 주차 안내가 시작돼요."

        shownGear == Gear.R ->
            "후방과 주변 보행자를 확인하세요."

        shownGear == Gear.D ->
            "전방의 공간과 보행자를 확인하세요."

        shownGear == Gear.P ->
            "주차 전 주변을 확인하세요."

        else ->
            "안전하게 정차한 뒤 주변을 확인하세요."
    }

    val simulatedSignals =
        gearSource == "SCRIPTED" ||
                angleSource == "SCRIPTED" ||
                speedSource == "SCRIPTED"

    val canReplayVoice =
        liveSpeed != null &&
                liveSpeed < 0.1f &&
                liveGear == Gear.P

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(18.dp)
    ) {

        // Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "스마트 주차 코치",
                    color = Navy,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "B1 · 도착 및 주차 중 안내",
                    color = Black,
                    fontSize = 21.sp
                )
            }

            TextButton(
                onClick = {
                    developerMode = !developerMode
                }
            ) {
                Text(
                    text = if (developerMode)
                        "운전자 화면"
                    else
                        "개발자 모드",
                    color = Navy,
                    fontSize = 21.sp
                )
            }
        }

        // Demo data warning.
        if (developerMode || simulatedSignals) {
            Surface(
                color = Navy,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (developerMode)
                        "개발자 테스트 · 가상 데이터입니다"
                    else
                        "데모 데이터 연결됨 · 실제 차량 상태가 아닙니다",
                    color = White,
                    fontSize = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                )
            }
        }

        // Developer test strip. Fixed at the top so it never needs scrolling.
        if (developerMode) {
            Surface(
                color = White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                manualArrival = !manualArrival
                            }
                        ) {
                            Text(
                                text = if (manualArrival)
                                    "도착 해제"
                                else
                                    "가상 도착",
                                color = Navy,
                                fontSize = 18.sp
                            )
                        }

                        listOf(
                            Gear.P,
                            Gear.R,
                            Gear.N,
                            Gear.D
                        ).forEach { gear ->
                            Button(
                                onClick = {
                                    demoGear = gear
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            if (demoGear == gear)
                                                Navy
                                            else
                                                Sand,
                                        contentColor =
                                            if (demoGear == gear)
                                                White
                                            else
                                                Navy
                                    ),
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                Text(
                                    text = gear.name,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                demoTargetOn = !demoTargetOn
                            }
                        ) {
                            Text(
                                text = if (demoTargetOn)
                                    "목표 끄기"
                                else
                                    "목표 켜기",
                                color = Navy,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(16.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "가상 조향각: %.0f°"
                                    .format(Locale.KOREA, demoAngle),
                                color = Black,
                                fontSize = 16.sp
                            )

                            Slider(
                                value = demoAngle,
                                onValueChange = {
                                    demoAngle = it
                                },
                                valueRange = -35f..35f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Navy,
                                    activeTrackColor = Navy,
                                    inactiveTrackColor = Sand
                                )
                            )
                        }

                        if (demoTargetOn) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "가상 목표: %.0f%%"
                                        .format(Locale.KOREA, demoTarget),
                                    color = Black,
                                    fontSize = 16.sp
                                )

                                Slider(
                                    value = demoTarget,
                                    onValueChange = {
                                        demoTarget = it
                                    },
                                    valueRange = -100f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Navy,
                                        activeTrackColor = Navy,
                                        inactiveTrackColor = Sand
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        graph.speechOutput.speak(
                                            "음성 테스트입니다. 소리가 들리나요?"
                                        )
                                        voiceError =
                                            "음성 호출 완료 · 소리가 안 나면 음성 출력 구현을 확인하세요"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        voiceError =
                                            "음성 실패: " +
                                                    (e.message
                                                        ?: e.javaClass.simpleName)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "음성 테스트",
                                color = Navy,
                                fontSize = 18.sp
                            )
                        }

                        if (voiceError.isNotBlank()) {
                            Text(
                                text = voiceError,
                                color = Navy,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Camera on the left, guidance on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {
            CameraPanel(
                gear = shownGear,
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(18.dp)
            ) {

                // Main guidance card.
                Surface(
                    color = White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (arrived)
                                "주차 안내"
                            else
                                "도착 대기",
                            color = Navy,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = guideText,
                            color = Black,
                            fontSize = 34.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (destination.isNotBlank()) {
                            Text(
                                text = "도착: $destination",
                                color = Navy,
                                fontSize = 21.sp
                            )
                        }

                        Text(
                            text = "차량의 기본 주차 경고와 " +
                                    "카메라 화면을 우선 확인하세요.",
                            color = Black,
                            fontSize = 21.sp,
                            lineHeight = 29.sp
                        )
                    }
                }

                // Gear and steering state.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(18.dp)
                ) {
                    ParkingStatusCard(
                        title = "현재 기어",
                        value = gearText,
                        modifier = Modifier.weight(1f)
                    )

                    ParkingStatusCard(
                        title = "앞바퀴 조향 상태",
                        value = angleText,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Steering percent gauge.
                SteeringGaugeBar(
                    angleDeg = shownAngle,
                    targetPercent = gaugeTarget,
                    modifier = Modifier.fillMaxWidth()
                )

                // Vehicle dimensions.
                Surface(
                    color = White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "내 차량 크기",
                            color = Navy,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = carDimensions,
                            color = Black,
                            fontSize = 26.sp,
                            lineHeight = 36.sp
                        )

                        Text(
                            text = "정보 출처: $dimensionSource",
                            color = Navy,
                            fontSize = 18.sp
                        )
                    }
                }

                // Live vehicle signals, shown only in developer mode.
                if (developerMode) {
                    Surface(
                        color = White,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "실제 신호 · 기어: " +
                                        "${liveGear?.name ?: "정보 없음"} " +
                                        "($gearSource)",
                                color = Black,
                                fontSize = 18.sp
                            )

                            Text(
                                text = "실제 신호 · 조향: " +
                                        "${liveAngle?.toString() ?: "정보 없음"} " +
                                        "($angleSource)",
                                color = Black,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                // Manual voice replay while stationary in P.
                if (!developerMode && arrived && canReplayVoice) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    graph.speechOutput.speak(
                                        guideText
                                    )
                                    voiceError = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    voiceError =
                                        "음성 출력 연결을 확인해 주세요"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Navy,
                            contentColor = White
                        )
                    ) {
                        Text(
                            text = "안내 다시 듣기",
                            fontSize = 23.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (voiceError.isNotBlank()) {
                    Text(
                        text = voiceError,
                        color = Navy,
                        fontSize = 18.sp
                    )
                }

                Text(
                    text = "실제 센서 및 음성 출력 연결은 " +
                            "별도 검증이 필요합니다.",
                    color = Black,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

@Composable
private fun ParkingStatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = White,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Navy,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                color = Black,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 38.sp
            )
        }
    }
}