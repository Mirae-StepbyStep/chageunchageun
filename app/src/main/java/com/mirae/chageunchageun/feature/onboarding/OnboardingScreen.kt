package com.mirae.chageunchageun.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirae.chageunchageun.BuildConfig
import com.mirae.chageunchageun.R
import com.mirae.chageunchageun.domain.settings.AdvisorLevel
import com.mirae.chageunchageun.domain.settings.LocationConsent
import com.mirae.chageunchageun.runtime.AppGraph
import com.mirae.chageunchageun.runtime.AppScreen
import com.mirae.chageunchageun.ui.common.CarScale
import com.mirae.chageunchageun.ui.common.GuideButton
import com.mirae.chageunchageun.ui.common.GuideOutlinedButton
import com.mirae.chageunchageun.ui.common.StepDots
import com.mirae.chageunchageun.ui.common.keepWords

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * First-run setup (location consent, advisor level). After completion the same tab edits those settings.
 * While driving it shows one sentence only; the step is kept in the ViewModel and comes back when parked.
 */
@Composable
fun OnboardingScreen(graph: AppGraph) {
    val vm: OnboardingViewModel = viewModel { OnboardingViewModel(graph.userSettings, graph.driveRuntime.canShowDetail) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    // The Android permission is asked here, not in the ViewModel. A permission that is not declared
    // in the Manifest comes back denied without a dialog, which is the normal "permission off" path.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> vm.onPermissionResult(result.values.any { it }) }
    LaunchedEffect(Unit) {
        vm.onPermissionChecked(
            LOCATION_PERMISSIONS.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    LaunchedEffect(ui.launchPermissionRequest) {
        if (ui.launchPermissionRequest) {
            vm.onPermissionRequestLaunched()
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    CarScale {
        if (ui.restricted) {
            RestrictedNotice()
            return@CarScale
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            // Scrolling is allowed only here, while parked.
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (ui.editMode) {
                    EditContent(ui, vm, wide)
                } else {
                    when (ui.step) {
                        OnboardingStep.LOCATION -> LocationStep(vm)
                        OnboardingStep.LEVEL -> LevelStep(ui, vm, wide)
                        OnboardingStep.DONE -> DoneStep(
                            ui,
                            onMirror = { graph.screenRequests.tryEmit(AppScreen.MIRROR); vm.finish() },
                            onHome = { graph.screenRequests.tryEmit(AppScreen.HOME); vm.finish() },
                            onBack = vm::back,
                        )
                    }
                }
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = vm::resetFirstRun) {
                        Text(stringResource(R.string.onboarding_debug_reset), fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/** Driving: one static sentence. No buttons, no scrolling, nothing to touch. */
@Composable
private fun RestrictedNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card {
            Text(
                stringResource(R.string.onboarding_restricted).keepWords(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
            )
        }
    }
}

@Composable
private fun LocationStep(vm: OnboardingViewModel) {
    StepDots(index = 0, total = 3)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // The mark's tile is the same sand as the light background, so a 1dp outline shows its edge.
        Image(
            painterResource(R.drawable.ic_brand_road),
            contentDescription = null, // the app name is right next to it
            modifier = Modifier
                .size(72.dp)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
        )
        Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
    Title(R.string.onboarding_location_title)
    Body(R.string.onboarding_location_body)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideButton(stringResource(R.string.onboarding_location_agree), vm::agreeToLocation)
        GuideOutlinedButton(stringResource(R.string.onboarding_location_later), vm::declineLocation)
    }
}

@Composable
private fun LevelStep(ui: OnboardingUi, vm: OnboardingViewModel, wide: Boolean) {
    StepDots(index = 1, total = 3)
    if (ui.permissionOff) Body(R.string.onboarding_permission_off)
    Title(R.string.onboarding_level_title)
    LevelChoices(ui.level, vm::selectLevel, wide)
    Body(R.string.onboarding_level_note)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideOutlinedButton(stringResource(R.string.onboarding_back), vm::back)
        GuideButton(stringResource(R.string.onboarding_next), vm::next)
    }
}

@Composable
private fun DoneStep(ui: OnboardingUi, onMirror: () -> Unit, onHome: () -> Unit, onBack: () -> Unit) {
    StepDots(index = 2, total = 3)
    Title(R.string.onboarding_done_title)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onboarding_summary_location, stringResource(ui.locationLabel())), fontSize = 22.sp)
            Text(stringResource(R.string.onboarding_summary_level, stringResource(ui.level.label())), fontSize = 22.sp)
        }
    }
    if (ui.permissionOff) Body(R.string.onboarding_permission_off)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideButton(stringResource(R.string.onboarding_to_mirror), onMirror)
        GuideOutlinedButton(stringResource(R.string.onboarding_to_home), onHome)
    }
    GuideOutlinedButton(stringResource(R.string.onboarding_back), onBack)
}

/** After completion: both settings as sections, no progress label, saved on change. */
@Composable
private fun EditContent(ui: OnboardingUi, vm: OnboardingViewModel, wide: Boolean) {
    Title(R.string.onboarding_edit_title)

    SectionLabel(R.string.onboarding_edit_location)
    Choices(wide) { item ->
        ChoiceCard(
            R.string.onboarding_edit_location_on,
            R.string.onboarding_edit_location_on_desc,
            selected = ui.consent == LocationConsent.GRANTED,
            onClick = vm::agreeToLocation,
            modifier = item,
        )
        ChoiceCard(
            R.string.onboarding_edit_location_off,
            R.string.onboarding_edit_location_off_desc,
            selected = ui.consent == LocationConsent.DECLINED,
            onClick = vm::declineLocation,
            modifier = item,
        )
    }
    if (ui.permissionOff) Body(R.string.onboarding_permission_off)

    SectionLabel(R.string.onboarding_edit_level)
    LevelChoices(ui.level, vm::selectLevel, wide)
    Body(R.string.onboarding_level_note)
}

@Composable
private fun LevelChoices(selected: AdvisorLevel, onSelect: (AdvisorLevel) -> Unit, wide: Boolean) {
    Choices(wide) { item ->
        AdvisorLevel.entries.forEach { level ->
            ChoiceCard(level.label(), level.description(), selected == level, { onSelect(level) }, item)
        }
    }
}

/** Single-choice group: side by side in landscape, stacked in portrait. */
@Composable
private fun Choices(wide: Boolean, content: @Composable (item: Modifier) -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            content(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content(Modifier.fillMaxWidth())
        }
    }
}

/** Selection is shown three ways: accent border, a check mark and the "선택됨" label. Never by color alone. */
@Composable
private fun ChoiceCard(
    @StringRes name: Int,
    @StringRes description: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        border = if (selected) BorderStroke(4.dp, MaterialTheme.colorScheme.secondary) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(description).keepWords(), fontSize = 20.sp, lineHeight = 28.sp)
            // Kept in the layout when unselected so the cards do not change height.
            Text(
                if (selected) "✓ " + stringResource(R.string.onboarding_selected) else "",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Title(@StringRes text: Int) {
    Text(
        stringResource(text).keepWords(),
        fontSize = 34.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(stringResource(text), fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text).keepWords(), fontSize = 22.sp, lineHeight = 32.sp)
}

@StringRes
private fun OnboardingUi.locationLabel(): Int = when {
    consent != LocationConsent.GRANTED -> R.string.onboarding_location_off
    permissionOff -> R.string.onboarding_location_permission_off
    else -> R.string.onboarding_location_on
}

@StringRes
private fun AdvisorLevel.label(): Int = when (this) {
    AdvisorLevel.DETAILED -> R.string.onboarding_level_detailed
    AdvisorLevel.NORMAL -> R.string.onboarding_level_normal
    AdvisorLevel.BRIEF -> R.string.onboarding_level_brief
}

@StringRes
private fun AdvisorLevel.description(): Int = when (this) {
    AdvisorLevel.DETAILED -> R.string.onboarding_level_detailed_desc
    AdvisorLevel.NORMAL -> R.string.onboarding_level_normal_desc
    AdvisorLevel.BRIEF -> R.string.onboarding_level_brief_desc
}
