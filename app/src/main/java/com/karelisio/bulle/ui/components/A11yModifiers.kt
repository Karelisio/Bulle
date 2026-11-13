package com.karelisio.bulle.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Ligne de réglage à interrupteur : toute la ligne bascule la valeur et TalkBack la lit comme
 * un seul élément (titre + résumé fusionnés), rôle Switch et état coché / non coché.
 * L'interrupteur de la ligne doit avoir `onCheckedChange = null` (simple indicateur).
 */
fun Modifier.switchRow(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Modifier =
    toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)

/** Ligne d'un choix unique (bouton radio) : un seul élément TalkBack, rôle RadioButton et état. */
fun Modifier.radioRow(selected: Boolean, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
