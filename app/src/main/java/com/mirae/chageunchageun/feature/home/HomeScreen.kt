package com.mirae.chageunchageun.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.R
import com.mirae.chageunchageun.core.model.LampState
import com.mirae.chageunchageun.core.model.Severity
import com.mirae.chageunchageun.core.model.WarningType
import com.mirae.chageunchageun.domain.guidance.WarningGuidance
import com.mirae.chageunchageun.domain.guidance.WarningGuide
import com.mirae.chageunchageun.feature.onboarding.label
import com.mirae.chageunchageun.feature.warning.severityColors
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.runtime.AppScreen
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.common.label
import com.mirae.chageunchageun.ui.theme.AppColors

@Composable
fun HomeScreen(graph: AppGraph) {
    val vm: HomeViewModel = viewModel {
        HomeViewModel(graph.driveState, graph.controls, graph.driveRuntime, graph.warningSource, graph.userSettings)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val open: (AppScreen) -> Unit = { graph.screenRequests.tryEmit(it) }

    CarScale {
        if (ui.canShowDetail) ParkedHome(ui, open) else DrivingHome(ui)
    }
}

/** Driving: title, one static sentence, and the active warning if any. No buttons, no scrolling. */
@Composable
private fun DrivingHome(ui: HomeUi) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("주행 중", style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.home_driving_notice).keepWords(), style = MaterialTheme.typography.headlineMedium)
        ui.drivingWarning?.let { DrivingWarningCard(it, others = ui.warnings.size - 1) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParkedHome(ui: HomeUi, open: (AppScreen) -> Unit) {
    // The Android permission is not stored; read it each time Home opens.
    val context = LocalContext.current
    val permissionGranted = remember {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("정차 중", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.home_level_label, stringResource(ui.level.label())),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (!ui.firstRunCompleted) {
                ActionCard(stringResource(R.string.home_first_run_title)) {
                    GuideButton(stringResource(R.string.home_start), { open(AppScreen.ONBOARDING) })
                }
            }

            ui.parkedWarning?.let { ParkedWarningCard(it, others = ui.warnings.size - 1) { open(AppScreen.WARNING) } }

            val mirror: @Composable (Modifier) -> Unit = { MirrorCard(it) { open(AppScreen.MIRROR) } }
            val practice: @Composable (Modifier) -> Unit = { modifier ->
                ActionCard(stringResource(R.string.home_practice_title), modifier) {
                    GuideOutlinedButton(stringResource(R.string.home_practice_button), { open(AppScreen.PRACTICE) })
                }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    mirror(Modifier.weight(1f).fillMaxHeight())
                    practice(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                mirror(Modifier.fillMaxWidth())
                practice(Modifier.fillMaxWidth())
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoCard("기어", ui.gear?.name ?: "-", ui.gearSource.label())
                HighBeamCard(ui)
            }

            SettingsCard(ui, permissionGranted) { open(AppScreen.ONBOARDING) }
        }
    }
}

/** A card with one line of text and one action. */
@Composable
private fun ActionCard(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title.keepWords(), style = MaterialTheme.typography.headlineSmall)
            action()
        }
    }
}

/** The main action before departure: primary background, onPrimary text and button. */
@Composable
private fun MirrorCard(modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(modifier, colors = CardDefaults.cardColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.home_mirror_title).keepWords(), style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick,
                Modifier.height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
            ) {
                Text(stringResource(R.string.home_mirror_button), fontSize = 24.sp, maxLines = 1)
            }
        }
    }
}

/** Parked: the most severe active warning, with the telltale drawn straight on the card. */
@Composable
private fun ParkedWarningCard(guide: WarningGuide, others: Int, onDetail: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Telltale(guide)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_warning_summary, guide.title, WarningGuidance.severityLabel(guide.severity)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (others > 0) {
                    Text(stringResource(R.string.home_warning_more, others), style = MaterialTheme.typography.titleMedium)
                }
            }
            GuideOutlinedButton(stringResource(R.string.home_warning_detail), onDetail)
        }
    }
}

/** Driving: C1's banner words (severity label, warning name, count of others) plus its one driving-time sentence. Nothing to touch. */
@Composable
private fun DrivingWarningCard(guide: WarningGuide, others: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Telltale(guide)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_warning_severity, WarningGuidance.severityLabel(guide.severity)),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(guide.title, style = MaterialTheme.typography.headlineMedium)
                Text(guide.driveAction.keepWords(), style = MaterialTheme.typography.titleLarge)
            }
            if (others > 0) {
                Text(stringResource(R.string.home_warning_more, others), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * Warning light on the card itself. Caution and danger use the same color as C1's screen
 * (severityColors in WarningScreen.kt).
 * Only tire pressure has a telltale drawable; the rest get a warning sign.
 */
@Composable
private fun Telltale(guide: WarningGuide) {
    val description = stringResource(R.string.home_warning_icon, guide.title)
    // ActiveBlue is never an icon or text color on a light background, so INFO uses primary here.
    // TODO(c1): C1's severityColors maps INFO to ActiveBlue; its own screen has the same issue.
    val tint = if (guide.severity == Severity.INFO) MaterialTheme.colorScheme.primary else severityColors(guide.severity).first
    if (guide.type == WarningType.TIRE_PRESSURE) {
        Icon(painterResource(R.drawable.ic_telltale_tpms), description, Modifier.size(56.dp), tint)
    } else {
        Text(
            "⚠",
            color = tint,
            fontSize = 48.sp,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun SettingsCard(ui: HomeUi, permissionGranted: Boolean, onChange: () -> Unit) {
    val location = when (locationStatus(ui.consent, permissionGranted)) {
        LocationStatus.ON -> R.string.onboarding_location_on
        LocationStatus.OFF -> R.string.onboarding_location_off
        LocationStatus.PERMISSION_OFF -> R.string.onboarding_location_permission_off
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.onboarding_summary_location, stringResource(location)),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.onboarding_summary_level, stringResource(ui.level.label())),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            GuideOutlinedButton(stringResource(R.string.home_settings_change), onChange)
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, source: String, highlight: Boolean = false) {
    Card(
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = AppColors.ActiveBlue, contentColor = AppColors.Black)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineLarge)
            if (source.isNotEmpty()) Text(source, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HighBeamCard(ui: HomeUi) {
    val text = when (ui.highBeam) {
        LampState.ON -> "켜짐"
        LampState.DAYTIME_RUNNING -> "주간주행등"
        LampState.OFF -> "꺼짐"
        null -> "-"
    }
    InfoCard("상향등", text, ui.highBeamSource.label(), highlight = ui.highBeam == LampState.ON)
}
