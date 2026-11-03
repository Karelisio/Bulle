package com.karelisio.bulle.core.update

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.R
import com.karelisio.bulle.ui.components.rememberReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * À placer à la racine de l'appli : vérifie au lancement (si le délai de 6 h est écoulé)
 * et affiche la feuille de mise à jour quand une version plus récente est publiée.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateChecker.checkIfDue(context.applicationContext) }
    val release by UpdateChecker.available.collectAsStateWithLifecycle()
    release?.let { UpdateSheet(it, onDismiss = { UpdateChecker.dismiss() }) }
}

/** Feuille de mise à jour : version, changelog, téléchargement et installation. */
@Composable
fun UpdateSheet(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by UpdateInstaller.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(UpdateInstaller.canInstall(context)) }
    var job by remember { mutableStateOf<Job?>(null) }
    // Retour de la page « applis inconnues » : on relit l'autorisation.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = UpdateInstaller.canInstall(context) }

    fun close() {
        job?.cancel()
        UpdateInstaller.reset()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.NewReleases, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.update_version_line, release.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (release.apkSize > 0) {
                Text(
                    stringResource(R.string.update_size, Formatter.formatShortFileSize(context, release.apkSize)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))

            // Changelog défilant.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(4.dp))
                    if (release.changelog.isBlank()) {
                        Text(stringResource(R.string.update_no_changelog), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        MarkdownText(release.changelog)
                    }
                }
            }
            Spacer(Modifier.size(20.dp))

            AnimatedContent(
                targetState = when {
                    state is InstallState.Idle && !canInstall -> "perm"
                    else -> state::class.simpleName.orEmpty()
                },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                label = "update-state",
            ) { key ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (key) {
                        "perm" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.update_permission_needed),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Button(
                                onClick = { context.startActivity(UpdateInstaller.unknownSourcesIntent(context)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.update_permission_grant)) }
                        }
                        InstallState.Downloading::class.simpleName -> DownloadProgress(
                            (state as? InstallState.Downloading) ?: InstallState.Downloading(0, 0),
                            onCancel = {
                                job?.cancel()
                                UpdateInstaller.reset()
                            },
                        )
                        InstallState.Installing::class.simpleName -> {
                            LoadingIndicatorOrSpinner()
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.update_install_confirm_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        InstallState.Failed::class.simpleName -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(
                                        R.string.update_failed,
                                        (state as? InstallState.Failed)?.message.orEmpty(),
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Button(
                                onClick = { job = scope.launch { UpdateInstaller.downloadAndInstall(context, release) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.update_retry)) }
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text(stringResource(R.string.update_view_on_github)) }
                        }
                        else -> Button(
                            onClick = { job = scope.launch { UpdateInstaller.downloadAndInstall(context, release) } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.update_download))
                        }
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = ::close, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_later))
                }
                FilledTonalButton(
                    onClick = {
                        job?.cancel()
                        UpdateInstaller.reset()
                        scope.launch { UpdateChecker.skip(context, release) }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.update_skip)) }
            }
        }
    }
}

/** Anneau + barre ondulés (M3 Expressive) avec octets reçus. */
@Composable
private fun DownloadProgress(state: InstallState.Downloading, onCancel: () -> Unit) {
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    val target = state.fraction ?: 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 200f),
        label = "download",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        if (reduce) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(88.dp))
        } else {
            CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(88.dp))
        }
        Text(
            "${(progress * 100).toInt()} %",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.size(12.dp))
    if (reduce) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.size(8.dp))
    Text(
        stringResource(R.string.update_downloading) + "  " + stringResource(
            R.string.update_bytes,
            Formatter.formatShortFileSize(context, state.bytes),
            if (state.total > 0) Formatter.formatShortFileSize(context, state.total) else "?",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
}

@Composable
private fun LoadingIndicatorOrSpinner() {
    if (rememberReduceMotion()) CircularProgressIndicator() else LoadingIndicator()
}
