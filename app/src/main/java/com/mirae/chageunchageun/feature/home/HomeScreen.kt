package com.mirae.chageunchageun.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.BuildConfig
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
import com.mirae.chageunchageun.ui.common.keepWords
import com.mirae.chageunchageun.ui.common.label

private val CardShape = RoundedCornerShape(24.dp)
private val Gap = 16.dp

/**
 * Home. Parked: a hub that leads to every feature. Driving: one sentence and the active warning, nothing to touch.
 * Still the port → ViewModel → screen example: the gear and high-beam values in "지금 내 차" come from [HomeViewModel].
 */
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
        Text(stringResource(R.string.home_driving_title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.home_driving_notice).keepWords(), style = MaterialTheme.typography.headlineMedium)
        ui.drivingWarning?.let { DrivingWarningCard(it, others = ui.warnings.size - 1) }
    }
}

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
        // Scrolling is allowed only here, while parked. In landscape everything is meant to fit without it.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Header(ui)

            // Row 1: hero (2) and "지금 내 차" (1).
            if (wide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    HeroCard(ui.hero, open, Modifier.weight(2f).fillMaxHeight())
                    CarCard(ui, open, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                HeroCard(ui.hero, open, Modifier.fillMaxWidth())
                CarCard(ui, open, Modifier.fillMaxWidth())
            }

            // Row 2: three features.
            val features: @Composable (Modifier) -> Unit = { item ->
                FeatureCard(
                    R.drawable.ic_feature_parking, R.string.home_feature_parking, R.string.home_feature_parking_desc, item,
                ) { open(AppScreen.PARKING) }
                FeatureCard(
                    R.drawable.ic_feature_warning, R.string.home_feature_warning, R.string.home_feature_warning_desc, item,
                ) { open(AppScreen.WARNING) }
                FeatureCard(
                    R.drawable.ic_feature_practice, R.string.home_feature_practice, R.string.home_feature_practice_desc, item,
                ) { open(AppScreen.PRACTICE) }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    features(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                features(Modifier.fillMaxWidth())
            }

            // Row 3: settings, and developer tools in debug builds.
            PillRow(ui, permissionGranted, open)
        }
    }
}

@Composable
private fun Header(ui: HomeUi) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Same 1dp outline as on the first-run screen: the mark's tile is the light background color.
        Image(
            painterResource(R.drawable.ic_brand_road),
            contentDescription = null, // the app name is right next to it
            modifier = Modifier
                .size(52.dp)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.home_tagline), fontSize = 16.sp, maxLines = 1)
        }
        Chip(stringResource(R.string.home_chip_parked))
        Chip(stringResource(R.string.home_level_label, stringResource(ui.level.label())))
    }
}

/** Status label. Not clickable. */
@Composable
private fun Chip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** The one main action: finish setup first, then the mirror guide. Primary background, onPrimary text and button. */
@Composable
private fun HeroCard(hero: HomeHero, open: (AppScreen) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (hero) {
                HomeHero.SETUP -> {
                    Text(
                        stringResource(R.string.home_first_run_title).keepWords(),
                        fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                    )
                    HeroButton(stringResource(R.string.home_start)) { open(AppScreen.ONBOARDING) }
                }
                HomeHero.MIRROR -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_feature_mirror), contentDescription = null, Modifier.size(24.dp))
                        Text(stringResource(R.string.home_hero_label), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.home_hero_title).keepWords(),
                        fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.home_hero_subtitle).keepWords(), fontSize = 18.sp, lineHeight = 26.sp)
                    HeroButton(stringResource(R.string.home_mirror_button)) { open(AppScreen.MIRROR) }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick,
        Modifier.padding(top = 12.dp).height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** "지금 내 차": the example gear and high-beam values, then the active warning or "none". */
@Composable
private fun CarCard(ui: HomeUi, open: (AppScreen) -> Unit, modifier: Modifier) {
    Card(modifier, shape = CardShape) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_car_title),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CarValue(
                    stringResource(R.string.home_car_gear),
                    ui.gear?.name ?: stringResource(R.string.home_car_unknown),
                    ui.gearSource.label(),
                )
                CarValue(
                    stringResource(R.string.home_car_high_beam),
                    stringResource(
                        when (ui.highBeam) {
                            LampState.ON -> R.string.home_car_on
                            LampState.DAYTIME_RUNNING -> R.string.home_car_drl
                            LampState.OFF -> R.string.home_car_off
                            null -> R.string.home_car_unknown
                        },
                    ),
                    ui.highBeamSource.label(),
                    highlight = ui.highBeam == LampState.ON,
                )
            }
            val warning = ui.parkedWarning
            if (warning == null) {
                Row(
                    Modifier.heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, Modifier.size(28.dp))
                    Text(stringResource(R.string.home_no_warning), fontSize = 18.sp)
                }
            } else {
                WarningRow(warning, others = ui.warnings.size - 1) { open(AppScreen.WARNING) }
            }
        }
    }
}

/** One vehicle value with where it came from. High beam on is marked by the accent background plus the word 켜짐. */
@Composable
private fun CarValue(title: String, value: String, source: String, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) scheme.secondary else scheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val color = if (highlight) scheme.onSecondary else scheme.onBackground
        Text(title, fontSize = 14.sp, color = color)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(source, fontSize = 12.sp, color = color)
    }
}

/** The most severe active warning. The whole row opens the warning guide. */
@Composable
private fun WarningRow(guide: WarningGuide, others: Int, onClick: () -> Unit) {
    val severity = WarningGuidance.severityLabel(guide.severity)
    val hint = stringResource(R.string.home_warning_tap, severity)
    val more = if (others > 0) " " + stringResource(R.string.home_warning_more, others) else ""
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${guide.title}$more. $hint"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Telltale(guide, size = 40)
        Column(Modifier.weight(1f)) {
            Text(guide.title + more, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(hint, fontSize = 14.sp)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, Modifier.size(24.dp))
    }
}

/** A feature entry. The whole card is the tap target. */
@Composable
private fun FeatureCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val name = stringResource(title)
    val line = stringResource(description)
    Card(
        onClick,
        modifier.clearAndSetSemantics {
            contentDescription = "$name. $line"
            role = Role.Button
        },
        shape = CardShape,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(line.keepWords(), fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillRow(ui: HomeUi, permissionGranted: Boolean, open: (AppScreen) -> Unit) {
    val location = stringResource(
        when (locationStatus(ui.consent, permissionGranted)) {
            LocationStatus.ON -> R.string.onboarding_location_on
            LocationStatus.OFF -> R.string.onboarding_location_off
            LocationStatus.PERMISSION_OFF -> R.string.onboarding_location_permission_off
        },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pill(
            R.drawable.ic_settings,
            stringResource(R.string.home_settings_pill, location, stringResource(ui.level.label())),
        ) { open(AppScreen.ONBOARDING) }
        if (BuildConfig.DEBUG) {
            // Developer tools: one small pill per debug-only screen.
            AppScreen.entries.filter { it.debugOnly }.forEach { screen ->
                Pill(
                    R.drawable.ic_dev_tools,
                    screen.label,
                    description = stringResource(R.string.home_dev_tool, screen.label),
                ) { open(screen) }
            }
        }
    }
}

@Composable
private fun Pill(@DrawableRes icon: Int, text: String, description: String = text, onClick: () -> Unit) {
    Surface(
        onClick,
        Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp))
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            Telltale(guide, size = 56)
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
private fun Telltale(guide: WarningGuide, size: Int) {
    val description = stringResource(R.string.home_warning_icon, guide.title)
    // ActiveBlue is never an icon or text color on a light background, so INFO uses primary here.
    // TODO(c1): C1's severityColors maps INFO to ActiveBlue; its own screen has the same issue.
    val tint = if (guide.severity == Severity.INFO) MaterialTheme.colorScheme.primary else severityColors(guide.severity).first
    if (guide.type == WarningType.TIRE_PRESSURE) {
        Icon(painterResource(R.drawable.ic_telltale_tpms), description, Modifier.size(size.dp), tint)
    } else {
        Text(
            "⚠",
            color = tint,
            fontSize = (size * 0.85f).sp,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
