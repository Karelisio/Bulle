package com.karelisio.bulle.modules.search.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.search.ContactSearch
import com.karelisio.bulle.modules.search.SearchPrefs
import com.karelisio.bulle.modules.search.SearchSettings
import com.karelisio.bulle.modules.search.formatRateDate
import com.karelisio.bulle.modules.search.startSafely
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Réglages de la recherche de l'île : sections activées. L'autorisation Contacts est demandée
 * ici, à l'activation de la section (jamais depuis l'île, qui ne bloque pas la recherche).
 */
@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SearchPrefs.settings(context) }.collectAsStateWithLifecycle(SearchSettings())
    var granted by remember { mutableStateOf(ContactSearch.hasPermission(context)) }
    var ratesDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { ratesDate = SearchPrefs.cachedRatesDate(context) }
    // Retour depuis les Réglages système : l'autorisation a pu changer.
    LifecycleResumeEffect(Unit) {
        granted = ContactSearch.hasPermission(context)
        onPauseOrDispose { }
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
        if (!ok) startSafely(context, ContactSearch.permissionIntent(context))
    }

    fun update(transform: (SearchSettings) -> SearchSettings) {
        scope.launch { SearchPrefs.update(context, transform) }
    }

    ModuleSettingsScaffold(title = stringResource(R.string.search_settings_title), onBack = onBack) {
        item {
            Text(
                stringResource(R.string.search_settings_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item { ModuleSectionHeader(stringResource(R.string.search_settings_sections)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    stringResource(R.string.search_settings_calc),
                    stringResource(R.string.search_settings_calc_summary),
                    settings.calculator,
                ) { on -> update { it.copy(calculator = on) } }
                ModuleSwitchRow(
                    stringResource(R.string.search_settings_units),
                    stringResource(R.string.search_settings_units_summary),
                    settings.units,
                ) { on -> update { it.copy(units = on) } }
                ModuleSwitchRow(
                    stringResource(R.string.search_settings_currency),
                    stringResource(R.string.search_settings_currency_summary) + "\n" +
                        (ratesDate?.let { stringResource(R.string.search_settings_rates_cached, formatRateDate(it, Locale.getDefault())) }
                            ?: stringResource(R.string.search_settings_rates_none)),
                    settings.currency,
                ) { on -> update { it.copy(currency = on) } }
                ModuleSwitchRow(
                    stringResource(R.string.search_settings_contacts),
                    if (settings.contacts && !granted) {
                        stringResource(R.string.search_settings_contacts_denied)
                    } else {
                        stringResource(R.string.search_settings_contacts_summary)
                    },
                    settings.contacts && granted,
                ) { on ->
                    update { it.copy(contacts = on) }
                    if (on && !granted) {
                        if (asked) startSafely(context, ContactSearch.permissionIntent(context))
                        else launcher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
                ModuleSwitchRow(
                    stringResource(R.string.search_settings_system),
                    stringResource(R.string.search_settings_system_summary),
                    settings.systemSettings,
                ) { on -> update { it.copy(systemSettings = on) } }
            }
        }
        item {
            Text(
                stringResource(R.string.search_settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
