package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object MeldWidgetUpdater {
    val KEY_REVISION = intPreferencesKey("meld_widget_revision")
    val KEY_ROOM_INDEX = intPreferencesKey("meld_widget_room_index")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAllAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            refreshAll(appContext)
        }
    }

    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)

        val pollWidget = MeldPollWidget()
        runCatching {
            val pollIds = manager.getGlanceIds(MeldPollWidget::class.java)
            for (glanceId in pollIds) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[KEY_REVISION] = (prefs[KEY_REVISION] ?: 0) + 1
                }
                pollWidget.update(context, glanceId)
            }
        }

        val avatarWidget = MeldAvatarWidget()
        runCatching {
            val avatarIds = manager.getGlanceIds(MeldAvatarWidget::class.java)
            for (glanceId in avatarIds) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[KEY_REVISION] = (prefs[KEY_REVISION] ?: 0) + 1
                }
                avatarWidget.update(context, glanceId)
            }
        }
    }

    fun requestPinPollWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val awm = AppWidgetManager.getInstance(context) ?: return false
        if (!awm.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, MeldPollWidgetReceiver::class.java)
        return runCatching {
            awm.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
    }

    fun requestPinAvatarWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val awm = AppWidgetManager.getInstance(context) ?: return false
        if (!awm.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, MeldAvatarWidgetReceiver::class.java)
        return runCatching {
            awm.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
    }
}
