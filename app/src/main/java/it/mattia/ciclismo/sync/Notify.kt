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
import it.mattia.ciclismo.data.AppData
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.engine.Kind
import it.mattia.ciclismo.engine.Planner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("refresh", false)) {
            Repo.ensure(context)
            Notify.scheduleAll(context)
            return
        }
        val code = intent.getIntExtra("code", 0)
        val title = intent.getStringExtra("title") ?: return
        Notify.show(context, code, title, intent.getStringExtra("text") ?: "")
    }
}

/** Reminders are lost when the phone restarts: schedule them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Repo.ensure(context)
        Notify.scheduleAll(context)
    }
}

object Notify {
    private const val CHANNEL = "promemoria"
    private const val REFRESH = 1
    private const val MEAL_BASE = 100
    private const val WEIGH = 200
    private const val EVENING = 201
    private const val WATER_BASE = 300
    private const val DRINK_BASE = 500
    private const val RIDE_BASE = 600
    private const val SLOTS = 40

    val mealKinds = listOf(
        Kind.BREAKFAST to "Colazione", Kind.SNACK to "Spuntini", Kind.LUNCH to "Pranzo", Kind.DINNER to "Cena",
        Kind.PRE to "Pre-giro", Kind.POST to "Recupero", Kind.SUPPER to "Spuntino per domani",
    )

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
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(code, n)
        } catch (_: SecurityException) {
        }
    }

    private fun pending(ctx: Context, code: Int, title: String = "x", text: String = "", refresh: Boolean = false): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("code", code).putExtra("title", title).putExtra("text", text).putExtra("refresh", refresh)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun at(ctx: Context, millis: Long, pi: PendingIntent) {
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun millis(date: LocalDate, minuteOfDay: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT).plusMinutes(minuteOfDay.toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun cancel(ctx: Context, code: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, code))
    }

    private fun cancelRange(ctx: Context, base: Int) {
        for (k in 0 until SLOTS) cancel(ctx, base + k)
    }

    /** One reminder at a planned time, only if it is still in the future. */
    private fun once(ctx: Context, code: Int, whenMs: Long, title: String, text: String) {
        if (whenMs > System.currentTimeMillis() + 1000) at(ctx, whenMs, pending(ctx, code, title, text))
    }

    /** Everything that should ring today, computed from the current plan. */
    class Planned(val code: Int, val minuteOfDay: Int, val title: String, val text: String)

    fun plannedToday(d: AppData, date: LocalDate): List<Planned> {
        val p = d.profile
        if (!p.notifOn) return emptyList()
        val out = mutableListOf<Planned>()
        if (p.notifMeals) {
            val kinds = p.notifMealKinds.split(',').map { it.trim() }.toSet()
            val info = Planner.dayInfo(d, date)
            Planner.meals(d, info).filter { it.kind.name in kinds }.forEachIndexed { i, m ->
                out += Planned(MEAL_BASE + i, (m.timeMin - p.notifMealLead).coerceAtLeast(0), "${m.name} alle ${m.time}",
                    "${m.kcal} kcal: ${m.summary}. Apri il piano per grammi e alternative.")
            }
        }
        if (p.notifWater && p.notifWaterEvery > 0) {
            var t = p.notifWaterFrom
            var i = 0
            while (t <= p.notifWaterTo && i < SLOTS) {
                out += Planned(WATER_BASE + i, t, "Bevi un bicchiere d'acqua", "Obiettivo di oggi: ${Planner.waterGoal(d, Planner.dayInfo(d, date))} ml.")
                t += p.notifWaterEvery * 60; i++
            }
        }
        val ride = d.planned[date.toString()]
        if (p.notifDrink && ride != null && p.notifDrinkEvery > 0) {
            val info = Planner.dayInfo(d, date)
            val ml = Planner.fuelFor(info, d)?.fluidMlPerH ?: 600
            val start = ride.startHour * 60
            for (k in 1..(ride.durMin / p.notifDrinkEvery).coerceAtMost(SLOTS - 1)) {
                out += Planned(RIDE_BASE + k, start + k * p.notifDrinkEvery, "Bevi",
                    "Circa ${ml * p.notifDrinkEvery / 60} ml e, se previsto, un gel o una barretta.")
            }
        }
        if (p.notifWeigh) out += Planned(WEIGH, p.notifWeighAt, "Pesata del mattino", "Pesati a digiuno: l'app ricalcola calorie e macro.")
        if (p.notifEvening) out += Planned(EVENING, p.notifEveningAt, "Piano di domani pronto", "Controlla pasti e carburante per il giro di domani.")
        return out
    }

    /**
     * Re-applies all reminders for today from the current data and settings, and sets a refresh just after
     * midnight that does the same for the next day. Called at start, on settings or plan changes and after a reboot.
     */
    fun scheduleAll(ctx: Context, d: AppData = Repo.data) {
        listOf(MEAL_BASE, WATER_BASE, RIDE_BASE).forEach { cancelRange(ctx, it) }
        cancel(ctx, WEIGH); cancel(ctx, EVENING); cancel(ctx, REFRESH)
        if (!d.profile.notifOn) return
        val today = LocalDate.now()
        plannedToday(d, today).forEach { once(ctx, it.code, millis(today, it.minuteOfDay), it.title, it.text) }
        at(ctx, millis(today.plusDays(1), 5), pending(ctx, REFRESH, refresh = true))
    }

    /** One reminder every [everyMin] minutes for the length of the ride, starting now. */
    fun startDrinkReminders(ctx: Context, durMin: Int, fluidMl: Int, everyMin: Int = 20) {
        val count = (durMin / everyMin).coerceAtMost(30)
        for (k in 1..count) {
            val pi = pending(ctx, DRINK_BASE + k, "Bevi", "Circa ${fluidMl * everyMin / 60} ml e, se previsto, un gel o una barretta.")
            at(ctx, System.currentTimeMillis() + k * everyMin * 60_000L, pi)
        }
    }

    fun stopDrinkReminders(ctx: Context) {
        for (k in 1..30) cancel(ctx, DRINK_BASE + k)
    }
}
