package com.karelisio.bulle.core.backup

import kotlinx.serialization.Serializable

/** Sauvegarde des réglages. */
@Serializable
data class BackupSettings(
    val autoBackup: Boolean = true,
    /** Dernière sauvegarde automatique (epoch ms). */
    val lastAutoBackup: Long = 0,
    /** Dossier choisi (arborescence SAF : cloud, carte…) où copier aussi chaque sauvegarde automatique. */
    val folderUri: String? = null,
)
