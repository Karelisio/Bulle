package com.karelisio.bulle.modules.deck

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Hôte des widgets d'applis affichés dans le Deck. */
object DeckWidgets {
    private const val HOST_ID = 0xB011E

    @Volatile
    private var host: AppWidgetHost? = null
    private var listeners = 0

    fun host(context: Context): AppWidgetHost =
        host ?: synchronized(this) { host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it } }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    fun info(context: Context, id: Int): AppWidgetProviderInfo? = manager(context).getAppWidgetInfo(id)

    @Synchronized
    fun startListening(context: Context) {
        if (listeners++ == 0) runCatching { host(context).startListening() }
    }

    @Synchronized
    fun stopListening(context: Context) {
        if (--listeners <= 0) {
            listeners = 0
            runCatching { host(context).stopListening() }
        }
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(context, id) ?: return null
        return host(context).createView(context, id, info)
    }

    fun allocate(context: Context): Int = host(context).allocateAppWidgetId()

    fun delete(context: Context, id: Int) = runCatching { host(context).deleteAppWidgetId(id) }

    fun providers(context: Context): List<AppWidgetProviderInfo> =
        manager(context).installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }
}
