package pl.kacperpe.zadania

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.util.Calendar

/** Codzienne przypomnienie o 19:00, jeśli cel dnia nie jest jeszcze zrobiony. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = ctx.getSharedPreferences("zadania", 0)
        if (!prefs.getBoolean("remind", false)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminder.schedule(ctx); return }
        val goal = prefs.getInt("goal", 10)
        val done = if (prefs.getLong("day", 0) == LocalDate.now().toEpochDay()) prefs.getInt("dayCount", 0) else 0
        if (done >= goal) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("cel", "Cel dzienny", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, "cel")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Zadania szachowe czekają")
            .setContentText("Dziś $done z $goal. Dokończ cel dnia.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(1, n) } catch (_: Exception) {}
    }
}

object Reminder {
    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, intent(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(intent(ctx))
    }
}
