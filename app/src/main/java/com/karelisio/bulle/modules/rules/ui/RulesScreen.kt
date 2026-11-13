package com.karelisio.bulle.modules.rules.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.rules.Rule
import com.karelisio.bulle.modules.rules.RuleLogic
import com.karelisio.bulle.modules.rules.RuleWorld
import com.karelisio.bulle.modules.rules.RuleWorldSources
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.launch

/** Liste des règles automatiques. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val rules = settings.rules.rules
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Rule?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Environnement observé en direct (aperçu « active maintenant ») : règles enregistrées + brouillon.
    val currentRules by rememberUpdatedState(rules)
    val sources = remember {
        RuleWorldSources(context, scope, snapshotFlow { currentRules + listOfNotNull(draft) })
    }
    DisposableEffect(sources) {
        sources.start()
        onDispose { sources.stop() }
    }
    val world by sources.world.collectAsStateWithLifecycle()

    fun update(transform: (List<Rule>) -> List<Rule>) = scope.launch {
        SettingsRepository.update(context) { it.copy(rules = it.rules.copy(rules = transform(it.rules.rules))) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.rules_add)) },
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.rules_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rules, key = { it.id }) { rule ->
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) update { l -> l.filterNot { r -> r.id == rule.id } } },
                    modifier = Modifier.animateItem().cascade(rules.indexOf(rule)).clip(RoundedCornerShape(28.dp)),
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                    },
                ) {
                    RuleCard(
                        rule = rule,
                        world = world,
                        onClick = { editing = rule },
                        onToggle = { on -> update { l -> l.map { if (it.id == rule.id) it.copy(enabled = on) else it } } },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        RuleEditorSheet(
            initial = editing,
            world = world,
            onDraft = { draft = it },
            onDismiss = { creating = false; editing = null; draft = null },
            onSave = { rule ->
                update { l -> if (l.any { it.id == rule.id }) l.map { if (it.id == rule.id) rule else it } else l + rule }
                creating = false
                editing = null
                draft = null
            },
        )
    }
}

@Composable
private fun RuleCard(rule: Rule, world: RuleWorld, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val activeNow = rule.enabled && RuleLogic.isActive(rule, world)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.sentence(), style = MaterialTheme.typography.titleMedium)
                if (activeNow) {
                    Spacer(Modifier.height(6.dp))
                    LiveBadge(active = true)
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}
