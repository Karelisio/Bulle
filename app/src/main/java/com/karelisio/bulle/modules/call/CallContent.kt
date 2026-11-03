package com.karelisio.bulle.modules.call

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.live.NotificationIntents
import com.karelisio.bulle.modules.live.ui.CompactDigits
import com.karelisio.bulle.modules.live.ui.RollingText
import com.karelisio.bulle.modules.live.ui.rememberWallClock
import com.karelisio.bulle.ui.components.rememberReduceMotion

private val CallGreen = Color(0xFF34C759)
private val CallRed = Color(0xFFFF3B30)

/**
 * Un appel affiché par l'île. La clé dépend de l'état : quand l'appel est décroché,
 * le contenu « en cours » remplace le contenu « entrant » et l'île se replie d'elle-même.
 */
data class CallContent(
    val call: CallInfo,
    val avatar: ImageBitmap?,
    val appLabel: String?,
    val autoExpand: Boolean,
    /** Début de l'appel (heure murale) : `when` de la notification ou première apparition. */
    val startWallMillis: Long,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "call:" + call.state.name.lowercase() + ":" + call.key
    override val moduleId = CallModule.ID
    override val priority = Priority.CALL
    override val showWhenHidden = true
    override val canBeSecondary = true
    override val accent: Color = if (call.state == CallState.ONGOING) CallGreen else Color.White

    private val ringing get() = call.state != CallState.ONGOING

    /** Tant que ça sonne, l'île reste ouverte (le système coupe la sonnerie bien avant). */
    override val autoExpandMs: Long? = if (ringing && autoExpand) 60_000L else null

    override val compactSlotWidth: Dp = if (ringing) 40.dp else 66.dp

    // ---------- Vue compacte ----------

    @Composable
    override fun Leading() {
        if (ringing) Avatar(24.dp) else PhoneBadge(22.dp)
    }

    @Composable
    override fun Trailing() {
        if (ringing) {
            WobblingPhone(20.dp)
        } else {
            Duration(MaterialTheme.typography.labelLarge.merge(CompactDigits), CallGreen)
        }
    }

    @Composable
    override fun Minimal() {
        if (ringing) WobblingPhone(18.dp) else PhoneBadge(20.dp)
    }

    // ---------- Vue étendue ----------

    @Composable
    override fun Expanded(host: IslandHost) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        call.callerName ?: stringResource(R.string.call_unknown),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ringing) {
                        Text(
                            stringResource(statusRes()) + (appLabel?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Duration(MaterialTheme.typography.bodyLarge.merge(CompactDigits), CallGreen)
                    }
                }
                if (!ringing) {
                    Spacer(Modifier.width(12.dp))
                    RoundCallButton(
                        icon = Icons.Rounded.CallEnd,
                        color = CallRed,
                        label = null,
                        description = stringResource(R.string.call_hang_up),
                        size = 52.dp,
                        enabled = (call.hangUpIntent ?: call.declineIntent) != null,
                    ) {
                        NotificationIntents.send(host.context, call.hangUpIntent ?: call.declineIntent)
                    }
                }
            }
            if (ringing) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val decline = call.declineIntent ?: call.hangUpIntent
                    RoundCallButton(
                        icon = Icons.Rounded.CallEnd,
                        color = CallRed,
                        label = stringResource(R.string.call_decline),
                        description = stringResource(R.string.call_decline),
                        enabled = decline != null,
                    ) {
                        NotificationIntents.send(host.context, decline)
                        host.collapse()
                    }
                    if (call.answerIntent != null) {
                        RoundCallButton(
                            icon = if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            color = CallGreen,
                            label = stringResource(R.string.call_answer),
                            description = stringResource(R.string.call_answer),
                            pulse = true,
                        ) {
                            NotificationIntents.send(host.context, call.answerIntent)
                            host.collapse()
                        }
                    }
                }
            }
        }
    }

    /** Toucher la carte ouvre l'écran d'appel de l'appli. */
    override fun onExpandedTap(host: IslandHost): Boolean {
        if (!NotificationIntents.send(host.context, call.contentIntent)) return false
        host.collapse()
        return true
    }

    private fun statusRes() = when (call.state) {
        CallState.SCREENING -> R.string.call_screening
        else -> if (call.isVideo) R.string.call_incoming_video else R.string.call_incoming
    }

    // ---------- Éléments ----------

    @Composable
    private fun Duration(style: androidx.compose.ui.text.TextStyle, color: Color) {
        val now = rememberWallClock()
        RollingText(CallParser.formatDuration(now - startWallMillis), style = style, color = color)
    }

    /** Photo de l'appelant, ou ses initiales, ou une silhouette. */
    @Composable
    private fun Avatar(size: Dp) {
        val initials = remember(call.callerName) { CallParser.initials(call.callerName) }
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(0xFF3A3A3C)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                avatar != null -> Image(avatar, contentDescription = null, modifier = Modifier.size(size))
                initials != null -> Text(
                    initials,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.4f).sp,
                    maxLines = 1,
                )
                else -> Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
            }
        }
    }

    @Composable
    private fun PhoneBadge(size: Dp) {
        Box(Modifier.size(size).clip(CircleShape).background(CallGreen), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.Black, modifier = Modifier.size(size * 0.62f))
        }
    }

    /** Combiné vert qui vibre par salves, comme une sonnerie. */
    @Composable
    private fun WobblingPhone(size: Dp) {
        val reduceMotion = rememberReduceMotion()
        val angle = if (!reduceMotion) {
            val t = rememberInfiniteTransition(label = "ring")
            t.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        0f at 0
                        -16f at 60
                        16f at 140
                        -14f at 220
                        14f at 300
                        -10f at 380
                        10f at 460
                        0f at 540
                        0f at 1200
                    },
                ),
                label = "angle",
            )
        } else {
            null
        }
        Icon(
            Icons.Rounded.Call,
            contentDescription = null,
            tint = CallGreen,
            modifier = Modifier.size(size).graphicsLayer { rotationZ = angle?.value ?: 0f },
        )
    }

    /**
     * Gros bouton rond : s'enfonce au toucher (ressort), retour haptique, et halo
     * qui pulse pour « Décrocher » (coupé si les animations sont supprimées).
     */
    @Composable
    private fun RoundCallButton(
        icon: ImageVector,
        color: Color,
        label: String?,
        description: String,
        size: Dp = 64.dp,
        enabled: Boolean = true,
        pulse: Boolean = false,
        onClick: () -> Unit,
    ) {
        val view = LocalView.current
        val reduceMotion = rememberReduceMotion()
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.86f else 1f,
            spring(dampingRatio = 0.45f, stiffness = 700f),
            label = "scale",
        )
        val halo = if (pulse && !reduceMotion) {
            val t = rememberInfiniteTransition(label = "halo")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "h")
        } else {
            null
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                if (halo != null) {
                    Canvas(Modifier.size(size)) {
                        val p = halo.value
                        drawCircle(color.copy(alpha = 0.45f * (1f - p)), radius = this.size.minDimension / 2 * (1f + 0.35f * p))
                    }
                }
                Box(
                    Modifier
                        .size(size)
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
                        .clip(CircleShape)
                        .background(color)
                        .clickable(
                            interactionSource = source,
                            indication = ripple(color = Color.White),
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = description,
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.45f))
                }
            }
            if (label != null) {
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
