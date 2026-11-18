package com.karelisio.bulle.modules.volet.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.volet.VoletController
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletCardDensity
import com.karelisio.bulle.modules.volet.notifications.NotifCardPreview
import com.karelisio.bulle.modules.volet.VoletSettings
import com.karelisio.bulle.modules.volet.VoletZones
import com.karelisio.bulle.modules.volet.history.NotificationHistoryScreen
import com.karelisio.bulle.ui.components.cascade
import com.karelisio.bulle.ui.onboarding.PermissionGlyph
import com.karelisio.bulle.ui.onboarding.PermissionLogic
import com.karelisio.bulle.ui.onboarding.PillState
import com.karelisio.bulle.ui.onboarding.StatusPill
import com.karelisio.bulle.ui.onboarding.rememberPermissionController
import com.karelisio.bulle.ui.onboarding.texts
import com.karelisio.bulle.ui.settings.AppList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bornes des réglages (partagées avec les tests). */
object VoletSettingsBounds {
    const val TRIGGER_MIN = 12f
    const val TRIGGER_MAX = 64f
    const val EDGE_MAX = 64f
    const val BLUR_MAX = 80f
    const val SCRIM_MIN = 0.2f
    const val SCRIM_MAX = 0.95f
    const val CORNER_MIN = 8f
    const val CORNER_MAX = 40f
    const val TEXT_SCALE_MIN = 0.8f
    const val TEXT_SCALE_MAX = 1.2f

    /** Taille du texte des cartes : pas de 5 %, bornée à 80 – 120 %. */
    fun textScale(raw: Float): Float = ((raw * 20).roundToInt() / 20f).coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX)

    /** Hauteur des zones en paysage : 0 (coupées) à 48 dp. */
    const val LANDSCAPE_MAX = 48f

    fun landscapeHeight(raw: Float): Float = raw.roundToInt().toFloat().coerceIn(0f, LANDSCAPE_MAX)

    /** Hauteur de zone : sous le minimum, on revient à « auto » (0). */
    fun triggerHeight(raw: Float): Float = if (raw < TRIGGER_MIN) 0f else raw.roundToInt().toFloat().coerceAtMost(TRIGGER_MAX)
}

/** Autorisations nécessaires au Volet, avec leur état en direct. */
@Composable
internal fun VoletPermissions() {
    val permissions = rememberPermissionController()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionLogic.voletRequired.forEach { key ->
            val granted = permissions[key]
            val texts = key.texts()
            Row(verticalAlignment = Alignment.CenterVertically) {
                PermissionGlyph(texts.icon, granted, size = 40.dp, animated = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(texts.title), style = MaterialTheme.typography.bodyLarge)
                    StatusPill(if (granted) PillState.GRANTED else PillState.DENIED, Modifier.padding(top = 4.dp))
                }
                if (granted) {
                    TextButton(onClick = { permissions.request(key) }) { Text(stringResource(R.string.vset_manage)) }
                } else {
                    FilledTonalButton(onClick = { permissions.request(key) }) { Text(stringResource(R.string.vset_allow)) }
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(icon: ImageVector, title: String, index: Int, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().cascade(index),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
internal fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    steps: Int = 0,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
        )
    }
}
