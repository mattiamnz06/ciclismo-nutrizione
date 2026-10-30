package it.mattia.libreria.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import it.mattia.libreria.MainActivity
import it.mattia.libreria.R
import it.mattia.libreria.data.Status
import it.mattia.libreria.data.Store

/** Widget della home: il libro in lettura, dipinto ad acquerello con i colori del telefono. */
class BookWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val book = Store.read(ctx).filter { it.status == Status.READING }.maxByOrNull { it.upd }
            val rv = RemoteViews(ctx.packageName, R.layout.widget_book)
            rv.setImageViewBitmap(R.id.art, WidgetArt.render(ctx, book))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.root, open)
            mgr.updateAppWidget(id, rv)
        }

        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.getAppWidgetIds(ComponentName(ctx, BookWidget::class.java)).forEach { update(ctx, mgr, it) }
            } catch (e: Exception) {
                // il widget non deve mai bloccare il salvataggio
            }
        }
    }
}
