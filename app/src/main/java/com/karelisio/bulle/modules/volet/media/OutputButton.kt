package com.karelisio.bulle.modules.volet.media

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R

/**
 * Bouton de choix de la sortie audio (bulles Volume et Média) : toucher = sélecteur du système,
 * appui long = réglages Bluetooth.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutputButton(
    icon: ImageVector,
    container: Color,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.output_long_press_hint),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(R.string.volume_output), tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Réglages Bluetooth depuis l'île ([close] la replie d'abord). */
fun openBluetoothSettings(context: Context, close: () -> Unit) {
    com.karelisio.bulle.island.IslandLog.log("Sortie audio : réglages Bluetooth (appui long)")
    close()
    runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
