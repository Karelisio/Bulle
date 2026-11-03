package com.karelisio.bulle.modules.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import kotlinx.coroutines.delay

/** Recherche d'applis directement sur l'île (le clavier s'ouvre dans l'overlay). */
object AppSearchPanel : IslandPanel {
    override val id = "app_search"

    @Composable
    override fun Content(host: IslandHost) {
        val context = host.context
        var query by remember { mutableStateOf("") }
        var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) { apps = AppCatalog.apps(context) }
        DisposableEffect(Unit) {
            host.requestInput(true)
            onDispose { host.requestInput(false) }
        }
        LaunchedEffect(Unit) {
            delay(120) // le temps que la fenêtre devienne focusable
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }

        val results = remember(query, apps) { AppCatalog.search(apps, query) }
        fun launch(app: AppEntry) {
            keyboard?.hide()
            host.closePanel()
            host.collapse()
            AppCatalog.launch(context, app)
        }

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), color = Color.White.copy(alpha = 0.45f))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(::launch) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                results.take(5).forEachIndexed { index, app ->
                    // Apparition en cascade des résultats.
                    var shown by remember(app.component) { mutableStateOf(false) }
                    LaunchedEffect(app.component) { delay(index * 35L); shown = true }
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.6f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { launch(app) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, 44.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                app.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    stringResource(R.string.search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp).size(width = 300.dp, height = 20.dp),
                )
            }
        }
    }
}
