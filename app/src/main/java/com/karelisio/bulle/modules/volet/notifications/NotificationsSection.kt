package com.karelisio.bulle.modules.volet.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karelisio.bulle.modules.volet.VoletHost

/**
 * Liste des notifications du volet. [header] (carte média) est placé en tête de liste
 * et défile avec elle. La liste doit relayer le défilement imbriqué (LazyColumn le fait).
 */
@Composable
fun NotificationsSection(host: VoletHost, modifier: Modifier, header: @Composable () -> Unit) {
    // Implémentation complète : étape « notifications ».
    Column(modifier) { header() }
}
