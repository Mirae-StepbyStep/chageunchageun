package com.mirae.chageunchageun.feature.parking

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.mirae.chageunchageun.core.model.Source
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.runtime.PortId
import com.mirae.chageunchageun.runtime.PortMode
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Approved Hyundai brand colors.
private val Navy = Color(0xFF002C5F)
private val Sand = Color(0xFFF6F3F2)
private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val DevBorder = Color(0xFF888780)
private val DevBadgeBg = Color(0xFFFAEEDA)
private val DevBadgeText = Color(0xFF633806)

/**
 * 핸들 조작 지시(목표 각도)가 실제 차량에서 검증됐는지.
 * false인 동안에는 개발자 모드나 시연 신호(SCRIPTED)에서만 목표를 보여 주고 "시뮬레이션 안내"로 표시합니다.
 */
private const val STEERING_GUIDANCE_VERIFIED = false

/** 이 너비 이상이면 카메라 두 화면을 나란히 놓습니다(Pleos 앱 창 가로 모드 대비). */
private val WideBreakpoint = 900.dp

/** 이 속도(m/s) 이상이면 움직이는 중으로 봅니다. */
private const val MOVING_SPEED_MPS = 0.1f


private const val DEV_ARRIVAL_NAME = "가상 목적지"

/** 이전 음성을 끊은 뒤 다음 음성까지 기다리는 시간(ms). TTS의 늦은 onStop 알림을 피합니다. */
private const val STOP_SETTLE_MS = 300L

/*
 * 상태 흐름(한 방향)
 *
 *   개발자 패널 ──쓰기──▶ FakeVehicleBus ──▶ DrivingSignals / LocationSource ──▶ 이 화면
 *   (기어·핸들·속도·도착)                       (기어·핸들·속도·도착 이벤트)
 *
 *   도착 이벤트 → 주차 안내 시작(arrived)
 *   속도·기어·핸들 → 차량 위치(pose) → ParkingPlanner.trackPlan → 목표 조향각
 *   현재 핸들 + 목표 조향각 → steeringInstruction → 화면(지금 할 일·게이지·카메라) + 음성(VoiceGuidePolicy)
 *
 * 개발자 값도 FakeVehicleBus를 거치므로 화면의 "실제 신호"와 개발자 패널 값이 항상 같습니다.
 * AppGraph가 실제 차량 어댑터로 바뀌면(출처가 SCRIPTED가 아니면) 개발자 조작은 막습니다.
 */
@Composable
fun ParkingScreen(graph: AppGraph) {
    val bus = graph.fakeBus
    // trial/with-minseo-parking: main removed the speed signal (old DrivingSignals.speedMps and FakeVehicleBus.speedMps).
    // The simulated speed is kept local to this screen instead of adding a port.
    val speedMps = remember { MutableStateFlow(0f) }

    var developerMode by remember { mutableStateOf(false) }
    var receivedArrival by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }

    var carDimensions by remember { mutableStateOf("조회 중") }
    var dimensionSource by remember { mutableStateOf("미수신") }
    var geometry by remember { mutableStateOf(VehicleGeometry()) }

    var gearSignal by remember {
        mutableStateOf<Signal<Gear>>(Signal.Unavailable("미수신"))
    }
    var steeringSignal by remember {
        mutableStateOf<Signal<Float>>(Signal.Unavailable("미수신"))
    }
    var speedSignal by remember {
        mutableStateOf<Signal<Float>>(Signal.Unavailable("미수신"))
    }

    var voiceError by remember { mutableStateOf("") }
    var arrivalNote by remember { mutableStateOf("") }
    var lastSpoken by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // The driver picks the side. The stall itself is a simulated example, not a detected one.
    var parkSide by remember { mutableStateOf(ParkSide.RIGHT) }

    // One simulation owns the virtual car pose. Steering alone never moves it.
    val sim = remember { ReverseSimulation(geometry = { geometry }) }
    var simSnap by remember { mutableStateOf(sim.snapshot()) }
    val pose = simSnap.pose
    var autoSteer by remember { mutableStateOf(false) }
    var devExpanded by remember { mutableStateOf(false) }
    val scene = remember(parkSide) { exampleScene(parkSide) }

    // Read vehicle dimensions.
    LaunchedEffect(graph) {
        try {
            when (val result = graph.vehicleSpec.exteriorSize()) {
                is Signal.Value -> {
                    val size = result.value
                    carDimensions =
                        "전장 %.2f m · 전폭 %.2f m · 전고 %.2f m".format(
                            Locale.KOREA,
                            size.lengthMm / 1000.0,
                            size.widthMm / 1000.0,
                            size.heightMm / 1000.0
                        )
                    dimensionSource = result.source.name
                    geometry = VehicleGeometry.fromSize(size.lengthMm, size.widthMm)
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

    // Observe vehicle signals. This screen reads gear, steering and speed only from here.
    LaunchedEffect(graph) {
        graph.driveState.gear.collect { gearSignal = it }
    }
    LaunchedEffect(graph) {
        graph.steering.angleDeg.collect { steeringSignal = it }
    }
    LaunchedEffect(graph) {
        speedMps.collect { speedSignal = Signal.Value(it, Source.SCRIPTED) }
    }

    // Arrival starts the parking guidance. The developer "가상 도착" sends through the same path.
    LaunchedEffect(graph) {
        graph.locationSource.arrivals.collect { name ->
            destination = name
            receivedArrival = true
            arrivalNote = ""
        }
    }

    val arrived = receivedArrival

    val liveGear = (gearSignal as? Signal.Value)?.value
    val liveAngle = (steeringSignal as? Signal.Value)?.value
    val liveSpeed = (speedSignal as? Signal.Value)?.value

    val gearSource = (gearSignal as? Signal.Value)?.source
    val angleSource = (steeringSignal as? Signal.Value)?.source
    val speedSource = (speedSignal as? Signal.Value)?.source

    val simulatedSignals =
        gearSource == Source.SCRIPTED ||
                angleSource == Source.SCRIPTED ||
                speedSource == Source.SCRIPTED

    // Developer writes reach this screen only while the signals come from FakeVehicleBus.
    val devControlsWork =
        listOf(gearSource, angleSource, speedSource).all { it == null || it == Source.SCRIPTED }

    // "가상 도착" only writes FakeVehicleBus.arrivals, which reaches this screen through the LOCATION port.
    // It needs that port to be FAKE, not gear, steering or speed.
    val locationMode by graph.portModes.mode(PortId.LOCATION)
        .collectAsState(initial = graph.portModes.get(PortId.LOCATION))
    val arrivalControlWorks = arrivalControlWorks(locationMode)

    val moving = liveSpeed != null && liveSpeed > MOVING_SPEED_MPS

    // Steering targets are not verified on a real vehicle, so they appear only as a simulation.
    val guidanceSimulated = !STEERING_GUIDANCE_VERIFIED
    val guidanceAllowed = STEERING_GUIDANCE_VERIFIED || developerMode || simulatedSignals

    // Plan the path into the stall once per side and vehicle size, then track it as the car moves.
    val plan = remember(parkSide, geometry, scene) {
        planReversePerpendicular(Pose(), parkSide, scene, geometry)
    }
    var trackIndex by remember(plan) { mutableIntStateOf(0) }
    val status = trackPlan(plan, pose, geometry, trackIndex)
    SideEffect { trackIndex = status.index }

    // Guidance starts after arrival once the car is in R (reverse parking), and stays on to show "done".
    val inReverse = liveGear == Gear.R
    val guiding = arrived && guidanceAllowed && liveAngle != null && (inReverse || status.done)

    // Why guidance is off, in order. Shown to the driver (first one) and in the developer panel (all).
    val offReasons = buildList {
        if (!arrived) add("도착 이벤트를 받지 않았어요" + if (arrivalNote.isNotBlank()) " ($arrivalNote)" else "")
        if (!inReverse && !status.done) add("기어가 R이 아니에요 (지금 ${liveGear?.name ?: "정보 없음"})")
        if (!guidanceAllowed) add("실제 차량 신호라 검증 안 된 조향 안내를 끔")
        if (liveAngle == null) add("핸들 각도 신호가 없어요")
    }

    // One shared object for the screen, the gauge, the cameras and the voice.
    val instruction: SteeringInstruction? =
        if (guiding && liveAngle != null) {
            steeringInstruction(liveAngle, status.targetDeg, status.done, status.phase, inPark = liveGear == Gear.P)
        } else {
            null
        }
    // At the goal the only task is P, so the gauge stops showing a steering target.
    val gaugeTarget: Float? = instruction?.takeIf { !it.kind.isFinish }?.targetDeg

    // Smooth value for drawing only. Text and judgments use the raw value.
    val drawnAngle by animateFloatAsState(
        targetValue = liveAngle ?: 0f,
        animationSpec = tween(durationMillis = 180),
        label = "steering"
    )

    val latestAngle by rememberUpdatedState(liveAngle ?: 0f)
    val latestSpeed by rememberUpdatedState(liveSpeed)
    val latestGear by rememberUpdatedState(liveGear)
    val latestDone by rememberUpdatedState(status.done)
    val latestTarget by rememberUpdatedState(status.targetDeg)
    val latestGuidingTarget by rememberUpdatedState(if (guiding) status.targetDeg else null)
    val latestAutoSteer by rememberUpdatedState(autoSteer)
    val latestDevControls by rememberUpdatedState(devControlsWork)

    // Keep the FakeVehicleBus speed equal to what the simulation applies (0 unless RUNNING).
    fun syncBus() {
        if (!latestDevControls) return
        val snap = sim.snapshot()
        if (snap.state == SimState.RUNNING) {
            bus.gear.value = Gear.R
            speedMps.value = snap.appliedSpeedMps
        } else if (snap.state != SimState.IDLE) {
            speedMps.value = 0f
        }
    }

    fun refreshSim() {
        simSnap = sim.snapshot()
        syncBus()
    }

    // The only loop that moves the virtual car. Buttons change sim state directly and synchronously,
    // so pausing stops the very next tick, and nothing else can keep moving the car.
    LaunchedEffect(sim) {
        var last = System.nanoTime()
        while (isActive) {
            delay(33)
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now
            val before = sim.state
            sim.tick(
                dtSec = dt,
                steeringDeg = latestAngle,
                targetDeg = latestGuidingTarget,
                done = latestDone,
                // While developer controls drive the bus, read the gear from the bus itself so a fresh "start"
                // is not paused by a gear value that has not reached this screen yet.
                inReverse = if (latestDevControls) bus.gear.value == Gear.R else latestGear == Gear.R,
                externalSpeedMps = latestSpeed ?: 0f,
                externalDirection = when (latestGear) {
                    Gear.R -> -1
                    Gear.D -> 1
                    else -> 0
                },
            )
            // Automatic steering test: resume by itself once the wheel is on target.
            if (latestAutoSteer && sim.state == SimState.WAITING_STEER) {
                sim.resume(latestAngle, latestGuidingTarget)
            }
            simSnap = sim.snapshot()
            if (sim.state != before) syncBus()
        }
    }

    // Developer test only: turn the wheel on the bus toward the computed target, like a driver following the guide.
    LaunchedEffect(autoSteer, developerMode) {
        if (!autoSteer || !developerMode) return@LaunchedEffect
        while (isActive) {
            delay(33)
            val current = bus.steeringAngleDeg.value
            val stepDeg = 400f * 0.033f
            bus.steeringAngleDeg.value = (current + (latestTarget - current).coerceIn(-stepDeg, stepDeg))
                .coerceIn(-MAX_STEERING_WHEEL_DEG, MAX_STEERING_WHEEL_DEG)
        }
    }

    // Leaving developer mode or this screen always parks the fake car: sim paused, gear P, speed 0.
    // A test run writes R to FakeVehicleBus.gear; left there, Home stays in its driving view with no way out.
    fun parkFakeCar() {
        sim.pause("개발자 모드 종료로 멈춤")
        bus.gear.value = Gear.P
        speedMps.value = 0f
    }
    LaunchedEffect(developerMode) {
        if (!developerMode) {
            sim.pause("개발자 모드 종료로 멈춤")
            autoSteer = false
            refreshSim()
        }
    }
    DisposableEffect(Unit) {
        onDispose { parkFakeCar() }
    }

    // Voice: one speaker, one policy, the same instruction object the screen shows.
    var speechJob by remember { mutableStateOf<Job?>(null) }
    val say: (String) -> Unit = { text ->
        val previous = speechJob
        lastSpoken = text
        speechJob = scope.launch {
            if (previous?.isActive == true) {
                // Cancelling the previous speak() stops the engine in its finally block.
                previous.cancel()
                previous.join()
                // The engine reports onStop later; let it pass before the next sentence starts,
                // otherwise that late onStop fails the new sentence.
                delay(STOP_SETTLE_MS)
            }
            var retried = false
            while (true) {
                try {
                    graph.speechOutput.speak(text)
                    voiceError = ""
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!retried && e.message?.contains("stopped", ignoreCase = true) == true) {
                        retried = true
                        delay(STOP_SETTLE_MS)
                        continue
                    }
                    voiceError = "음성 출력 실패: " + (e.message ?: e.javaClass.simpleName)
                    break
                }
            }
        }
    }
    val latestSay by rememberUpdatedState(say)

    val policy = remember { VoiceGuidePolicy() }
    val latestGuiding by rememberUpdatedState(guiding)
    val latestInstruction by rememberUpdatedState(instruction)
    LaunchedEffect(policy) {
        while (isActive) {
            delay(200)
            policy.next(latestGuiding, latestInstruction, System.currentTimeMillis())
                ?.let { latestSay(it) }
        }
    }

    val safetyText = when {
        liveGear == Gear.R -> "후방과 주변 보행자를 확인하세요."
        liveGear == Gear.D -> "전방의 공간과 보행자를 확인하세요."
        else -> "주차 전 주변을 확인하세요."
    }

    val waitingText = when {
        !arrived && developerMode -> "'가상 도착'을 눌러 주차 안내를 시작하세요."
        !arrived -> "목적지에 도착하면 주차 안내가 시작돼요."
        liveAngle == null -> "핸들 각도 신호를 기다리는 중이에요."
        !guidanceAllowed -> "핸들 위치만 보여 드려요. 조작 안내는 실제 차량 검증 뒤에 켜져요."
        !inReverse -> "기어를 R(후진)로 바꾸면 주차 안내가 시작돼요."
        else -> ""
    }

    val sendArrival: () -> Unit = {
        // Same path as a real arrival: FakeVehicleBus → LocationSource → this screen.
        arrivalNote = if (bus.arrivals.tryEmit(DEV_ARRIVAL_NAME)) "보냄, 수신 대기" else "전송 실패"
    }

    // In developer mode the waiting card offers the one next step.
    val nextStep: Pair<String, () -> Unit>? = when {
        !developerMode -> null
        !arrived -> if (arrivalControlWorks) "가상 도착" to sendArrival else null
        !devControlsWork -> null
        !inReverse && !status.done -> "R(후진)로 바꾸기" to { bus.gear.value = Gear.R }
        else -> null
    }

    val canReplayVoice = liveSpeed != null && liveSpeed < 0.1f && liveGear == Gear.P

    val visual = ParkingVisualState(
        steeringDeg = liveAngle?.let { drawnAngle },
        plannedEdges = if (instruction != null && !status.done) plannedRearEdges(plan, status.index, geometry) else null,
        goalOutline = if (instruction != null) goalOutline(plan, geometry) else null,
        pose = pose,
        geometry = geometry,
        scene = scene,
    )

    val resetRun: () -> Unit = {
        sim.reset()
        if (devControlsWork) speedMps.value = 0f
        trackIndex = 0
        policy.reset()
        refreshSim()
    }

    val simBadge: String? = when (simSnap.state) {
        SimState.IDLE -> null
        SimState.RUNNING -> "가상 후진 중 · %.1f km/h".format(Locale.KOREA, simSnap.speedKmh)
        SimState.PAUSED -> "가상 후진 일시정지"
        SimState.WAITING_STEER -> "조향 대기 · 목표에 맞춘 뒤 재개"
        SimState.DONE -> "가상 후진 종료 · 주차 완료"
    }

    val todo: @Composable (Modifier) -> Unit = { m ->
        TodoCard(
            instruction = instruction,
            guidanceSimulated = guidanceSimulated,
            phase = status.phase,
            distanceToGoalM = status.distanceToGoalM,
            moving = moving,
            safetyText = safetyText,
            waitingText = waitingText,
            nextStep = nextStep,
            simBadge = if (developerMode) simBadge else null,
            destination = destination,
            parkSide = parkSide,
            onSide = {
                parkSide = it
                resetRun()
            },
            modifier = m
        )
    }

    val steering: @Composable (Modifier) -> Unit = { m ->
        SteeringGaugeBar(
            angleDeg = liveAngle,
            targetDeg = gaugeTarget,
            modifier = m,
            // Developer test only: drag the gauge to turn the wheel on FakeVehicleBus.
            onDrag = if (developerMode && devControlsWork && !autoSteer) {
                { deg -> bus.steeringAngleDeg.value = deg }
            } else {
                null
            }
        )
    }

    val footer: @Composable () -> Unit = {
        FooterRow(
            carDimensions = carDimensions,
            dimensionSource = dimensionSource,
            showReplay = !developerMode && instruction != null && canReplayVoice,
            voiceError = voiceError,
            onReplay = { instruction?.let { say(it.voiceText) } }
        )
    }

    val devStrip: @Composable () -> Unit = {
        if (developerMode) {
            DevStrip(
                compact = moving,
                enabled = devControlsWork,
                arrivalEnabled = arrivalControlWorks,
                expanded = devExpanded,
                onExpanded = { devExpanded = it },
                arrived = arrived,
                onToggleArrival = {
                    if (arrived) {
                        receivedArrival = false
                        destination = ""
                        resetRun()
                    } else {
                        sendArrival()
                    }
                },
                gear = liveGear,
                onGear = { bus.gear.value = it },
                angle = liveAngle ?: 0f,
                onAngle = { bus.steeringAngleDeg.value = it },
                autoSteer = autoSteer,
                onAutoSteer = { autoSteer = it },
                snap = simSnap,
                canResume = !simSnap.waitForSteer || instruction == null || instruction.onTarget,
                onStart = {
                    sim.start(if (guiding) status.targetDeg else null)
                    refreshSim()
                },
                onPause = {
                    sim.pause()
                    refreshSim()
                },
                onResume = {
                    sim.resume(liveAngle ?: 0f, if (guiding) status.targetDeg else null)
                    refreshSim()
                },
                onReset = resetRun,
                onNudge = { d ->
                    if (devControlsWork) bus.gear.value = Gear.R
                    sim.nudge(d, liveAngle ?: 0f)
                    refreshSim()
                },
                onSpeed = { kmh ->
                    sim.setSpeedKmh(kmh)
                    refreshSim()
                },
                onWaitForSteer = { on ->
                    sim.setWaitForSteer(on)
                    refreshSim()
                },
                liveText = "실제 신호 · 기어 ${liveGear?.name ?: "정보 없음"} (${gearSource?.name ?: "미수신"}) · " +
                        "핸들 ${liveAngle?.let { signedDegText(it) } ?: "정보 없음"} (${angleSource?.name ?: "미수신"}) · " +
                        "속도 ${liveSpeed?.let { "%.2f m/s".format(Locale.KOREA, it) } ?: "정보 없음"}",
                stateText = "주차 안내 ${if (instruction != null) "켜짐" else "꺼짐"} · " +
                        "도착 ${if (arrived) "예" else "아니오"} · 단계 ${status.phase.label} · " +
                        "계산된 목표 ${signedDegText(status.targetDeg)}" +
                        if (instruction == null && offReasons.isNotEmpty()) {
                            "\n꺼진 이유: " + offReasons.joinToString(" · ")
                        } else {
                            ""
                        },
                lastSpoken = lastSpoken,
                onReplayGuide = { say(instruction?.voiceText ?: VOICE_START_TEXT) },
                onVoiceTest = { say("음성 테스트입니다. 소리가 들리나요?") }
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
    ) {
        val wide = maxWidth >= WideBreakpoint

        // No page scroll: the developer strip is pinned at the bottom and the cameras take what is left.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(
                developerMode = developerMode,
                onToggleDeveloper = {
                    if (developerMode) parkFakeCar()
                    developerMode = !developerMode
                },
                notice = when {
                    developerMode -> "개발자 테스트 · 가상 데이터"
                    simulatedSignals -> "데모 데이터 · 실제 차량 상태가 아닙니다"
                    else -> null
                }
            )

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    todo(Modifier.weight(1.25f).fillMaxHeight())
                    steering(Modifier.weight(1f).fillMaxHeight())
                }

                CameraPanel(
                    gear = liveGear,
                    state = visual,
                    wide = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                footer()
            } else {
                // Narrow window: the guidance scrolls, the developer strip stays put below it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    todo(Modifier.fillMaxWidth())
                    steering(Modifier.fillMaxWidth())
                    CameraPanel(
                        gear = liveGear,
                        state = visual,
                        wide = false,
                        modifier = Modifier.fillMaxWidth().height(520.dp)
                    )
                    footer()
                }
            }

            devStrip()
        }
    }
}

@Composable
private fun Header(
    developerMode: Boolean,
    onToggleDeveloper: () -> Unit,
    notice: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "스마트 주차 코치",
            color = Navy,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        if (notice != null) {
            Surface(color = Navy, shape = RoundedCornerShape(10.dp)) {
                Text(
                    text = notice,
                    color = White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onToggleDeveloper,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (developerMode) "운전자 화면" else "개발자 모드",
                color = Navy,
                fontSize = 20.sp
            )
        }
    }
}

/** 가장 눈에 띄어야 하는 카드: 지금 할 일 한 문장과 현재/목표/남은 조작량. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoCard(
    instruction: SteeringInstruction?,
    guidanceSimulated: Boolean,
    phase: ParkPhase,
    distanceToGoalM: Float,
    moving: Boolean,
    safetyText: String,
    waitingText: String,
    nextStep: Pair<String, () -> Unit>?,
    simBadge: String?,
    destination: String,
    parkSide: ParkSide,
    onSide: (ParkSide) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlight = instruction != null &&
            (instruction.onTarget || instruction.kind.isFinish)

    Surface(
        modifier = modifier,
        color = White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(3.dp, if (highlight) GaugeOnTarget else Navy)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        instruction == null -> "주차 안내 대기"
                        instruction.kind == InstructionKind.PARKED -> "주차 완료"
                        instruction.kind == InstructionKind.ARRIVED_SHIFT_P -> "지금 할 일 · 도착 · 기어 P로"
                        else -> "지금 할 일 · ${phase.stepNo}/3 ${phase.label}"
                    },
                    color = Navy,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (simBadge != null) {
                    Surface(color = Navy, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = simBadge,
                            color = White,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (instruction != null && guidanceSimulated) {
                    Surface(color = DevBadgeBg, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "시뮬레이션 안내",
                            color = DevBadgeText,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (instruction == null) {
                Text(
                    text = waitingText,
                    color = Black,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (nextStep != null) {
                    Button(
                        onClick = nextStep.second,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = White),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(text = nextStep.first, fontSize = 22.sp)
                    }
                }
                return@Column
            }

            // ① The one action a beginner needs right now.
            Text(
                text = instruction.headline,
                color = if (highlight) GaugeOnTarget else Black,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold
            )

            // ② Current, target and remaining, updated as the wheel and the car move.
            //    Not needed once the car is at the goal: the only task left is P.
            if (!instruction.kind.isFinish) {
                ValueLine("현재", steeringWithDegText(instruction.currentDeg))
                ValueLine("목표", steeringWithDegText(instruction.targetDeg))
                ValueLine("남은 조작량", remainingText(instruction.remainingDeg))
                ValueLine("목표 위치까지", "%.1f m".format(Locale.KOREA, distanceToGoalM))
            }

            // No setup while the car is moving: keep only the key guidance.
            if (!moving) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(ParkSide.LEFT to "왼쪽 칸", ParkSide.RIGHT to "오른쪽 칸").forEach { (value, label) ->
                        StepChip(label, parkSide == value) { onSide(value) }
                    }
                }
            }

            Text(text = safetyText, color = Navy, fontSize = 20.sp)

            if (destination.isNotBlank()) {
                Text(text = "도착: $destination", color = Navy, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Navy,
            fontSize = 20.sp,
            modifier = Modifier.width(150.dp)
        )
        Text(text = value, color = Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Navy else Sand,
            contentColor = if (selected) White else Navy
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text = label, fontSize = 20.sp)
    }
}

@Composable
private fun FooterRow(
    carDimensions: String,
    dimensionSource: String,
    showReplay: Boolean,
    voiceError: String,
    onReplay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "내 차 $carDimensions", color = Black, fontSize = 20.sp)
            Text(
                text = "정보 출처: $dimensionSource · 경로는 휠베이스·조향비 가정값으로 그린 시뮬레이션",
                color = Navy,
                fontSize = 18.sp
            )
            if (voiceError.isNotBlank()) {
                Text(text = voiceError, color = Navy, fontSize = 18.sp)
            }
        }

        if (showReplay) {
            Button(
                onClick = onReplay,
                colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = White),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(text = "안내 다시 듣기", fontSize = 23.sp)
            }
        }
    }
}

/**
 * 화면 아래에 고정된 개발자 테스트 영역(두 줄).
 *
 * 1줄: 도착 · 기어 · 가상 후진(상태에 맞는 버튼만) · 더보기
 * 2줄: 핸들 슬라이더(넓게) · 후진 속도 슬라이더
 * 더보기: 조향 모드, "목표 조향각 도달 후 이동", 음성, 신호·상태 정보
 *
 * 모든 조작은 FakeVehicleBus에 씁니다. 움직이는 중([compact])에는 1줄에 가상 후진 버튼만 남기고,
 * 핸들 슬라이더는 계속 보여 줍니다(후진하면서 핸들을 돌리므로).
 * 속도는 에뮬레이터 관찰용 시뮬레이션 값이며 실제 주차 권장 속도가 아닙니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevStrip(
    compact: Boolean,
    enabled: Boolean,
    arrivalEnabled: Boolean,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    arrived: Boolean,
    onToggleArrival: () -> Unit,
    gear: Gear?,
    onGear: (Gear) -> Unit,
    angle: Float,
    onAngle: (Float) -> Unit,
    autoSteer: Boolean,
    onAutoSteer: (Boolean) -> Unit,
    snap: SimSnapshot,
    canResume: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onNudge: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
    onWaitForSteer: (Boolean) -> Unit,
    liveText: String,
    stateText: String,
    lastSpoken: String,
    onReplayGuide: () -> Unit,
    onVoiceTest: () -> Unit
) {
    val stateLabel = when (snap.state) {
        SimState.IDLE -> "대기"
        SimState.RUNNING -> "진행 중"
        SimState.PAUSED -> "일시정지"
        SimState.WAITING_STEER -> "조향 대기"
        SimState.DONE -> "완료"
    }

    Surface(
        color = White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, DevBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: buttons.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = DevBadgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "개발자 · 가상 후진 $stateLabel",
                        color = DevBadgeText,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (!compact) {
                    SimButton(if (arrived) "도착 해제" else "가상 도착", arrivalEnabled, onClick = onToggleArrival)
                    listOf(Gear.P, Gear.R, Gear.N, Gear.D).forEach { g ->
                        StripChip(g.name, gear == g, enabled) { onGear(g) }
                    }
                }

                when (snap.state) {
                    SimState.RUNNING -> {
                        Button(
                            onClick = onPause,
                            enabled = enabled,
                            modifier = Modifier.height(56.dp).widthIn(min = 200.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = White)
                        ) {
                            Text("일시정지", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    SimState.IDLE -> {
                        SimButton("가상 후진 시작", enabled, primary = true, onClick = onStart)
                        SimButton("0.1m", enabled) { onNudge(0.1f) }
                        SimButton("0.5m", enabled) { onNudge(0.5f) }
                    }

                    SimState.PAUSED, SimState.WAITING_STEER -> {
                        SimButton("재개", enabled && canResume, primary = true, onClick = onResume)
                        SimButton("0.1m", enabled) { onNudge(0.1f) }
                        SimButton("0.5m", enabled) { onNudge(0.5f) }
                        SimButton("처음 위치", true, onClick = onReset)
                    }

                    SimState.DONE -> {
                        SimButton("처음 위치", true, primary = true, onClick = onReset)
                    }
                }

                if (!compact) {
                    SimButton(if (expanded) "접기" else "더보기", true) { onExpanded(!expanded) }
                }
            }

            if (snap.state == SimState.WAITING_STEER && !canResume) {
                Text(
                    text = "핸들을 목표 구간에 맞추면 재개할 수 있어요.",
                    color = DevBadgeText,
                    fontSize = 18.sp
                )
            }

            // Row 2: steering (wide) and speed.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "핸들 ${signedDegText(angle)}",
                    color = Navy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(130.dp)
                )
                Slider(
                    value = angle,
                    onValueChange = { onAngle((it / 10f).roundToInt() * 10f) },
                    enabled = enabled && !autoSteer,
                    valueRange = -MAX_STEERING_WHEEL_DEG..MAX_STEERING_WHEEL_DEG,
                    modifier = Modifier.weight(3f),
                    colors = SliderDefaults.colors(
                        thumbColor = Navy,
                        activeTrackColor = Navy,
                        inactiveTrackColor = Sand
                    )
                )
                Text(
                    text = "속도 %.1f km/h".format(Locale.KOREA, snap.speedKmh),
                    color = Navy,
                    fontSize = 18.sp
                )
                Slider(
                    value = snap.speedKmh,
                    onValueChange = { onSpeed((it * 10f).roundToInt() / 10f) },
                    enabled = enabled,
                    valueRange = SIM_MIN_SPEED_KMH..SIM_MAX_SPEED_KMH,
                    steps = 18,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Navy,
                        activeTrackColor = Navy,
                        inactiveTrackColor = Sand
                    )
                )
            }

            if (autoSteer) {
                Text(
                    text = "자동 조향 중이라 핸들 슬라이더는 잠겨 있어요. 더보기에서 수동 조향으로 바꿀 수 있어요.",
                    color = DevBadgeText,
                    fontSize = 16.sp
                )
            }

            if (expanded && !compact) {
                HorizontalDivider(color = Sand)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StripChip("수동 조향", !autoSteer, enabled) { onAutoSteer(false) }
                    StripChip("자동 조향(안내대로)", autoSteer, enabled) { onAutoSteer(true) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = snap.waitForSteer, onCheckedChange = onWaitForSteer, enabled = enabled)
                        Text("  목표 조향각 도달 후 이동", color = Black, fontSize = 18.sp)
                    }
                    SimButton("안내 다시 듣기", true, onClick = onReplayGuide)
                    SimButton("음성 테스트(TTS)", true, onClick = onVoiceTest)
                }

                if (!arrivalEnabled) {
                    Text(
                        text = "위치·도착 포트를 가짜로 바꾸면 쓸 수 있어요",
                        color = DevBadgeText,
                        fontSize = 16.sp
                    )
                }
                if (!enabled) {
                    Text(
                        text = "차량 신호가 FakeVehicleBus가 아니라서 개발자 조작이 반영되지 않아요.",
                        color = DevBadgeText,
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = "가상 후진 $stateLabel · 설정 %.1f km/h (%.2f m/s) · 적용 %.2f m/s · 이동 %.2f m".format(
                        Locale.KOREA,
                        snap.speedKmh,
                        kmhToMps(snap.speedKmh),
                        snap.appliedSpeedMps,
                        snap.travelledM
                    ) + if (snap.note.isNotBlank()) " · ${snap.note}" else "",
                    color = Black,
                    fontSize = 16.sp
                )
                Text(text = liveText, color = Black, fontSize = 16.sp)
                Text(text = stateText, color = Black, fontSize = 16.sp)
                Text(text = "마지막 음성: ${lastSpoken.ifBlank { "없음" }}", color = Navy, fontSize = 16.sp)
                Text(
                    text = "속도는 에뮬레이터 관찰용 시뮬레이션 값이며 실제 주차 권장 속도가 아닙니다.",
                    color = Navy,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StripChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Navy else Sand,
            contentColor = if (selected) White else Navy
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Text(text = label, fontSize = 19.sp)
    }
}

@Composable
private fun SimButton(
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = White),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(label, color = Navy, fontSize = 19.sp)
        }
    }
}

/** The developer "가상 도착" reaches this screen only while the LOCATION port is the fake one. */
internal fun arrivalControlWorks(locationMode: PortMode): Boolean = locationMode == PortMode.FAKE
