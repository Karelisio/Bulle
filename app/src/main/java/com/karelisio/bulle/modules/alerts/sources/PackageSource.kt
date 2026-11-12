package com.karelisio.bulle.modules.alerts.sources

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import com.karelisio.bulle.modules.alerts.ui.rememberEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

private val InstallGreen = Color(0xFF7BE0A8)
private val RemoveGrey = Color(0xFFFF8A80)

/**
 * Appli installée ou désinstallée (hors mises à jour : EXTRA_REPLACING ignoré).
 * QUERY_ALL_PACKAGES (manifeste) permet de lire le nom et l'icône de l'appli installée.
 */
object PackageSource : AlertSource {
    override val id = "packages"

    override fun isEnabled(settings: AlertSettings) = settings.packages

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        broadcasts(ctx, filter).collect { i ->
            if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return@collect
            val pkg = i.data?.schemeSpecificPart ?: return@collect
            if (pkg == ctx.packageName) return@collect
            val installed = i.action == Intent.ACTION_PACKAGE_ADDED
            val label = if (installed) appLabel(ctx, pkg) else null
            emit(PackageContent(pkg, label, installed, alertNow()))
        }
    }.latestAlive()

    private fun appLabel(ctx: Context, pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrNull()

    /** Ouvre la fiche Play Store (market://), repli sur la page web. */
    fun openStore(context: Context, pkg: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()).addFlags(flags))
        } catch (_: Exception) { // Pas de Play Store, ou lancement refusé : repli sur le site.
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()).addFlags(flags),
                )
            }
        }
    }
}

/** Icône de l'appli chargée hors du thread principal (null si inconnue / désinstallée). */
@Composable
private fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    return icon
}

@Composable
private fun AppIcon(pkg: String, installed: Boolean, size: Dp) {
    val bmp = if (installed) rememberAppIcon(pkg) else null
    if (bmp == null) {
        PopIcon(if (installed) Icons.Rounded.InstallMobile else Icons.Rounded.DeleteOutline, if (installed) InstallGreen else RemoveGrey, size = size)
    } else {
        val e = rememberEntrance(from = 0.3f)
        Image(
            bmp, null,
            Modifier.size(size).clip(RoundedCornerShape(size / 4)).graphicsLayer { scaleX = e.value; scaleY = e.value },
        )
    }
}

/** Appli installée (icône + nom) ou désinstallée (bouton Réinstaller). */
data class PackageContent(
    val packageName: String,
    val label: String?,
    val installed: Boolean,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:package:$postedAt"
    override val alertId = "packages"
    override val accent = if (installed) InstallGreen else RemoveGrey
    override val compactSlotWidth = 84.dp
    override val baseTimeoutMs: Long? = 5_000L

    @Composable
    override fun Leading() = AppIcon(packageName, installed, 22.dp)

    @Composable
    override fun Trailing() =
        AlertLabel(stringResource(if (installed) R.string.alerts_pkg_installed_short else R.string.alerts_pkg_removed_short), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = accent,
            title = label ?: packageName,
            subtitle = stringResource(if (installed) R.string.alerts_pkg_installed else R.string.alerts_pkg_removed),
            glyph = { AppIcon(packageName, installed, 30.dp) },
            actions = {
                if (installed) {
                    FilledTonalButton(onClick = {
                        host.context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                            runCatching { host.context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        host.collapse()
                    }) { Text(stringResource(R.string.alerts_pkg_open)) }
                } else {
                    FilledTonalButton(onClick = {
                        PackageSource.openStore(host.context, packageName)
                        host.collapse()
                    }) { Text(stringResource(R.string.alerts_pkg_reinstall)) }
                }
            },
        )
    }
}
