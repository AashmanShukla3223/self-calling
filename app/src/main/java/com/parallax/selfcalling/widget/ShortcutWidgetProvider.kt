package com.parallax.selfcalling.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RemoteViews
import com.parallax.selfcalling.R
import com.parallax.selfcalling.util.Prefs
import com.parallax.selfcalling.util.ShortcutRunner
import com.parallax.selfcalling.util.ShortcutSets

/**
 * Home screen widget that runs a shortcut set on tap.
 * Multiple widget instances are supported — each stores its own set ID.
 */
class ShortcutWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS = "widget_shortcut"
    }
    private fun setKey(appWidgetId: Int) = "set_id_$appWidgetId"

    private fun saveSetId(context: Context, appWidgetId: Int, setId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(setKey(appWidgetId), setId)
            .apply()
    }

    private fun loadSetId(context: Context, appWidgetId: Int): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(setKey(appWidgetId), -1)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove(setKey(appWidgetId))
        }
        editor.apply()
    }

    override fun onDisabled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == "com.parallax.selfcalling.widget.RUN_SET") {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val setId = loadSetId(context, appWidgetId)
            if (setId >= 0) {
                ShortcutRunner.run(context, setId)
            }
            // Refresh widget to show current state
            val manager = AppWidgetManager.getInstance(context)
            updateWidget(context, manager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val setId = loadSetId(context, appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_shortcut)

        if (setId >= 0) {
            val set = ShortcutSets.get(context, setId)
            if (set != null) {
                views.setTextViewText(R.id.widgetTitle, set.name)
                views.setTextViewText(R.id.widgetSub, "${set.steps.size} steps · tap to run")
            } else {
                views.setTextViewText(R.id.widgetTitle, "Shortcut set")
                views.setTextViewText(R.id.widgetSub, "Not configured")
            }
        } else {
            views.setTextViewText(R.id.widgetTitle, "Shortcut set")
            views.setTextViewText(R.id.widgetSub, "Tap to configure")
        }

        // Tap to run / configure
        val runIntent = Intent(context, ShortcutWidgetProvider::class.java).apply {
            action = "com.parallax.selfcalling.widget.RUN_SET"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context, appWidgetId, runIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetIcon, pending)
        views.setOnClickPendingIntent(R.id.widgetTitle, pending)
        views.setOnClickPendingIntent(R.id.widgetSub, pending)

        manager.updateAppWidget(appWidgetId, views)
    }
}
