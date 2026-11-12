package com.karelisio.bulle.modules.weather.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.weather.WeatherCity
import com.karelisio.bulle.modules.weather.WeatherLocation
import com.karelisio.bulle.modules.weather.WeatherLocationSource
import com.karelisio.bulle.modules.weather.WeatherModule
import com.karelisio.bulle.modules.weather.WeatherRepository
import com.karelisio.bulle.modules.weather.WeatherSettings
import com.karelisio.bulle.modules.weather.WeatherSettingsBinding
import com.karelisio.bulle.modules.weather.formatTemperature
import kotlinx.coroutines.launch
import java.util.Locale

/** Réglages du module « Météo » (Open-Meteo). La position approximative est demandée à l'activation. */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val weather = WeatherSettingsBinding.read(settings)
    val enabled = WeatherModule.isEnabled(settings)
    val forecast by WeatherRepository.latest.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(WeatherLocation.hasPermission(context)) }
    LifecycleResumeEffect(Unit) {
        granted = WeatherLocation.hasPermission(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { WeatherRepository.cached(context) }

    fun setEnabled(on: Boolean) {
        scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (WeatherModule.ID to on)) } }
    }

    fun update(transform: (WeatherSettings) -> WeatherSettings) {
        scope.launch {
            SettingsRepository.update(context) { WeatherSettingsBinding.write(it, transform(WeatherSettingsBinding.read(it))) }
        }
    }

    // Activation demandée en attendant la réponse à la permission de localisation.
    var enableAfterGrant by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok && enableAfterGrant) setEnabled(true)
        enableAfterGrant = false
    }
    fun askLocation(thenEnable: Boolean) {
        enableAfterGrant = thenEnable
        permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherCity>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val language = remember { if (Locale.getDefault().language == "fr") "fr" else "en" }
    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        scope.launch {
            results = WeatherRepository.searchCity(query, language)
            searching = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.weather_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.weather_settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.weather_settings_enable),
                        summary = stringResource(R.string.weather_settings_enable_summary),
                        checked = enabled,
                    ) { on ->
                        if (on && weather.source == WeatherLocationSource.DEVICE && !granted) askLocation(thenEnable = true) else setEnabled(on)
                    }
                }
            }
            // Aperçu : dernière prévision connue (cache compris).
            item {
                SettingsCard {
                    val f = forecast
                    ListItem(
                        leadingContent = { if (f != null) WeatherGlyph(f.kind, f.isDay, size = 36.dp) },
                        headlineContent = {
                            Text(
                                if (f == null) {
                                    stringResource(R.string.weather_settings_no_data)
                                } else {
                                    listOfNotNull(f.temperature?.let(::formatTemperature), stringResource(f.kind.labelRes)).joinToString(" · ")
                                },
                            )
                        },
                        supportingContent = f?.let {
                            {
                                Text(
                                    stringResource(
                                        R.string.weather_updated_at,
                                        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.fetchedAt)),
                                    ),
                                )
                            }
                        },
                        trailingContent = {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    enabled = enabled,
                                    onClick = {
                                        refreshing = true
                                        scope.launch {
                                            WeatherModule.load(context, weather, force = true)
                                            refreshing = false
                                        }
                                    },
                                ) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.weather_settings_refresh)) }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.weather_settings_location_header)) }
            item {
                SettingsCard {
                    val sources = listOf(
                        WeatherLocationSource.DEVICE to R.string.weather_settings_source_device,
                        WeatherLocationSource.CITY to R.string.weather_settings_source_city,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                        sources.forEachIndexed { i, (source, label) ->
                            SegmentedButton(
                                selected = weather.source == source,
                                onClick = {
                                    update { it.copy(source = source) }
                                    if (source == WeatherLocationSource.DEVICE && !granted) askLocation(thenEnable = false)
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, sources.size),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    if (weather.source == WeatherLocationSource.DEVICE && !granted) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.weather_settings_permission_missing)) },
                            trailingContent = {
                                TextButton(onClick = { askLocation(thenEnable = false) }) { Text(stringResource(R.string.weather_settings_permission_grant)) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    if (weather.source == WeatherLocationSource.CITY) {
                        weather.city?.let { city ->
                            ListItem(
                                leadingContent = { Icon(Icons.Rounded.LocationCity, contentDescription = null) },
                                headlineContent = { Text(city.name) },
                                supportingContent = { Text(city.label) },
                                trailingContent = { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.weather_settings_city_search)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            trailingIcon = {
                                if (searching) {
                                    CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { search() }) {
                                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.weather_settings_city_search))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }
            val found = results
            if (weather.source == WeatherLocationSource.CITY && found != null) {
                if (found.isEmpty()) {
                    item { FootNote(stringResource(R.string.weather_settings_city_none)) }
                }
                items(found, key = { "${it.latitude},${it.longitude},${it.name}" }) { city ->
                    SettingsCard(Modifier.animateItem()) {
                        ListItem(
                            headlineContent = { Text(city.name) },
                            supportingContent = { Text(city.label) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                update { it.copy(city = city, source = WeatherLocationSource.CITY) }
                                results = null
                                query = ""
                            },
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.weather_settings_show_header)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.weather_settings_rain),
                        summary = stringResource(R.string.weather_settings_rain_summary),
                        checked = weather.rainAlert,
                        enabled = enabled,
                    ) { on -> update { it.copy(rainAlert = on) } }
                    val thresholds = WeatherSettings.THRESHOLD_CHOICES
                    val labels = listOf(R.string.weather_threshold_light, R.string.weather_threshold_moderate, R.string.weather_threshold_heavy)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        thresholds.forEachIndexed { i, mm ->
                            SegmentedButton(
                                selected = weather.rainThresholdMm == mm,
                                onClick = { update { it.copy(rainThresholdMm = mm) } },
                                shape = SegmentedButtonDefaults.itemShape(i, thresholds.size),
                                enabled = enabled && weather.rainAlert,
                            ) { Text(stringResource(labels[i]), maxLines = 1) }
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.weather_settings_idle),
                        summary = stringResource(R.string.weather_settings_idle_summary),
                        checked = weather.idleTemperature,
                        enabled = enabled,
                    ) { on -> update { it.copy(idleTemperature = on) } }
                }
            }
            item { FootNote(stringResource(R.string.weather_settings_privacy)) }
        }
    }
}
