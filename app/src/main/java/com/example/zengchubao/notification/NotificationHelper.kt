package com.example.zengchubao.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.zengchubao.model.AppSettings
import com.example.zengchubao.model.Deposit
import com.example.zengchubao.model.DepositStatus
import com.example.zengchubao.storage.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val CHANNEL_ID = "zengchubao_reminder"
private const val TAG = "ZCB_Reminder"

object NotificationHelper {

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "存单到期提醒", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "存单到期前发送提醒" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    suspend fun scheduleAll(context: Context, settings: AppSettings) = withContext(Dispatchers.IO) {
        cancelAllInternal(context)
        if (settings.reminderDays <= 0) return@withContext
        val storage = LocalFileManager(context)
        val deposits = storage.getAllDeposits()
        val now = LocalDate.now()
        deposits.filter { it.status == DepositStatus.HOLDING }
            .forEach { dep ->
                try {
                    val endDate = LocalDate.parse(dep.endDate)
                    val targetDate = endDate.minusDays(settings.reminderDays.toLong())
                    if (!targetDate.isBefore(now)) {
                        scheduleOneInternal(context, dep, targetDate,
                            settings.reminderHour, settings.reminderMinute)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "schedule failed for ${dep.id}", e)
                }
            }
    }

    private fun scheduleOneInternal(
        context: Context, dep: Deposit, targetDate: LocalDate,
        hour: Int, minute: Int
    ) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(hour, minute))
        val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        Log.d(TAG, "schedule: ${dep.productName} trigger at ${targetDateTime} ($triggerMillis ms)")
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "skip past trigger for ${dep.productName}")
            return
        }

        val body = "${dep.productName}（${dep.bankName}）将于${dep.endDate}到期，本金 ¥${"%.2f".format(dep.principal)}"

        val requestCode = dep.id.hashCode()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_TITLE, "存单到期提醒")
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_NOTIFY_ID, requestCode)
            // 标记此 Intent 唯一
            data = android.net.Uri.parse("zcb://remind/${dep.id}")
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
            Log.d(TAG, "alarm set OK for ${dep.productName}")
        } catch (e: SecurityException) {
            Log.e(TAG, "no exact alarm permission, fallback to inexact", e)
            alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    private fun cancelAllInternal(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val storage = LocalFileManager(context)
        val deposits = kotlinx.coroutines.runBlocking { storage.getAllDeposits() }
        deposits.forEach { dep ->
            val requestCode = dep.id.hashCode()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                data = android.net.Uri.parse("zcb://remind/${dep.id}")
            }
            val flags = PendingIntent.FLAG_NO_CREATE or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
            val existing = PendingIntent.getBroadcast(context, requestCode, intent, flags)
            if (existing != null) {
                alarmMgr.cancel(existing)
                existing.cancel()
            }
        }
    }
}

const val ACTION_REMIND = "com.example.zengchubao.REMIND"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"
const val EXTRA_NOTIFY_ID = "notify_id"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called: ${intent.action}")
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "存单到期提醒"
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 0)

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val launchPending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifyId, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(launchPending)
            .build()
        )
        Log.d(TAG, "notification posted: $title")
    }
}
