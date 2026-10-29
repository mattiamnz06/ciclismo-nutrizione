package it.mattia.ciclismo.sync

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import it.mattia.ciclismo.MainActivity
import it.mattia.ciclismo.data.Profile
import it.mattia.ciclismo.engine.MealPlanner
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getIntExtra("code", 0)
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        Notify.show(context, code, title, text)
        val min = intent.getIntExtra("min", -1)
        if (min >= 0) Notify.scheduleDaily(context, code, min, title, text)
    }
}

object Notify {
    private const val CHANNEL = "promemoria"
    private const val DRINK_BASE = 500

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, code: Int, title: String, text: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(code, n)
        } catch (_: SecurityException) {
        }
    }

    private fun pending(ctx: Context, code: Int, title: String, text: String, min: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("code", code).putExtra("title", title).putExtra("text", text).putExtra("min", min)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun scheduleDaily(ctx: Context, code: Int, minuteOfDay: Int, title: String, text: String) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, code, title, text, minuteOfDay))
    }

    private fun cancel(ctx: Context, code: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, code, "x", "", -1))
    }

    /** Re-applies all reminders from the current settings; called at start and when settings change. */
    fun scheduleAll(ctx: Context, p: Profile) {
        val n = p.nMeals.coerceIn(3, 6)
        val times = MealPlanner.parseTimes(p.mealTimes, n)
        for (i in 0 until 6) {
            if (p.notifMeals && i < times.size) scheduleDaily(ctx, 100 + i, times[i], "È ora di mangiare", "Apri il piano di oggi per grammi e alternative.")
            else cancel(ctx, 100 + i)
        }
        if (p.notifWeigh) scheduleDaily(ctx, 200, 7 * 60, "Pesata del mattino", "Pesati a digiuno: l'app ricalcola calorie e macro.") else cancel(ctx, 200)
        if (p.notifEvening) scheduleDaily(ctx, 201, 21 * 60, "Piano di domani pronto", "Controlla pasti e carburante per il giro di domani.") else cancel(ctx, 201)
    }

    /** One reminder every 20 minutes for the length of the ride, starting now. */
    fun startDrinkReminders(ctx: Context, durMin: Int, fluidMl: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val count = (durMin / 20).coerceAtMost(30)
        for (k in 1..count) {
            val pi = pending(ctx, DRINK_BASE + k, "Bevi", "Circa ${fluidMl / 3} ml e, se previsto, un gel o una barretta.", -1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + k * 20L * 60_000, pi)
        }
    }

    fun stopDrinkReminders(ctx: Context) {
        for (k in 1..30) cancel(ctx, DRINK_BASE + k)
    }
}
