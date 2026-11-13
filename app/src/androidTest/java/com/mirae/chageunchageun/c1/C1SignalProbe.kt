package com.mirae.chageunchageun.c1

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatAirbagEnabledListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mirae.chageunchageun.runtime.ChageunApp
import com.mirae.chageunchageun.runtime.PortId
import com.mirae.chageunchageun.runtime.PortMode
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C1 Step 1 probe: which Pleos Safety signals reach the app on the emulator.
 * Lives in androidTest, so it is in no app build. It asserts nothing; read logcat tag C1Probe.
 *
 *   adb shell am instrument -w -e class com.mirae.chageunchageun.c1.C1SignalProbe -e seconds 120 \
 *       com.mirae.chageunchageun.test/androidx.test.runner.AndroidJUnitRunner
 *
 * While it runs, change telltales and gear with tools/console/console.py.
 * SDK signatures were read from Vehicle-2.3.2.aar (api.Safety).
 */
@RunWith(AndroidJUnit4::class)
class C1SignalProbe {

    private fun log(line: String) {
        Log.i("C1Probe", line)
    }

    private val fail: (String) -> (Exception) -> Unit = { name -> { e -> log("$name FAILED ${e.javaClass.simpleName}: ${e.message}") } }

    /** Runs one SDK call; a thrown exception (for example SecurityException) is logged, not fatal. */
    private fun call(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            log("$name THREW ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun Telltales.active(): String =
        telltales.filter { it.state != TelltaleState.OFF }
            .joinToString { "${it.type}=${it.state}(color=${it.color},prio=${it.priority},count=${it.warningCount})" }
            .ifEmpty { "none" }

    private fun SeatArea.name(): String = values.joinToString("+")

    @Test
    fun probe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val seconds = InstrumentationRegistry.getArguments().getString("seconds")?.toLongOrNull() ?: 120L
        val graph = (context.applicationContext as ChageunApp).graph
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val unregister = mutableListOf<() -> Unit>()
        val lastPolled = ConcurrentHashMap<String, String>()

        log("=== start, running ${seconds}s, package=${context.packageName}")
        for (p in listOf("pleos.car.permission.CAR_SAFETY", "pleos.car.permission.PRIVATE_CAR_SAFETY", "android.permission.POST_NOTIFICATIONS")) {
            log("permission $p granted=${context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED}")
        }

        // 1. Drive state through the existing real adapter.
        graph.portModes.set(PortId.DRIVE_STATE, PortMode.REAL)
        scope.launch { graph.driveState.gear.collect { log("driveState.gear $it") } }
        scope.launch { graph.driveState.uxRestricted.collect { log("driveState.uxRestricted $it") } }
        scope.launch { graph.driveRuntime.canShowDetail.collect { log("driveRuntime.canShowDetail $it") } }

        // 2. Safety warnings.
        val safety = graph.vehicleSession.vehicle().getSafety()

        call("checkTelltalesCapabilities") {
            safety.checkTelltalesCapabilities({ log("checkTelltalesCapabilities OK $it") }, fail("checkTelltalesCapabilities"))
        }
        call("getTelltalesInfo") {
            safety.getTelltalesInfo(
                { log("getTelltalesInfo OK size=${it.telltales.size} types=${it.telltales.map { t -> t.type }} active=${it.active()}") },
                fail("getTelltalesInfo"),
            )
        }
        val telltaleListener = object : TelltaleInfoListener {
            override fun onFailed(e: Exception) = log("TelltaleInfoListener onFailed ${e.javaClass.simpleName}: ${e.message}")
            override fun onTelltaleInfoUpdated(telltales: Telltales?) {
                log("registerTelltales CALLBACK size=${telltales?.telltales?.size} active=${telltales?.active()}")
            }
        }
        call("registerTelltales") {
            safety.registerTelltales(telltaleListener)
            log("registerTelltales returned without exception")
            unregister += { safety.unregisterTelltales(telltaleListener) }
        }

        call("powerDown") {
            safety.checkPowerDownWarningIndicatorCapabilities({ log("checkPowerDownWarningIndicatorCapabilities OK $it") }, fail("checkPowerDownWarningIndicatorCapabilities"))
            safety.hasPowerDownWarningIndicator({ log("hasPowerDownWarningIndicator OK $it") }, fail("hasPowerDownWarningIndicator"))
            val l = object : PowerDownWarningIndicatorListener {
                override fun onFailed(e: Exception) = log("PowerDownWarningIndicatorListener onFailed ${e.javaClass.simpleName}: ${e.message}")
                override fun onPowerDownWarningIndicatorUpdated(value: Boolean?) = log("registerPowerDownWarningIndicator CALLBACK $value")
            }
            safety.registerPowerDownWarningIndicator(l)
            unregister += { safety.unregisterPowerDownWarningIndicator(l) }
        }

        val beltAreas = mutableListOf<SeatArea>()
        call("seatBelt") {
            safety.checkSeatNotBuckledWarningCapabilities({ config, areas ->
                log("checkSeatNotBuckledWarningCapabilities OK $config areas=${areas.map { it.name() }}")
                areas.forEach { area ->
                    call("seatBelt ${area.name()}") {
                        safety.hasSeatBeltNotBuckledWarning(area, { log("hasSeatBeltNotBuckledWarning[${area.name()}] OK $it") }, fail("hasSeatBeltNotBuckledWarning[${area.name()}]"))
                        val l = object : SeatBeltNotBuckledWarningListener {
                            override fun onFailed(e: Exception) = log("SeatBeltNotBuckledWarningListener onFailed ${e.javaClass.simpleName}: ${e.message}")
                            override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) = log("registerSeatBeltNotBuckledWarning[${area.name()}] CALLBACK $value")
                        }
                        safety.registerSeatBeltNotBuckledWarning(area, l)
                        synchronized(unregister) { unregister += { safety.unregisterSeatBeltNotBuckledWarning(area, l) } }
                        synchronized(beltAreas) { beltAreas += area }
                    }
                }
            }, fail("checkSeatNotBuckledWarningCapabilities"))
        }

        val airbagAreas = mutableListOf<SeatArea>()
        call("airbag") {
            safety.checkAirbagCapabilities({ config, areas ->
                log("checkAirbagCapabilities OK $config areas=${areas.map { it.name() }}")
                areas.forEach { area ->
                    call("airbag ${area.name()}") {
                        safety.hasSeatAirbagEnabled(area, { log("hasSeatAirbagEnabled[${area.name()}] OK $it") }, fail("hasSeatAirbagEnabled[${area.name()}]"))
                        val l = object : SeatAirbagEnabledListener {
                            override fun onFailed(e: Exception) = log("SeatAirbagEnabledListener onFailed ${e.javaClass.simpleName}: ${e.message}")
                            override fun onSeatAirbagEnabledUpdated(value: Boolean?) = log("registerSeatAirbagEnabled[${area.name()}] CALLBACK $value")
                        }
                        safety.registerSeatAirbagEnabled(area, l)
                        synchronized(unregister) { unregister += { safety.unregisterSeatAirbagEnabled(area, l) } }
                        synchronized(airbagAreas) { airbagAreas += area }
                    }
                }
            }, fail("checkAirbagCapabilities"))
            safety.checkAirbagDeployedCapabilities({ config, areas ->
                log("checkAirbagDeployedCapabilities OK $config areas=${areas.map { it.name() }}")
                areas.forEach { area ->
                    call("airbagDeployed ${area.name()}") {
                        safety.hasSeatAirbagBeenDeployed(area, { log("hasSeatAirbagBeenDeployed[${area.name()}] OK $it") }, fail("hasSeatAirbagBeenDeployed[${area.name()}]"))
                        val l = object : SeatAirbagDeployedListener {
                            override fun onFailed(e: Exception) = log("SeatAirbagDeployedListener onFailed ${e.javaClass.simpleName}: ${e.message}")
                            override fun onSeatAirbagDeployUpdated(value: Boolean?) = log("registerSeatAirbagDeployed[${area.name()}] CALLBACK $value")
                        }
                        safety.registerSeatAirbagDeployed(area, l)
                        synchronized(unregister) { unregister += { safety.unregisterSeatAirbagDeployed(area, l) } }
                    }
                }
            }, fail("checkAirbagDeployedCapabilities"))
        }

        fun oilListener(name: String) = object : OilLevelStateListener {
            override fun onFailed(e: Exception) = log("OilLevelStateListener onFailed ${e.javaClass.simpleName}: ${e.message}")
            override fun onOilLevelUpdated(state: OilLevelState?) = log("$name CALLBACK $state")
        }
        call("engineOil") {
            safety.checkEngineOilLevelStateCapabilities({ c, states -> log("checkEngineOilLevelStateCapabilities OK $c $states") }, fail("checkEngineOilLevelStateCapabilities"))
            safety.getEngineOilLevelState({ log("getEngineOilLevelState OK $it") }, fail("getEngineOilLevelState"))
            val l = oilListener("registerEngineOilLevelState")
            safety.registerEngineOilLevelState(l)
            unregister += { safety.unregisterEngineOilLevelState(l) }
        }
        call("brakeFluid") {
            safety.checkBrakeFluidLevelStateCapabilities({ c, states -> log("checkBrakeFluidLevelStateCapabilities OK $c $states") }, fail("checkBrakeFluidLevelStateCapabilities"))
            safety.getBrakeFluidLevelState({ log("getBrakeFluidLevelState OK $it") }, fail("getBrakeFluidLevelState"))
            val l = oilListener("registerBrakeFluidLevelState")
            safety.registerBrakeFluidLevelState(l)
            unregister += { safety.unregisterBrakeFluidLevelState(l) }
        }
        call("washerFluid") {
            safety.checkLowWasherFluidWarningStateCapabilities({ log("checkLowWasherFluidWarningStateCapabilities OK $it") }, fail("checkLowWasherFluidWarningStateCapabilities"))
            safety.hasLowWasherFluidWarning({ log("hasLowWasherFluidWarning OK $it") }, fail("hasLowWasherFluidWarning"))
            val l = object : LowWasherFluidWaringListener {
                override fun onFailed(e: Exception) = log("LowWasherFluidWaringListener onFailed ${e.javaClass.simpleName}: ${e.message}")
                override fun onLowWasherFluidWaringUpdated(value: Boolean?) = log("registerLowWasherFluidWaringState CALLBACK $value")
            }
            safety.registerLowWasherFluidWaringState(l)
            unregister += { safety.unregisterLowWasherFluidWaringState(l) }
        }

        // 3. Poll once per second and log only changes, to measure delay and to catch getters whose listeners stay silent.
        fun polled(name: String, value: String) {
            if (lastPolled.put(name, value) != value) log("POLL $name -> $value")
        }
        val end = System.currentTimeMillis() + seconds * 1000
        while (System.currentTimeMillis() < end) {
            call("poll") {
                safety.getTelltalesInfo({ polled("getTelltalesInfo", it.active()) }, { polled("getTelltalesInfo", "FAILED ${it.message}") })
                safety.hasPowerDownWarningIndicator({ polled("hasPowerDownWarningIndicator", "$it") }, { polled("hasPowerDownWarningIndicator", "FAILED ${it.message}") })
                safety.getEngineOilLevelState({ polled("getEngineOilLevelState", "$it") }, { polled("getEngineOilLevelState", "FAILED ${it.message}") })
                synchronized(beltAreas) { beltAreas.toList() }.forEach { a ->
                    safety.hasSeatBeltNotBuckledWarning(a, { polled("hasSeatBeltNotBuckledWarning[${a.name()}]", "$it") }, { polled("hasSeatBeltNotBuckledWarning[${a.name()}]", "FAILED ${it.message}") })
                }
                synchronized(airbagAreas) { airbagAreas.toList() }.forEach { a ->
                    safety.hasSeatAirbagEnabled(a, { polled("hasSeatAirbagEnabled[${a.name()}]", "$it") }, { polled("hasSeatAirbagEnabled[${a.name()}]", "FAILED ${it.message}") })
                }
            }
            Thread.sleep(1000)
        }

        synchronized(unregister) { unregister.toList() }.forEach { runCatching(it) }
        scope.cancel()
        graph.portModes.set(PortId.DRIVE_STATE, PortMode.FAKE)
        log("=== end")
    }
}
