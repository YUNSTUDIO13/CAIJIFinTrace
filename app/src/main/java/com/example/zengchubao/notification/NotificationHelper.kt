package com.example.zengchubao.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.zengchubao.model.AppSettings
import com.example.zengchubao.model.Deposit
import com.example.zengchubao.model.DepositStatus
import com.example.zengchubao.storage.LocalFileManager
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val CHANNEL_ID = "zengchubao_reminder"
private const val CHANNEL_NAME = "存单到期提醒"
private const val ACTION_REMIND = "com.example.zengchubao.REMIND"

object NotificationHelper {

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "存单到期前发送提醒" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun scheduleAll(context: Context, settings: AppSettings) {
        cancelAll(context)
        if (settings.reminderDays <= 0) return
        val storage = LocalFileManager(context)
        val deposits = runBlocking {
            storage.getAllDeposits()
        }
        val now = LocalDate.now()
        deposits.filter { it.status == DepositStatus.HOLDING }
            .forEach { dep ->
                try {
                    val endDate = LocalDate.parse(dep.endDate)
                    val targetDate = endDate.minusDays(settings.reminderDays.toLong())
                    if (!targetDate.isBefore(now)) {
                        scheduleOne(context, dep, targetDate,
                            settings.reminderHour, settings.reminderMinute)
                    }
                } catch (_: Exception) {}
            }
    }

    private fun scheduleOne(
        context: Context, dep: Deposit, targetDate: LocalDate,
        hour: Int, minute: Int
    ) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(hour, minute))
        val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val body = "${dep.productName}（${dep.bankName}）将于${dep.endDate}到期，本金 ¥${"%.2f".format(dep.principal)}"

        val id = dep.id.hashCode()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", "存单到期提醒")
            putExtra("body", body)
            putExtra("notify_id", id)
        }

        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
        } catch (_: SecurityException) {
            // 用户未授予精确闹钟权限，使用普通 alarm
            alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    fun cancelAll(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val storage = LocalFileManager(context)
        val deposits = runBlocking {
            storage.getAllDeposits()
        }
        deposits.filter { it.status == DepositStatus.HOLDING }
            .forEach { dep ->
                val id = dep.id.hashCode()
                val intent = Intent(context, ReminderReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmMgr.cancel(pending)
            }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "存单到期提醒"
        val body = intent.getStringExtra("body") ?: ""
        val notifyId = intent.getIntExtra("notify_id", 0)
        if (body.isEmpty()) return

        val notifyIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val notifyPending = PendingIntent.getActivity(
            context, 0, notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifyId, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(notifyPending)
            .build()
        )
    }
}
