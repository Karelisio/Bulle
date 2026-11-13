package com.karelisio.bulle.modules.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/* Sections de résultats de la recherche de l'île (au-dessus et au-dessous des applis). */

private val RowShape = RoundedCornerShape(18.dp)

internal fun copyToClipboard(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Bulle", text))
    }
}

internal fun startSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** Ligne « réponse » : grand résultat, détail en dessous, toucher = copier. */
@Composable
private fun AnswerRow(icon: ImageVector, value: String, detail: String, copyText: String?) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(Color.White.copy(alpha = 0.1f))
            .then(
                if (copyText != null) {
                    Modifier.clickable {
                        copyToClipboard(context, copyText)
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        copied = true
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (copied) stringResource(R.string.search_copied) else detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (copyText != null) {
            Icon(
                if (copied) Icons.Rounded.Done else Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.search_copy),
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun QuickAnswerRow(answer: QuickAnswer, rates: RatesState) {
    val locale = Locale.getDefault()
    when (answer) {
        is QuickAnswer.Calc -> {
            val text = Calculator.format(answer.value, locale)
            AnswerRow(Icons.Rounded.Calculate, "= $text", answer.expression, text)
        }
        is QuickAnswer.Units -> {
            val c = answer.conversion
            val result = Calculator.format(UnitConverter.display(c.result), locale)
            val amount = Calculator.format(c.query.amount, locale)
            AnswerRow(
                Icons.Rounded.Straighten,
                "$result ${c.query.to.symbol}",
                "$amount ${c.query.from.symbol} = $result ${c.query.to.symbol}",
                result,
            )
        }
        is QuickAnswer.Money -> {
            val q = answer.query
            val amount = Calculator.format(q.amount, locale)
            val snapshot = rates.snapshot
            val converted = snapshot?.let { Currency.convert(q, it) }
            when {
                converted != null -> {
                    val text = formatMoney(converted, locale)
                    val date = formatRateDate(snapshot.date, locale)
                    val detail = if (rates.stale) {
                        stringResource(R.string.search_currency_offline, "$amount ${q.from}", date)
                    } else {
                        stringResource(R.string.search_currency_rate, "$amount ${q.from}", date)
                    }
                    AnswerRow(Icons.Rounded.CurrencyExchange, "$text ${q.to}", detail, text)
                }
                rates.loading -> AnswerRow(Icons.Rounded.CurrencyExchange, "$amount ${q.from} → ${q.to}", stringResource(R.string.search_currency_loading), null)
                else -> AnswerRow(Icons.Rounded.CurrencyExchange, "$amount ${q.from} → ${q.to}", stringResource(R.string.search_currency_unavailable), null)
            }
        }
    }
}

/** État des taux pour la saisie courante. */
internal data class RatesState(val snapshot: RatesSnapshot? = null, val loading: Boolean = false, val stale: Boolean = false)

internal fun formatMoney(value: java.math.BigDecimal, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

internal fun formatRateDate(date: String, locale: Locale): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
        .getOrDefault(date)

/** Petite pastille d'action (appeler, SMS, fiche, autoriser…). */
@Composable
internal fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

@Composable
internal fun ContactRow(hit: ContactHit, onLaunch: (Intent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onLaunch(ContactSearch.openIntent(hit)) }
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            hit.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        hit.phone?.let { phone ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip(Icons.Rounded.Phone, stringResource(R.string.search_contact_call)) { onLaunch(ContactSearch.dialIntent(phone)) }
                ActionChip(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.search_contact_sms)) { onLaunch(ContactSearch.smsIntent(phone)) }
            }
        }
    }
}

@Composable
internal fun SettingsShortcutRow(shortcuts: List<SettingsShortcut>, onOpen: (SettingsShortcut) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shortcuts.forEach { s -> ActionChip(Icons.Rounded.Settings, stringResource(s.label)) { onOpen(s) } }
    }
}
